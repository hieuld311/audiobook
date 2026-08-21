package com.ivi.audiobook.data.lyrics

import com.ivi.audiobook.domain.model.LyricLine
import java.io.RandomAccessFile
import java.nio.charset.StandardCharsets

/**
 * Reads synced lyrics from an MP4/M4A/M4B file's `moov/udta/meta/ilst/©lyr` atom (the standard
 * iTunes-style lyrics tag) and parses its LRC-format (`[mm:ss.xx] text`) contents.
 *
 * Media3 doesn't expose this atom — it's not part of ID3 (that's MP3-only) and isn't one of the
 * handful of tags Mp4Extractor surfaces as MediaMetadata — so this walks the box tree directly
 * with seek-based reads (no full-file load, safe for large audiobook files that lack the atom
 * entirely). The exact layout here (FullBox version/flags on `meta`, the nested `data` box inside
 * `©lyr`) was verified byte-for-byte against a real tagged file, not assumed from spec alone.
 */
object Mp4LyricsExtractor {

    fun extractLyrics(filePath: String): List<LyricLine>? = try {
        RandomAccessFile(filePath, "r").use { raf ->
            val moov = findAtom(raf, 0L, raf.length(), "moov") ?: return@use null
            val udta = findAtom(raf, moov.start, moov.end, "udta") ?: return@use null
            val meta = findAtom(raf, udta.start, udta.end, "meta") ?: return@use null
            // 'meta' is a FullBox: 4 bytes of version/flags precede its children.
            val ilst = findAtom(raf, meta.start + 4, meta.end, "ilst") ?: return@use null
            val lyr = findAtom(raf, ilst.start, ilst.end, "©lyr") ?: return@use null
            val data = findAtom(raf, lyr.start, lyr.end, "data") ?: return@use null
            // data box: 4-byte type flag + 4-byte locale, then the raw text payload.
            val payloadStart = data.start + 8
            val payloadLength = (data.end - payloadStart).toInt()
            if (payloadLength <= 0) return@use null
            raf.seek(payloadStart)
            val bytes = ByteArray(payloadLength)
            raf.readFully(bytes)
            parseLrc(String(bytes, StandardCharsets.UTF_8))
        }
    } catch (e: Exception) {
        null
    }

    private data class AtomRange(val start: Long, val end: Long)

    private fun findAtom(raf: RandomAccessFile, from: Long, to: Long, fourcc: String): AtomRange? {
        var pos = from
        val header = ByteArray(8)
        while (pos + 8 <= to) {
            raf.seek(pos)
            raf.readFully(header)
            var size = beUInt32(header, 0)
            var headerSize = 8L
            if (size == 1L) {
                val largeSize = ByteArray(8)
                raf.readFully(largeSize)
                size = beUInt64(largeSize)
                headerSize = 16L
            } else if (size == 0L) {
                size = to - pos
            }
            if (size < headerSize) break
            val type = String(header, 4, 4, StandardCharsets.ISO_8859_1)
            if (type == fourcc) return AtomRange(pos + headerSize, pos + size)
            pos += size
        }
        return null
    }

    private fun beUInt32(b: ByteArray, offset: Int): Long =
        ((b[offset].toLong() and 0xFF) shl 24) or
            ((b[offset + 1].toLong() and 0xFF) shl 16) or
            ((b[offset + 2].toLong() and 0xFF) shl 8) or
            (b[offset + 3].toLong() and 0xFF)

    private fun beUInt64(b: ByteArray): Long {
        var value = 0L
        for (i in 0 until 8) value = (value shl 8) or (b[i].toLong() and 0xFF)
        return value
    }

    private val LRC_LINE = Regex("""\[(\d{2}):(\d{2})\.(\d{2,3})]\s*(.*)""")

    private fun parseLrc(text: String): List<LyricLine> {
        val lines = mutableListOf<LyricLine>()
        for (rawLine in text.lineSequence()) {
            val match = LRC_LINE.find(rawLine) ?: continue
            val (minutes, seconds, fraction, content) = match.destructured
            val fractionMs = if (fraction.length == 2) fraction.toLong() * 10 else fraction.toLong()
            val startMs = minutes.toLong() * 60_000 + seconds.toLong() * 1_000 + fractionMs
            if (content.isNotBlank()) lines += LyricLine(startMs, content.trim())
        }
        return lines.sortedBy { it.startMs }
    }
}
