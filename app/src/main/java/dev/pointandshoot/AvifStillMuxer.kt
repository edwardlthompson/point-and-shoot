package dev.pointandshoot

import java.io.ByteArrayOutputStream

/**
 * Single-call host-side muxer that turns a working-space tag, a
 * canvas size, a per-channel bit-depth list, and an AV1 image
 * bitstream into a complete, mux-ready AVIF still file (`ftyp` +
 * `meta` + `mdat`) per AVIF spec § 4 and ISO/IEC 23008-12.
 *
 * This is the integration round (Round 32) that ties together
 * every host-side primitive shipped in Rounds 17 - 31:
 *
 *  * `colr` (Round 17), `pixi` (Round 20), `ispe` (Round 31) —
 *    mandatory property bundle on the primary image item.
 *  * `pasp` / `clap` (Round 19), `irot` / `imir` (Round 20),
 *    `mdcv` / `clli` (Round 18) — optional transformative /
 *    HDR static-metadata properties.
 *  * `IsobmffBox` (Round 22) — canonical box envelope writer.
 *  * `ipma` (Round 23) — item / property association table.
 *  * `ipco` / `iprp` (Round 24) — property container + parent.
 *  * `pitm` (Round 25) — primary-item declaration.
 *  * `infe` / `iinf` (Round 26) — item-info table.
 *  * `hdlr` (Round 27) — handler reference (`pict`).
 *  * `iloc` (Round 28) — item-location table (offsets into mdat).
 *  * `meta` (Round 29) — top-level meta FullBox.
 *  * `ftyp` / `mdat` (Round 30) — file-type + media-data top-level.
 *
 * The muxer does NOT validate the AV1 bitstream content (the
 * encoder's job) — it only frames the bytes into a well-formed
 * AVIF still file with byte-exact spec-compliant metadata. Pure
 * Kotlin (no Android imports) so the entire emit path is
 * JVM-testable on host.
 *
 * Wire layout produced (per AVIF spec § 4):
 *
 * ```
 * +---------------------------------------------------------+
 * | ftyp box (28 bytes; AVIF still major brand + compat)    |
 * +---------------------------------------------------------+
 * | meta FullBox (variable size)                            |
 * |    hdlr (pict)                                          |
 * |    pitm (primary item = 1)                              |
 * |    iinf (1 av01 entry)                                  |
 * |    iloc (1 row, 1 extent → mdat byte range)             |
 * |    iprp (ipco + ipma; 1 entry binding properties to 1)  |
 * +---------------------------------------------------------+
 * | mdat box (8-byte header + AV1 bitstream)                |
 * +---------------------------------------------------------+
 * ```
 *
 * Offsets in `iloc` point absolutely into the file (the
 * canonical `construction_method = FILE_OFFSET` form) so any
 * decoder can find the AV1 image bytes without parsing `mdat`.
 *
 * ### Two-pass offset planning
 *
 * `iloc` has a circular-dependency problem: the absolute byte
 * offset of the AV1 image data depends on the size of `meta`,
 * but the size of `meta` depends on the size of `iloc`, which
 * depends on the field widths chosen for the offsets. We resolve
 * this with **upfront analytical sizing**:
 *
 *  1. Pick `iloc` field sizes (`offsetSize`, `lengthSize`,
 *     `baseOffsetSize`, `indexSize`) from the *known* item
 *     lengths and a *worst-case* file size bound (Step 4).
 *  2. Compute `iloc` payload size analytically (no encode pass).
 *  3. Now every other box size is known → compute `meta` size.
 *  4. Compute the absolute file offset of each item.
 *  5. Build `iloc` with the real offsets; verify the analytical
 *     size matches the encoded size (sanity check).
 *
 * This is byte-exact, single-pass, and never needs a re-encode
 * loop.
 *
 * ### What this round does NOT yet ship
 *
 *  * EXIF metadata items (require an `iref` "cdsc" reference
 *    box; planned for a future round).
 *  * Auxiliary alpha image items (require an `iref` "auxl"
 *    reference + `auxC` property; planned for a future round).
 *  * Animated AVIF (`avis`) sequences (require track-based
 *    boxes from § 8.x; this muxer is still-image only).
 *
 * Pinned schema version: bumped only when the on-disk byte
 * layout changes incompatibly.
 */
object AvifStillMuxer {

    /** Bumped only when the on-disk byte layout changes incompatibly. */
    const val SCHEMA_VERSION: Int = 1

    /** The canonical primary item ID for a single-image AVIF still. */
    const val PRIMARY_ITEM_ID: Int = 1

    /**
     * Per-channel bit-depth list shorthands matching
     * [AvifAuxiliaryBoxes.PixiPayload].
     */
    val BIT_DEPTHS_RGB_8: IntArray = intArrayOf(8, 8, 8)
    val BIT_DEPTHS_RGB_10: IntArray = intArrayOf(10, 10, 10)
    val BIT_DEPTHS_RGB_12: IntArray = intArrayOf(12, 12, 12)
    val BIT_DEPTHS_MONO_8: IntArray = intArrayOf(8)

