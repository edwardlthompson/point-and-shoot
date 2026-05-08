package dev.pointandshoot

import java.io.ByteArrayOutputStream

/**
 * Pure-data formatter for the top-level ISOBMFF / HEIF / AVIF
 * Meta Box (`meta`) FullBox per ISO/IEC 14496-12 §8.11.1.
 *
 * `meta` is the root container the entire HEIF / AVIF item-based
 * file format hangs off of. It is a `FullBox('meta', version = 0,
 * flags = 0)` whose payload is an ordered concatenation of child
 * boxes describing the items in the file:
 *
 * ```
 * aligned(8) class MetaBox extends FullBox('meta', 0, 0) {
 *     HandlerBox        theHandler;       // mandatory ('hdlr')
 *     PrimaryItemBox    primaryItem?;     // ('pitm')
 *     DataInformationBox dinf?;
 *     ItemLocationBox   iloc?;
 *     ItemProtectionBox ipro?;
 *     ItemInfoBox       iinf?;            // ('iinf')
 *     IPMPControlBox    ipmc?;
 *     ItemReferenceBox  iref?;            // ('iref')
 *     ItemDataBox       idat?;
 *     ItemPropertiesBox iprp?;            // ('iprp')
 * }
 * ```
 *
 * For a canonical AVIF still file, the mandatory minimum is:
 *
 *  1. `hdlr` with `handler_type = "pict"` (Round 27).
 *  2. `pitm` declaring the primary image item ID (Round 25).
 *  3. `iinf` cataloging every item's `infe` entry (Round 26).
 *  4. `iloc` mapping every item to a byte range (Round 28).
 *  5. `iprp` carrying the property container `ipco` plus
 *     `ipma` linkage (Rounds 23-24).
 *
 * `hdlr` MUST come first per §8.11.1.2 ("the HandlerBox shall
 * occur before the other boxes"). Every other child is optional
 * and MUST appear at most once.
 *
 * This module emits the *full* mux-ready `meta` box in one call;
 * callers feed pre-encoded children into [Builder] (or directly
 * into [encodeBox]) and the writer takes care of:
 *
 *  * Validating that `hdlr` is present and is the first child.
 *  * Validating that no other child type appears more than once.
 *  * Wrapping the concatenated payload with
 *    `IsobmffBox.encodeFullBox("meta", version = 0, flags = 0,
 *    payload)`.
 *
 * Pure-data Kotlin (no Android imports), JVM-testable.
 */
object MetaBox {

    /** Bumped only when the on-disk byte layout changes incompatibly. */
    const val SCHEMA_VERSION: Int = 1

    /** Canonical 4-byte ASCII box type. */
    const val BOX_TYPE: String = "meta"

    /**
     * Minimum sane child-box size: a plain ISOBMFF box must
     * carry at least the 8-byte `(size, type)` header
     * (Round 22's `IsobmffBox.PLAIN_HEADER_SIZE`). Smaller
     * inputs are rejected as malformed.
     */
    const val MIN_CHILD_BOX_SIZE: Int = IsobmffBox.PLAIN_HEADER_SIZE

    /**
     * Encode the FullBox payload (the bytes after the 4-byte
     * version+flags slot).
     *
     * The payload is the ordered concatenation of the supplied
     * pre-encoded child boxes. `hdlr` is always the first child
     * per §8.11.1.2; the remaining optional children appear in
     * the documented order (`pitm`, `iinf`, `iloc`, `iref`,
     * `iprp`). If a caller wants a different child mix or
     * ordering they should reach for the lower-level
     * [encodePayloadOrdered] form below.
     *
     * Caller wraps with
     * `IsobmffBox.encodeFullBox("meta", version = 0, flags = 0,
     * payload)`.
     */
    fun encodePayload(
        handlerBox: ByteArray,
        primaryItemBox: ByteArray? = null,
        itemInfoBox: ByteArray? = null,
        itemLocationBox: ByteArray? = null,
        itemReferenceBox: ByteArray? = null,
        itemPropertiesBox: ByteArray? = null,
    ): ByteArray {
        val ordered = mutableListOf<ByteArray>().apply {
            add(handlerBox)
            primaryItemBox?.let { add(it) }
            itemInfoBox?.let { add(it) }
            itemLocationBox?.let { add(it) }
            itemReferenceBox?.let { add(it) }
            itemPropertiesBox?.let { add(it) }
        }
        return encodePayloadOrdered(ordered)
    }

