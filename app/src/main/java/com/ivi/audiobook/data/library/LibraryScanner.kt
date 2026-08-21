package com.ivi.audiobook.data.library

import android.content.Context
import android.media.MediaMetadataRetriever
import android.os.Environment
import android.os.storage.StorageManager
import android.util.Log
import androidx.core.os.EnvironmentCompat
import com.ivi.audiobook.data.local.dao.BookDao
import com.ivi.audiobook.data.local.entity.BookEntity
import com.ivi.audiobook.util.StoragePermissions
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest
import javax.inject.Inject

data class LibraryScanResult(val addedCount: Int, val failedCount: Int)

private const val TAG = "LibraryScanner"

//private val SUPPORTED_EXTENSIONS = listOf("mp3", "m4a", "m4b", "aac", "ogg", "flac", "wav")
private val SUPPORTED_EXTENSIONS = listOf("m4b")

/**
 * Walks every storage volume directly (internal + removable alike) via StorageManager and
 * MediaMetadataRetriever, with no MediaStore involved at all.
 *
 * MediaStore was the original design, but proved unreliable on target IVI hardware for both
 * internal and removable storage — its scanner populates a separate, private catalog rather than
 * the standard MediaStore rows any third-party app queries, so `MediaStore.Audio.Media` queries
 * came back empty even when files genuinely existed and had been scanned by the OEM's own
 * service. With MANAGE_EXTERNAL_STORAGE granted (required regardless, since removable volumes
 * aren't owned by this app), a direct filesystem walk works uniformly for every volume and needs
 * no MediaStore fallback logic.
 */
