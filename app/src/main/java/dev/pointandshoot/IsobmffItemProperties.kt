package dev.pointandshoot

/**
 * Pure-data formatter for the AVIF / HEIF Item Properties parent box
 * (`iprp`) and its mandatory child Item Property Container (`ipco`)
 * per ISO/IEC 14496-12 §8.11.14 ItemPropertyContainer / §8.11.14
 * ItemProperties.
 *
 * Both `ipco` and `iprp` are *plain* boxes (NOT FullBoxes — they have
 * no `version` / `flags` slot), so their wire format is just the
 * canonical `size + type + payload` envelope from [IsobmffBox]. The
 * payload of `ipco` is an ordered concatenation of property child
 * boxes (`colr`, `pasp`, `clap`, `irot`, `imir`, `pixi`, ...); the
 * payload of `iprp` is the concatenation of the single `ipco`
 * followed by one or more [ItemPropertyAssociation] (`ipma`) boxes.
 *
 * **Property order matters**: per ISO/IEC 14496-12 §8.11.14.3, the
 * 1-based index in `ipma` refers to the position of the property in
 * the `ipco` child list. The [Builder] enforces this by handing back
 * the 1-based index when each property is added, so the caller can
 * hand the index straight to
 * [ItemPropertyAssociation.Association.propertyIndex] without having
 * to count children manually.
 *
 * Pure-data Kotlin (no Android imports), JVM-testable.
 */
object IsobmffItemProperties {

    /** Bumped only when the on-disk byte layout changes incompatibly. */
    const val SCHEMA_VERSION: Int = 1

    /** Canonical 4-byte ASCII box type for the Item Property Container. */
    const val IPCO_BOX_TYPE: String = "ipco"

    /** Canonical 4-byte ASCII box type for the Item Properties parent. */
    const val IPRP_BOX_TYPE: String = "iprp"

    /**
     * Minimum size of a pre-encoded box: the 8-byte plain box header
     * (`uint32 size + 4-byte type`). Anything shorter cannot be a
     * well-formed property box and is rejected at [Builder.add] time
     * rather than silently accepted into the bitstream.
     */
    const val MIN_ENCODED_BOX_SIZE: Int = IsobmffBox.PLAIN_HEADER_SIZE

    /**
     * Encode the `ipco` container box: an ordered concatenation of
     * pre-encoded property child boxes wrapped in the canonical
     * `size + type + payload` envelope.
     *
     * Each entry in [properties] must already include its own
     * canonical 8-byte (or 16-byte large-size) header; the typical
     * call site is `IsobmffBox.encodeBox(type, payload)` for plain
     * property boxes (`colr`, `pasp`, etc) or
     * `IsobmffBox.encodeFullBox(type, version, flags, payload)` for
     * FullBox property boxes. Caller-supplied buffers are NOT
     * defensively copied because the entire `ipco` payload is
     * concatenated into a fresh buffer before this function returns.
     *
     * Returns the complete `ipco` box (header + payload).
     */
    fun encodeIpcoBox(properties: List<ByteArray>): ByteArray {
        for ((idx, prop) in properties.withIndex()) {
            require(prop.size >= MIN_ENCODED_BOX_SIZE) {
                "property[$idx] must include the canonical $MIN_ENCODED_BOX_SIZE-byte ISOBMFF header; got ${prop.size}"
            }
        }
        val payload = IsobmffBox.concatPayloads(properties)
        return IsobmffBox.encodeBox(IPCO_BOX_TYPE, payload)
    }

    /**
     * Encode the `iprp` parent box: one `ipco` (mandatory, exactly
     * one per spec) followed by one or more `ipma` boxes. Both
     * arguments must already be pre-encoded box byte buffers
     * (header + payload); typical call sites are [encodeIpcoBox] for
     * the `ipco` argument and
     * [ItemPropertyAssociation.encodeBox] for each `ipma` argument.
     *
     * Returns the complete `iprp` box (header + payload).
     */
    fun encodeIprpBox(ipcoBox: ByteArray, ipmaBoxes: List<ByteArray>): ByteArray {
        require(ipcoBox.size >= MIN_ENCODED_BOX_SIZE) {
            "ipcoBox must include the canonical $MIN_ENCODED_BOX_SIZE-byte ISOBMFF header; got ${ipcoBox.size}"
        }
        require(ipmaBoxes.isNotEmpty()) {
            "iprp must contain at least one ipma box per ISO/IEC 14496-12 §8.11.14"
        }
        for ((idx, ipma) in ipmaBoxes.withIndex()) {
            require(ipma.size >= MIN_ENCODED_BOX_SIZE) {
                "ipmaBoxes[$idx] must include the canonical $MIN_ENCODED_BOX_SIZE-byte ISOBMFF header; got ${ipma.size}"
            }
        }
        val payload = IsobmffBox.concatPayloads(buildList(ipmaBoxes.size + 1) {
            add(ipcoBox)
            addAll(ipmaBoxes)
        })
        return IsobmffBox.encodeBox(IPRP_BOX_TYPE, payload)
    }

    /**
     * Order-preserving builder for the `ipco` payload. Each call to
     * [add] returns the **1-based** property index per ISO/IEC
     * 14496-12 §8.11.14.3 — so the caller can do
     *
     * ```
     * val ipco = IsobmffItemProperties.Builder()
     * val colrIdx = ipco.add(IsobmffBox.encodeBox("colr", colrPayload))
     * val pixiIdx = ipco.add(IsobmffBox.encodeBox("pixi", pixiPayload))
     * val ipcoBox = ipco.build()
     * val ipmaBox = ItemPropertyAssociation.encodeBox(
     *     listOf(ItemPropertyAssociation.Entry(itemId = 1, associations = listOf(
     *         ItemPropertyAssociation.Association(propertyIndex = colrIdx, essential = true),
     *         ItemPropertyAssociation.Association(propertyIndex = pixiIdx, essential = true),
     *     )))
     * )
     * val iprpBox = IsobmffItemProperties.encodeIprpBox(ipcoBox, listOf(ipmaBox))
     * ```
     *
     * without manually tracking the property count.
     */
    class Builder {
        private val properties = ArrayList<ByteArray>()

        /**
         * Append a pre-encoded property box and return the 1-based
         * `ipma` index that addresses it. The builder defensively
         * copies the input array so a caller mutating the source
         * buffer afterward cannot corrupt the eventual `ipco`
         * payload.
         *
         * @throws IllegalArgumentException if the encoded box does
         *     not include at least the canonical 8-byte ISOBMFF
         *     header.
         */
        fun add(encodedBox: ByteArray): Int {
            require(encodedBox.size >= MIN_ENCODED_BOX_SIZE) {
                "encodedBox must include the canonical $MIN_ENCODED_BOX_SIZE-byte ISOBMFF header; got ${encodedBox.size}"
            }
            properties.add(encodedBox.copyOf())
            return properties.size
        }

        /** Number of properties currently in the builder (== last index returned by [add]). */
        fun size(): Int = properties.size

        /**
         * Encode the accumulated properties as a complete `ipco`
         * box (header + payload). Equivalent to
         * `encodeIpcoBox(properties)` where `properties` is the
         * list of byte buffers passed to [add] in order.
         */
        fun build(): ByteArray = encodeIpcoBox(properties)
    }
}
