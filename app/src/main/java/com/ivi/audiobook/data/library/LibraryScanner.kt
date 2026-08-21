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
     * @param forced Mirrors Helium's LibraryUpdate `mForced` flag, with one adaptation: a normal
     * scan (app launch, USB attach/detach) only soft-hides a book whose volume is mounted but the
     * file is genuinely gone — it leaves a book on an unplugged USB volume alone, since that's not
     * "gone", just not here right now. A forced scan — the user explicitly hitting reset — is a
     * deliberate "clean up my library now": it removes any unreachable book outright, including
     * ones on a currently-unplugged USB drive, and re-checks books already hidden from an earlier
     * scan too.
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
        val allBooks = bookDao.getAllBooks()
        Log.d(TAG, "removeOrHideMissingBooks() forced=$forced checking ${allBooks.size} book(s)")
        var removed = 0
        var hidden = 0

        for (book in allBooks) {
            val file = File(book.filePath)
            val exists = file.exists()
            Log.d(TAG, "book id=${book.id} hidden=${book.hidden} path=${book.filePath} exists=$exists")

            if (exists) continue

            if (forced) {
                // A forced reset is the explicit "clean up my library now" action — remove it
                // outright regardless of *why* it's unreachable (deleted file, or a USB drive
                // that's currently unplugged). This intentionally does NOT check mount state:
                // if the user asked to reset while the drive is out, the book should go.
                Log.i(TAG, "removing book id=${book.id} path=${book.filePath}: forced reset, file not reachable")
                bookDao.deleteById(book.id)
                removed++
                continue
            }

            if (book.hidden) continue // normal scan has nothing new to say about an already-hidden book

            // Only soft-hide when the volume is actually mounted and the file is really gone — a
            // background/automatic scan shouldn't silently wipe books just because a USB drive
            // happens to be unplugged at that moment.
            val storageState = EnvironmentCompat.getStorageState(file)
            val mounted = storageState == Environment.MEDIA_MOUNTED || storageState == Environment.MEDIA_MOUNTED_READ_ONLY
            Log.d(TAG, "book id=${book.id} storageState=$storageState mounted=$mounted")
            if (mounted) {
                Log.i(TAG, "hiding book id=${book.id} path=${book.filePath}: volume mounted (state=$storageState) but file is gone")
                bookDao.updateHidden(book.id, true)
                hidden++
            } else {
                Log.d(TAG, "book id=${book.id} path=${book.filePath} missing but volume state=$storageState (likely unplugged) — leaving untouched")
            }
        }

        Log.i(TAG, "removeOrHideMissingBooks() done: removed=$removed hidden=$hidden")
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