class LibraryScanner @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val bookDao: BookDao,
) {

    /**
     * @param forced Mirrors Helium's LibraryUpdate `mForced` flag: a normal scan (app launch, USB
     * attach/detach) only soft-hides a missing-but-mounted book, since it might reappear (e.g. a
     * transient read glitch). A forced scan — the user explicitly hitting reset — actually removes
     * it from the database, including books that were already hidden from an earlier scan. Either
     * way, a book whose volume isn't mounted (an unplugged USB drive) is left untouched — it isn't
     * "gone", it's just not here right now.
     */
    suspend fun scan(forced: Boolean = false): LibraryScanResult = withContext(Dispatchers.IO) {
        if (!StoragePermissions.isGranted()) {
            Log.w(TAG, "scan() aborted: All Files Access not granted")
            return@withContext LibraryScanResult(0, 0)
        }

        val storageManager = context.getSystemService(StorageManager::class.java)
        val volumes = storageManager?.storageVolumes.orEmpty()
        Log.d(TAG, "scan() found ${volumes.size} storage volume(s)")

        var added = 0
        var failed = 0

        for (volume in volumes) {
            val directory = volume.directory
            if (directory == null) {
                Log.w(TAG, "scan() skipping volume with no directory: ${volume.mediaStoreVolumeName ?: "primary"}, state=${volume.state}")
                continue
            }

            val files = walkForSupportedFiles(directory)
            Log.i(TAG, "scan() volume=${volume.mediaStoreVolumeName ?: "primary"} path=${directory.absolutePath} isPrimary=${volume.isPrimary} found=${files.size}")

            for (file in files) {
                val metadata = extractAudioMetadata(file.absolutePath)
                val title = metadata.title ?: file.nameWithoutExtension
                val wasAdded = addOrUpdateBook(
                    path = file.absolutePath,
                    isInternal = volume.isPrimary,
                    title = title,
                    author = metadata.author,
                    durationMs = metadata.durationMs,
                )
                if (wasAdded) added++ else failed++
            }
        }

        removeOrHideMissingBooks(forced)

        Log.i(TAG, "scan() complete: added=$added failed=$failed forced=$forced")
        LibraryScanResult(added, failed)
    }

    private fun walkForSupportedFiles(directory: File): List<File> = runCatching {
        directory.walkTopDown()
            // Deleting a file on a desktop OS often leaves it in a hidden housekeeping folder
            // (Windows' $RECYCLE.BIN, macOS' .Trash, "System Volume Information") rather than
            // actually erasing it; a raw walk has no concept of "deleted" unless those folders are
            // pruned before descending into them.
            .onEnter { dir -> !dir.isHiddenOrSystemFolder() }
            .filter { file -> file.isFile && SUPPORTED_EXTENSIONS.any { ext -> file.name.endsWith(".$ext", ignoreCase = true) } }
            .toList()
    }.onFailure { error ->
        Log.e(TAG, "walkForSupportedFiles() failed for ${directory.absolutePath}", error)
    }.getOrDefault(emptyList())

    private fun File.isHiddenOrSystemFolder(): Boolean =
        name.startsWith('.') || name.startsWith('$') || name.equals("System Volume Information", ignoreCase = true)

    private data class AudioMetadata(val title: String?, val author: String?, val durationMs: Long)

    private fun extractAudioMetadata(path: String): AudioMetadata {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(path)
            AudioMetadata(
                title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)?.takeIf { it.isNotBlank() },
                author = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)?.takeIf { it.isNotBlank() },
                durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L,
            )
        } catch (e: Exception) {
            Log.w(TAG, "extractAudioMetadata() failed for path=$path", e)
            AudioMetadata(null, null, 0L)
        } finally {
            retriever.release()
        }
    }

    private suspend fun addOrUpdateBook(
        path: String,
        isInternal: Boolean,
        title: String,
        author: String?,
        durationMs: Long,
    ): Boolean {
        val existingByPath = bookDao.getByFilePath(path)
        if (existingByPath != null) {
            if (existingByPath.hidden) bookDao.updateHidden(existingByPath.id, false)
            return true
        }

        val hash = hashFile(path) ?: return false

        val existingByHash = bookDao.getByHash(hash)
        if (existingByHash != null) {
            // Same file content re-appearing at a different path/volume (e.g. moved between
            // internal storage and a USB drive) — update in place instead of duplicating.
            bookDao.updateFileLocation(existingByHash.id, path, isInternal)
            if (existingByHash.hidden) bookDao.updateHidden(existingByHash.id, false)
            return true
        }

        bookDao.insert(
            BookEntity(
                filePath = path,
                isInternal = isInternal,
                title = title,
                author = author,
                durationMs = durationMs,
                coverPath = extractCover(path, hash),
                hash = hash,
                addedDate = System.currentTimeMillis(),
            ),
        )
        return true
    }

    private suspend fun removeOrHideMissingBooks(forced: Boolean) {
        for (book in bookDao.getAllBooks()) {
            // A normal scan has nothing new to say about a book that's already hidden. A forced
            // reset re-checks it too, since this is the explicit "clean up my library" action.
            if (book.hidden && !forced) continue

            val file = File(book.filePath)
            if (file.exists()) continue

            // Only act when the volume is actually mounted and the file is really gone — an
            // unplugged USB drive should not wipe or hide its books from the library.
            val storageState = EnvironmentCompat.getStorageState(file)
            val mounted = storageState == Environment.MEDIA_MOUNTED || storageState == Environment.MEDIA_MOUNTED_READ_ONLY
            if (!mounted) {
                Log.d(TAG, "book id=${book.id} path=${book.filePath} missing but volume state=$storageState (likely unplugged) — leaving untouched")
                continue
            }

            if (forced) {
                Log.i(TAG, "removing book id=${book.id} path=${book.filePath}: forced reset, volume mounted (state=$storageState), file gone")
                bookDao.deleteById(book.id)
            } else {
                Log.i(TAG, "hiding book id=${book.id} path=${book.filePath}: volume mounted (state=$storageState) but file is gone")
                bookDao.updateHidden(book.id, true)
            }
        }
    }

    private fun extractCover(path: String, hash: String): String? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(path)
            val art = retriever.embeddedPicture ?: return null
            val coversDir = File(context.filesDir, "covers").apply { mkdirs() }
            val coverFile = File(coversDir, "$hash.jpg")
            coverFile.writeBytes(art)
            coverFile.absolutePath
        } catch (e: Exception) {
            null
        } finally {
            retriever.release()
        }
    }

    private fun hashFile(path: String): String? = try {
        val digest = MessageDigest.getInstance("MD5")
        BufferedInputStream(FileInputStream(path)).use { input ->
            val buffer = ByteArray(8192)
            var read: Int
            while (input.read(buffer).also { read = it } != -1) {
                digest.update(buffer, 0, read)
            }
        }
        digest.digest().joinToString("") { "%02x".format(it) }
    } catch (e: Exception) {
        null
    }
}
