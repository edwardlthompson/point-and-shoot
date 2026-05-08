package dev.pointandshoot

/**
 * Pure-data formatter for the AVIF / HEIF Primary Item Box (`pitm`)
 * per ISO/IEC 14496-12 §8.11.4.
 *
 * `pitm` is a `FullBox('pitm', version, 0)` that names the single
 * primary item of the file. For a HEIF / AVIF still image, the
 * primary item is the displayable image; without `pitm`, a HEIF
 * decoder cannot tell which of the items in `iinf` is the one to
 * present to the user.
 *
 * Wire format (per §8.11.4):
 *
 * ```
 * aligned(8) class PrimaryItemBox extends FullBox('pitm', version, 0) {
 *     if (version == 0) {
 *         unsigned int(16) item_ID;
 *     } else {
 *         unsigned int(32) item_ID;
 *     }
 * }
 * ```
 *
 * `version = 0` (16-bit item_ID) covers any file with up to 65535
 * items — which is every realistic still-image use case the engine
 * is going to ship. `version = 1` (32-bit item_ID) is provided for
 * spec completeness, but [chooseVersion] returns `0` until the
 * caller's itemId actually overflows 16 bits.
 *
 * This module emits ONLY the FullBox *payload* (the bytes after the
 * 4-byte version+flags slot). The caller wraps with
 * `IsobmffBox.encodeFullBox("pitm", version, flags = 0, payload)`,
 * or uses the [encodeBox] convenience that picks the minimum
 * version automatically.
 *
 * Pure-data Kotlin (no Android imports), JVM-testable.
 */
object PrimaryItemBox {

    /** Bumped only when the on-disk byte layout changes incompatibly. */
    const val SCHEMA_VERSION: Int = 1

    /** Canonical 4-byte ASCII box type. */
    const val BOX_TYPE: String = "pitm"

    /** Maximum item ID encodable with `version = 0`. */
    const val MAX_SMALL_ITEM_ID: Long = 0xFFFFL

    /** Maximum item ID encodable with `version = 1`. */
    const val MAX_LARGE_ITEM_ID: Long = 0xFFFFFFFFL

    /** Payload size (in bytes) for `version = 0`. */
    const val PAYLOAD_SIZE_V0: Int = 2

    /** Payload size (in bytes) for `version = 1`. */
    const val PAYLOAD_SIZE_V1: Int = 4

    /**
     * Pick the minimum `version` that can encode [itemId] without
     * truncation. Returns `0` if the itemId fits in 16 bits;
     * otherwise `1`.
     *
     * @throws IllegalArgumentException if [itemId] is negative or
     *     exceeds [MAX_LARGE_ITEM_ID].
     */
    fun chooseVersion(itemId: Long): Int {
        require(itemId >= 0L) { "itemId must be >= 0; got $itemId" }
        require(itemId <= MAX_LARGE_ITEM_ID) {
            "itemId must be <= MAX_LARGE_ITEM_ID ($MAX_LARGE_ITEM_ID); got $itemId"
        }
        return if (itemId > MAX_SMALL_ITEM_ID) 1 else 0
    }

    /**
     * Encode the `pitm` FullBox payload (the bytes after the
     * version+flags slot). Caller wraps with
     * `IsobmffBox.encodeFullBox("pitm", version, 0, payload)`.
     *
     * @throws IllegalArgumentException for invalid version, negative
     *     itemId, or itemId that overflows the chosen version's
     *     capacity.
     */
    fun encodePayload(itemId: Long, version: Int): ByteArray {
        require(version == 0 || version == 1) {
            "version must be 0 or 1 per ISO/IEC 14496-12 §8.11.4; got $version"
        }
        require(itemId >= 0L) { "itemId must be >= 0; got $itemId" }
        return if (version == 0) {
            require(itemId <= MAX_SMALL_ITEM_ID) {
                "itemId $itemId exceeds version=0 capacity ($MAX_SMALL_ITEM_ID); use version=1"
            }
            byteArrayOf(
                ((itemId.toInt() ushr 8) and 0xFF).toByte(),
                (itemId.toInt() and 0xFF).toByte(),
            )
        } else {
            require(itemId <= MAX_LARGE_ITEM_ID) {
                "itemId $itemId exceeds version=1 capacity ($MAX_LARGE_ITEM_ID)"
            }
            byteArrayOf(
                ((itemId.toInt() ushr 24) and 0xFF).toByte(),
                ((itemId.toInt() ushr 16) and 0xFF).toByte(),
                ((itemId.toInt() ushr 8) and 0xFF).toByte(),
                (itemId.toInt() and 0xFF).toByte(),
            )
        }
    }

    /**
     * Decode a `pitm` FullBox payload back into the primary item ID.
     * Throws [IllegalArgumentException] when the payload length does
     * not match the expected width for the given [version].
     */
    fun decodePayload(bytes: ByteArray, version: Int): Long {
        require(version == 0 || version == 1) {
            "version must be 0 or 1; got $version"
        }
        return if (version == 0) {
            require(bytes.size == PAYLOAD_SIZE_V0) {
                "pitm v=0 payload must be exactly $PAYLOAD_SIZE_V0 bytes; got ${bytes.size}"
            }
            (((bytes[0].toInt() and 0xFF) shl 8) or (bytes[1].toInt() and 0xFF)).toLong()
        } else {
            require(bytes.size == PAYLOAD_SIZE_V1) {
                "pitm v=1 payload must be exactly $PAYLOAD_SIZE_V1 bytes; got ${bytes.size}"
            }
            ((bytes[0].toInt() and 0xFF).toLong() shl 24) or
                ((bytes[1].toInt() and 0xFF).toLong() shl 16) or
                ((bytes[2].toInt() and 0xFF).toLong() shl 8) or
                (bytes[3].toInt() and 0xFF).toLong()
        }
    }

    /**
     * Convenience: pick the minimum version via [chooseVersion],
     * encode the payload, and wrap with `IsobmffBox.encodeFullBox`
     * so the caller gets a complete, mux-ready `pitm` box (header
     * + payload) in one call. `flags` is always `0` per spec.
     */
    fun encodeBox(itemId: Long): ByteArray {
        val version = chooseVersion(itemId)
        val payload = encodePayload(itemId, version)
        return IsobmffBox.encodeFullBox(BOX_TYPE, version, flags = 0, payload = payload)
    }
}