    /**
     * Inputs to the AVIF still muxer.
     *
     * @param widthPx canvas width in pixels; surfaces in `ispe`.
     * @param heightPx canvas height in pixels; surfaces in `ispe`.
     * @param bitDepths per-channel bit depths; surfaces in `pixi`.
     *     Length 1 for monochrome, 3 for RGB, 4 for RGBA.
     * @param cicp the working-space colour tag; surfaces in `colr`
     *     as the `nclx` colour-type entry.
     * @param av1Bitstream the AV1 OBU sequence for the primary
     *     image item. Caller is responsible for ensuring this is
     *     a valid AV1 still bitstream (the muxer does not validate
     *     the bytes — that's the encoder's job).
     * @param rotation optional clockwise rotation; surfaces as
     *     `irot`. Spec allows 0 / 90 / 180 / 270 degrees only.
     * @param mirror optional mirror axis; surfaces as `imir`.
     *     Combine with [rotation] to express any of the 8 dihedral
     *     orientations.
     * @param pasp optional pixel-aspect ratio; surfaces as `pasp`.
     *     Default is square (1:1).
     * @param clap optional clean-aperture rectangle; surfaces as
     *     `clap`. Use [IsobmffSampleAspect.ClapPayload.centeredCropOf]
     *     for the typical top-left-anchored crop case.
     * @param mdcv optional SMPTE ST 2086 mastering-display
     *     metadata; surfaces as `mdcv`. Required for HDR content
     *     graded against a reference monitor.
     * @param clli optional CTA-861.3 content light-level info;
     *     surfaces as `clli`. Strongly recommended whenever
     *     [mdcv] is set.
     */
    data class Input(
        val widthPx: Int,
        val heightPx: Int,
        val bitDepths: IntArray,
        val cicp: Cicp,
        val av1Bitstream: ByteArray,
        val rotation: AvifAuxiliaryBoxes.Rotation? = null,
        val mirror: AvifAuxiliaryBoxes.MirrorAxis? = null,
        val pasp: IsobmffSampleAspect.PaspPayload? = null,
        val clap: IsobmffSampleAspect.ClapPayload? = null,
        val mdcv: MasteringDisplayMetadata? = null,
        val clli: ContentLightLevel? = null,
    ) {
        init {
            require(widthPx >= 1) { "widthPx must be >= 1; got $widthPx" }
            require(heightPx >= 1) { "heightPx must be >= 1; got $heightPx" }
            require(bitDepths.isNotEmpty()) { "bitDepths must not be empty" }
            require(av1Bitstream.isNotEmpty()) { "av1Bitstream must not be empty" }
        }
    }

