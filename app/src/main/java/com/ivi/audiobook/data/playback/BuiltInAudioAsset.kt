package com.ivi.audiobook.data.playback

import android.content.Context
import android.media.MediaMetadataRetriever
import com.ivi.audiobook.domain.model.LyricLine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

data class BuiltInAudioInfo(
    val assetUri: String,
    val title: String,
    val author: String?,
    val durationMs: Long,
    val coverPath: String?,
    val lyrics: List<LyricLine>,
)

/**
 * There is exactly one MP3 bundled in assets/ -- discovered by listing the assets root instead of
 * hardcoding a filename. Title/artist/duration/cover art are read from the file's own metadata,
 * falling back to the filename when a tag is missing.
 *
 * Lyrics are tried in order: a sidecar `<basename>.lrc` asset next to the audio file, then an
 * embedded ID3 TXXX lyrics frame via [Id3LyricsReader] (the actual path this app's files use --
 * see that class for why).
 */
object BuiltInAudioAsset {
    suspend fun resolve(context: Context): BuiltInAudioInfo = withContext(Dispatchers.IO) {
        val fileName = context.assets.list("")
            ?.firstOrNull { name -> name.endsWith(".mp3", ignoreCase = true) }
            ?: error("No .mp3 file found in assets/")

        val retriever = MediaMetadataRetriever()
        val title: String?
        val author: String?
        val durationMs: Long
        val coverPath: String?
        try {
            context.assets.openFd(fileName).use { afd ->
                retriever.setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
            }
            title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)?.takeIf { it.isNotBlank() }
            author = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)?.takeIf { it.isNotBlank() }
            durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            coverPath = retriever.embeddedPicture?.let { bytes -> cacheCover(context, bytes) }
        } finally {
            retriever.release()
        }

        val lyrics = readSidecarLyrics(context, fileName).ifEmpty {
            context.assets.open(fileName).use { Id3LyricsReader.read(it) }
        }

        BuiltInAudioInfo(
            assetUri = "asset:///$fileName",
            title = title ?: fileName.substringBeforeLast('.').replace('_', ' '),
            author = author,
            durationMs = durationMs,
            coverPath = coverPath,
            lyrics = lyrics,
        )
    }

    private fun cacheCover(context: Context, bytes: ByteArray): String {
        val file = File(context.cacheDir, "built_in_cover.jpg")
        file.writeBytes(bytes)
        return file.absolutePath
    }

    private fun readSidecarLyrics(context: Context, audioFileName: String): List<LyricLine> {
        val lrcName = audioFileName.substringBeforeLast('.') + ".lrc"
        val text = runCatching {
            context.assets.open(lrcName).bufferedReader().use { it.readText() }
        }.getOrNull() ?: return emptyList()
        return Id3LyricsReader.parseLrcText(text)
    }
}