    /**
     * Lower-level form: encode the payload from a caller-ordered
     * list of pre-encoded child boxes. This still validates that
     * the first child is `hdlr` and that no other child type
     * appears more than once, but otherwise preserves the
     * supplied ordering.
     */
    fun encodePayloadOrdered(children: List<ByteArray>): ByteArray {
        require(children.isNotEmpty()) { "meta box must have at least one child (hdlr)" }
        for ((i, child) in children.withIndex()) {
            require(child.size >= MIN_CHILD_BOX_SIZE) {
                "child $i is shorter than the canonical 8-byte ISOBMFF header (got ${child.size} bytes)"
            }
        }

        val firstType = readBoxType(children[0])
        require(firstType == HandlerReferenceBox.BOX_TYPE) {
            "first child must be '${HandlerReferenceBox.BOX_TYPE}'; got '$firstType'"
        }

        val seen = HashSet<String>()
        for (child in children) {
            val type = readBoxType(child)
            require(seen.add(type)) {
                "child type '$type' appears more than once; meta only allows at most one of each child"
            }
        }

        val out = ByteArrayOutputStream()
        for (child in children) {
            out.write(child)
        }
        return out.toByteArray()
    }

    /**
     * Convenience: encode the payload via [encodePayload] and
     * wrap with `IsobmffBox.encodeFullBox("meta", 0, 0,
     * payload)` so the caller gets a complete, mux-ready `meta`
     * box (header + payload) in one call.
     */
    fun encodeBox(
        handlerBox: ByteArray,
        primaryItemBox: ByteArray? = null,
        itemInfoBox: ByteArray? = null,
        itemLocationBox: ByteArray? = null,
        itemReferenceBox: ByteArray? = null,
        itemPropertiesBox: ByteArray? = null,
    ): ByteArray {
        val payload = encodePayload(
            handlerBox = handlerBox,
            primaryItemBox = primaryItemBox,
            itemInfoBox = itemInfoBox,
            itemLocationBox = itemLocationBox,
            itemReferenceBox = itemReferenceBox,
            itemPropertiesBox = itemPropertiesBox,
        )
        return IsobmffBox.encodeFullBox(BOX_TYPE, version = 0, flags = 0, payload = payload)
    }

    /**
     * Builder for accumulating child boxes incrementally. Useful
     * when the caller assembles `iinf` / `iloc` / `iprp` in a
     * pipeline and wants to plug the result into a single
     * `meta` box at the end.
     *
     * Mandatory: [setHandler]. All other setters are optional.
     * [build] applies the ordering and uniqueness rules and
     * returns the complete mux-ready `meta` box.
     */
    class Builder {
        private var handler: ByteArray? = null
        private var primaryItem: ByteArray? = null
        private var itemInfo: ByteArray? = null
        private var itemLocation: ByteArray? = null
        private var itemReference: ByteArray? = null
        private var itemProperties: ByteArray? = null

        fun setHandler(box: ByteArray): Builder {
            handler = box.copyOf()
            return this
        }

        fun setPrimaryItem(box: ByteArray): Builder {
            primaryItem = box.copyOf()
            return this
        }

        fun setItemInfo(box: ByteArray): Builder {
            itemInfo = box.copyOf()
            return this
        }

        fun setItemLocation(box: ByteArray): Builder {
            itemLocation = box.copyOf()
            return this
        }

        fun setItemReference(box: ByteArray): Builder {
            itemReference = box.copyOf()
            return this
        }

        fun setItemProperties(box: ByteArray): Builder {
            itemProperties = box.copyOf()
            return this
        }

        fun build(): ByteArray {
            val handler = checkNotNull(handler) {
                "MetaBox.Builder requires a handler box (call setHandler before build)"
            }
            return encodeBox(
                handlerBox = handler,
                primaryItemBox = primaryItem,
                itemInfoBox = itemInfo,
                itemLocationBox = itemLocation,
                itemReferenceBox = itemReference,
                itemPropertiesBox = itemProperties,
            )
        }
    }

    /**
     * Read the 4-byte ASCII type field at offset 4 of a
     * pre-encoded ISOBMFF box. Caller has already confirmed
     * `box.size >= MIN_CHILD_BOX_SIZE`.
     */
    private fun readBoxType(box: ByteArray): String {
        val sb = StringBuilder(4)
        for (i in 0 until 4) {
            sb.append((box[4 + i].toInt() and 0xFF).toChar())
        }
        return sb.toString()
    }
}