    /**
     * Produce a complete AVIF still file as a single byte array.
     *
     * The returned buffer is the byte sequence a writer would
     * `flush()` to disk: `ftyp` + `meta` + `mdat`. No additional
     * framing is needed.
     */
    fun encode(input: Input): ByteArray {
        val ftypBox = FileTypeBox.encodeAvifStillBox()

        val ipcoBuilder = IsobmffItemProperties.Builder()
        val associations = mutableListOf<ItemPropertyAssociation.Association>()

        val ispeBox = ImageSpatialExtents.encodeBox(input.widthPx, input.heightPx)
        val ispeIdx = ipcoBuilder.add(ispeBox)
        associations.add(ItemPropertyAssociation.Association(propertyIndex = ispeIdx, essential = true))

        val pixiPayload = AvifAuxiliaryBoxes.encodePixi(AvifAuxiliaryBoxes.PixiPayload(input.bitDepths))
        val pixiBox = IsobmffBox.encodeBox("pixi", pixiPayload)
        val pixiIdx = ipcoBuilder.add(pixiBox)
        associations.add(ItemPropertyAssociation.Association(propertyIndex = pixiIdx, essential = true))

        val colrPayload = AvifColrPayload.encodeNclxPayload(input.cicp)
        val colrBox = IsobmffBox.encodeBox("colr", colrPayload)
        val colrIdx = ipcoBuilder.add(colrBox)
        associations.add(ItemPropertyAssociation.Association(propertyIndex = colrIdx, essential = true))

        if (input.rotation != null) {
            val irotBox = IsobmffBox.encodeBox("irot", AvifAuxiliaryBoxes.encodeIrot(input.rotation))
            val idx = ipcoBuilder.add(irotBox)
            associations.add(ItemPropertyAssociation.Association(propertyIndex = idx, essential = false))
        }
        if (input.mirror != null) {
            val imirBox = IsobmffBox.encodeBox("imir", AvifAuxiliaryBoxes.encodeImir(input.mirror))
            val idx = ipcoBuilder.add(imirBox)
            associations.add(ItemPropertyAssociation.Association(propertyIndex = idx, essential = false))
        }
        if (input.pasp != null) {
            val paspBox = IsobmffBox.encodeBox("pasp", IsobmffSampleAspect.encodePasp(input.pasp))
            val idx = ipcoBuilder.add(paspBox)
            associations.add(ItemPropertyAssociation.Association(propertyIndex = idx, essential = false))
        }
        if (input.clap != null) {
            val clapBox = IsobmffBox.encodeBox("clap", IsobmffSampleAspect.encodeClap(input.clap))
            val idx = ipcoBuilder.add(clapBox)
            associations.add(ItemPropertyAssociation.Association(propertyIndex = idx, essential = false))
        }
        if (input.mdcv != null) {
            val mdcvBox = IsobmffBox.encodeBox("mdcv", HdrStaticMetadata.encodeMdcvPayload(input.mdcv))
            val idx = ipcoBuilder.add(mdcvBox)
            associations.add(ItemPropertyAssociation.Association(propertyIndex = idx, essential = false))
        }
        if (input.clli != null) {
            val clliBox = IsobmffBox.encodeBox("clli", HdrStaticMetadata.encodeClliPayload(input.clli))
            val idx = ipcoBuilder.add(clliBox)
            associations.add(ItemPropertyAssociation.Association(propertyIndex = idx, essential = false))
        }

        val ipcoBox = ipcoBuilder.build()
        val ipmaBox = ItemPropertyAssociation.encodeBox(
            listOf(
                ItemPropertyAssociation.Entry(
                    itemId = PRIMARY_ITEM_ID.toLong(),
                    associations = associations.toList(),
                ),
            ),
        )
        val iprpBox = IsobmffItemProperties.encodeIprpBox(ipcoBox, listOf(ipmaBox))

        val hdlrBox = HandlerReferenceBox.encodePictBox()
        val pitmBox = PrimaryItemBox.encodeBox(PRIMARY_ITEM_ID.toLong())

        val infeBox = ItemInfoEntry.encodeBox(
            ItemInfoEntry.Entry(
                itemId = PRIMARY_ITEM_ID.toLong(),
                itemType = ItemInfoEntry.ITEM_TYPE_AV01,
            ),
        )
        val iinfBox = ItemInfoBox.encodeBox(listOf(infeBox))

        // ----------------------------------------------------------------
        // Two-pass offset planning. Build an iloc with placeholder offsets
        // that have the same byte width as the real offsets, compute the
        // meta size, then rebuild iloc with the real offsets and verify
        // the size didn't change.
        // ----------------------------------------------------------------

        val placeholderIloc = ItemLocationBox.encodeBox(
            listOf(
                ItemLocationBox.Item(
                    itemId = PRIMARY_ITEM_ID.toLong(),
                    extents = listOf(
                        ItemLocationBox.Extent(
                            offset = 1L,
                            length = input.av1Bitstream.size.toLong(),
                        ),
                    ),
                ),
            ),
        )

        val metaPlaceholder = MetaBox.Builder()
            .setHandler(hdlrBox)
            .setPrimaryItem(pitmBox)
            .setItemInfo(iinfBox)
            .setItemLocation(placeholderIloc)
            .setItemProperties(iprpBox)
            .build()

        val mdatHeaderSize = MediaDataBox.headerSize(input.av1Bitstream.size.toLong())
        val mdatStart = ftypBox.size.toLong() + metaPlaceholder.size.toLong()
        val av1Offset = mdatStart + mdatHeaderSize.toLong()

        val realIloc = ItemLocationBox.encodeBox(
            listOf(
                ItemLocationBox.Item(
                    itemId = PRIMARY_ITEM_ID.toLong(),
                    extents = listOf(
                        ItemLocationBox.Extent(
                            offset = av1Offset,
                            length = input.av1Bitstream.size.toLong(),
                        ),
                    ),
                ),
            ),
        )

        check(realIloc.size == placeholderIloc.size) {
            "iloc size changed between placeholder and real pass " +
                "(placeholder=${placeholderIloc.size}, real=${realIloc.size}); " +
                "this indicates the field-size selection crossed a uint32 / " +
                "uint16 boundary mid-encode and the muxer needs a re-pass loop"
        }

        val metaBox = MetaBox.Builder()
            .setHandler(hdlrBox)
            .setPrimaryItem(pitmBox)
            .setItemInfo(iinfBox)
            .setItemLocation(realIloc)
            .setItemProperties(iprpBox)
            .build()

        val mdatBox = MediaDataBox.encodeBox(input.av1Bitstream)

        val out = ByteArrayOutputStream(ftypBox.size + metaBox.size + mdatBox.size)
        out.write(ftypBox)
        out.write(metaBox)
        out.write(mdatBox)
        return out.toByteArray()
    }

    /**
     * Convenience that picks 8-bit RGB depths and the sRGB
     * working-space tag — the most common still-image case for a
     * camera app exporting an SDR JPEG-class still.
     */
    fun encodeSrgbStill(
        widthPx: Int,
        heightPx: Int,
        av1Bitstream: ByteArray,
    ): ByteArray = encode(
        Input(
            widthPx = widthPx,
            heightPx = heightPx,
            bitDepths = BIT_DEPTHS_RGB_8,
            cicp = WorkingSpace.SRGB.cicp,
            av1Bitstream = av1Bitstream,
        ),
    )
}
