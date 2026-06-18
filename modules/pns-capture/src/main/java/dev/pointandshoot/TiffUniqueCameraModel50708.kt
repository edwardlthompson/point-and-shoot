package dev.pointandshoot

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.StandardCharsets

/**
 * Post-processes a little-endian TIFF/DNG from [android.hardware.camera2.DngCreator] to stamp
 * **UniqueCameraModel** (tag **50708** / `0xC614`), which has no public `DngCreator` setter.
 *
 * Rewrites the **primary IFD pointer** in the 8-byte TIFF header to a **new IFD** appended at EOF.
 * The new IFD copies all entries from the old primary IFD (replacing or adding 50708). Old IFD bytes
 * remain in the file as dead space; strip/subIFD offsets keep pointing into unchanged trailing bytes.
 */
object TiffUniqueCameraModel50708 {

    private const val TIFF_MAGIC_II: Short = 0x4949.toShort()
    private const val TIFF_VERSION: Short = 42
    private const val TAG_UNIQUE_CAMERA_MODEL = 50708
    private const val TIFF_TYPE_ASCII = 2

    fun appendTag50708(original: ByteArray, asciiModel: String): ByteArray {
        val trimmed = asciiModel.trim()
        require(trimmed.isNotEmpty()) { "asciiModel must not be blank" }
        require(original.size >= 8) { "buffer too small for TIFF header" }

        val hdr = ByteBuffer.wrap(original, 0, 8).order(ByteOrder.LITTLE_ENDIAN)
        require(hdr.short == TIFF_MAGIC_II) { "only little-endian TIFF (II) supported" }
        require(hdr.short == TIFF_VERSION) { "not TIFF version 42" }
        val primaryIfdOffset = hdr.int.toLong() and 0xffff_ffffL

        val (oldEntries, nextIfd) = parsePrimaryIfd(original, primaryIfdOffset)
        val baseEntries =
            oldEntries
                .filter { it.tag != TAG_UNIQUE_CAMERA_MODEL }
                .map { it.copy() }

        val asciiPayload =
            buildString(trimmed.length + 1) {
                for (ch in trimmed) {
                    require(ch.code in 0x20..0x7e) {
                        "UniqueCameraModel must be printable ASCII (got U+${ch.code})"
                    }
                    append(ch)
                }
                append('\u0000')
            }.toByteArray(StandardCharsets.US_ASCII)

        val newEntryCount = baseEntries.size + 1
        val ifdBodyLen = 2 + newEntryCount * 12 + 4
        val newIfdOffset = original.size
        val asciiDataOffset = newIfdOffset + ifdBodyLen

        val valueFour =
            if (asciiPayload.size <= 4) {
                asciiPayload.copyOf(4)
            } else {
                ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(asciiDataOffset).array()
            }

        val with50708 =
            baseEntries +
                ParsedEntry(TAG_UNIQUE_CAMERA_MODEL, TIFF_TYPE_ASCII, asciiPayload.size, valueFour)

        val ifdBytes = serializeIfd(with50708, nextIfd)

        val total =
            when {
                asciiPayload.size <= 4 -> original.size + ifdBytes.size
                else -> original.size + ifdBytes.size + asciiPayload.size
            }
        val out = ByteArray(total)
        System.arraycopy(original, 0, out, 0, original.size)
        System.arraycopy(ifdBytes, 0, out, newIfdOffset, ifdBytes.size)
        if (asciiPayload.size > 4) {
            System.arraycopy(asciiPayload, 0, out, asciiDataOffset, asciiPayload.size)
        }

        ByteBuffer.wrap(out, 4, 4).order(ByteOrder.LITTLE_ENDIAN).putInt(newIfdOffset)
        return out
    }

    private data class ParsedEntry(
        val tag: Int,
        val type: Int,
        val count: Int,
        val inlineOrOffset: ByteArray,
    )

    private fun parsePrimaryIfd(buf: ByteArray, ifdOffset: Long): Pair<List<ParsedEntry>, Int> {
        require(ifdOffset >= 0 && ifdOffset <= buf.size - 4)
        val bb = ByteBuffer.wrap(buf).order(ByteOrder.LITTLE_ENDIAN)
        bb.position(ifdOffset.toInt())
        val entryCount = bb.short.toInt() and 0xffff
        val entries = ArrayList<ParsedEntry>(entryCount)
        repeat(entryCount) {
            val tag = bb.short.toInt() and 0xffff
            val type = bb.short.toInt() and 0xffff
            val count = bb.int.toLong() and 0xffff_ffffL
            require(count <= Int.MAX_VALUE)
            val raw = ByteArray(4)
            bb.get(raw)
            entries.add(ParsedEntry(tag, type, count.toInt(), raw))
        }
        val nextIfd = bb.int
        return entries to nextIfd
    }

    private fun serializeIfd(entries: List<ParsedEntry>, nextIfdOffset: Int): ByteArray {
        val count = entries.size
        val out = ByteArray(2 + count * 12 + 4)
        val bb = ByteBuffer.wrap(out).order(ByteOrder.LITTLE_ENDIAN)
        bb.putShort(count.toShort())
        for (e in entries) {
            bb.putShort(e.tag.toShort())
            bb.putShort(e.type.toShort())
            bb.putInt(e.count)
            bb.put(e.inlineOrOffset.copyOf(4))
        }
        bb.putInt(nextIfdOffset)
        return out
    }
}
