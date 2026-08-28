package com.ivi.audiobook.data.playback

import com.ivi.audiobook.domain.model.LyricLine
import java.io.InputStream

// Accepts both "[mm:ss.xx]" and the rarer "[mm:ss:xx]" separator, and makes the fractional part
// optional -- some LRC sources only carry whole-second timestamps.
private val LRC_LINE = Regex("""\[(\d{2}):(\d{2})(?:[.:](\d{1,3}))?]\s*(.*)""")

// ffmpeg has no native-frame writer for a Vorbis "LYRICS" comment when muxing to MP3, so it maps
// it into a TXXX (user-defined text) frame with descriptor "USLT", value = the LRC-formatted
// text -- confirmed against a real FLAC-to-MP3 conversion (both the original file's converter and
// ffmpeg itself do this). This is the only lyrics path this app's actual MP3 files ever use.
private val LYRICS_TXXX_DESCRIPTORS = setOf("USLT", "LYRICS")

/**
 * Hand-rolled ID3v2 frame walker for MP3 embedded lyrics. MP3 has no equivalent to the MP4 lyr
 * atom this app parses for M4B files, and no native SYLT/USLT frame is ever produced by the
 * FLAC-to-MP3 pipeline this app's files come from -- lyrics live in a TXXX frame instead (see
 * [LYRICS_TXXX_DESCRIPTORS]).
 */
object Id3LyricsReader {

    fun read(input: InputStream): List<LyricLine> = try {
        readInternal(input)
    } catch (e: Exception) {
        emptyList()
    }

    /** Also used for sidecar .lrc files, which are just plain LRC text with no ID3 wrapper. */
    fun parseLrcText(text: String): List<LyricLine> =
        text.lineSequence().mapNotNull(::parseLrcLine).toList()

    private fun readInternal(input: InputStream): List<LyricLine> {
        val header = readFully(input, 10)
        val isId3 = header.size >= 10 &&
            header[0] == 'I'.code.toByte() &&
            header[1] == 'D'.code.toByte() &&
            header[2] == '3'.code.toByte()
        if (!isId3) return emptyList()

        val majorVersion = header[3].toInt()
        val flags = header[5].toInt()
        val tagSize = synchSafeInt(header, 6)

        val tag = readFully(input, tagSize)
        var offset = 0

        if (flags and 0x40 != 0) {
            // Extended header present -- its size field is synchsafe in v2.4, a plain big-endian
            // int (not counting the size field itself) in v2.3.
            offset += if (majorVersion >= 4) synchSafeInt(tag, 0) else beInt(tag, 0) + 4
        }

        while (offset + 10 <= tag.size) {
            val frameId = String(tag, offset, 4, Charsets.US_ASCII)
            val isPadding = frameId.isEmpty() || frameId[0].code == 0
            if (isPadding) break

            val frameSize = if (majorVersion >= 4) synchSafeInt(tag, offset + 4) else beInt(tag, offset + 4)
            val frameStart = offset + 10
            val frameEnd = frameStart + frameSize
            if (frameSize <= 0 || frameEnd > tag.size) break

            if (frameId == "TXXX") {
                parseLyricsTxxx(tag, frameStart, frameEnd)?.let { return it }
            }

            offset = frameEnd
        }
        return emptyList()
    }

    private fun parseLyricsTxxx(bytes: ByteArray, start: Int, end: Int): List<LyricLine>? {
        var pos = start
        if (pos >= end) return null
        val encoding = bytes[pos].toInt(); pos += 1
        val descEnd = findTerminator(bytes, pos, end, isWide(encoding)) ?: return null
        val descriptor = decodeId3Text(bytes, pos, descEnd, encoding).trim().uppercase()
        if (descriptor !in LYRICS_TXXX_DESCRIPTORS) return null

        val valueStart = descEnd + if (isWide(encoding)) 2 else 1
        if (valueStart >= end) return null
        val value = decodeId3Text(bytes, valueStart, end, encoding)
        return parseLrcText(value).takeIf { it.isNotEmpty() }
    }

    private fun parseLrcLine(line: String): LyricLine? {
        val match = LRC_LINE.find(line) ?: return null
        val (min, sec, frac, content) = match.destructured
        val fracMs = when (frac.length) {
            0 -> 0L
            2 -> frac.toLong() * 10
            else -> frac.toLong()
        }
        val startMs = min.toLong() * 60_000 + sec.toLong() * 1_000 + fracMs
        return LyricLine(startMs = startMs, text = content.trim())
    }

    private fun isWide(encoding: Int) = encoding == 1 || encoding == 2

    private fun decodeId3Text(bytes: ByteArray, start: Int, end: Int, encoding: Int): String {
        if (end <= start) return ""
        val charset = when (encoding) {
            0 -> Charsets.ISO_8859_1
            1 -> Charsets.UTF_16
            2 -> Charsets.UTF_16BE
            3 -> Charsets.UTF_8
            else -> Charsets.ISO_8859_1
        }
        return String(bytes, start, end - start, charset)
    }

    private fun findTerminator(bytes: ByteArray, from: Int, end: Int, wide: Boolean): Int? {
        var i = from
        while (i < end) {
            if (wide) {
                if (i + 1 < end && bytes[i].toInt() == 0 && bytes[i + 1].toInt() == 0) return i
                i += 2
            } else {
                if (bytes[i].toInt() == 0) return i
                i += 1
            }
        }
        return null
    }

    private fun readFully(input: InputStream, size: Int): ByteArray {
        val buffer = ByteArray(size)
        var offset = 0
        while (offset < size) {
            val read = input.read(buffer, offset, size - offset)
            if (read == -1) return buffer.copyOf(offset)
            offset += read
        }
        return buffer
    }

    private fun synchSafeInt(bytes: ByteArray, offset: Int): Int =
        ((bytes[offset].toInt() and 0x7F) shl 21) or
            ((bytes[offset + 1].toInt() and 0x7F) shl 14) or
            ((bytes[offset + 2].toInt() and 0x7F) shl 7) or
            (bytes[offset + 3].toInt() and 0x7F)

    private fun beInt(bytes: ByteArray, offset: Int): Int =
        ((bytes[offset].toInt() and 0xFF) shl 24) or
            ((bytes[offset + 1].toInt() and 0xFF) shl 16) or
            ((bytes[offset + 2].toInt() and 0xFF) shl 8) or
            (bytes[offset + 3].toInt() and 0xFF)
}
