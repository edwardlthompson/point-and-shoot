package dev.pointandshoot

/**
 * EncoderRoute - decides what file(s) the capture pipeline actually writes for
 * a given [ImagingProfile] given the runtime availability of native encoders
 * (libavif / libjxl). Pure-data, no Android imports, JUnit-tested.
 *
 * Per `BUILD_PLAN.md` §4 / `FAILURE_MATRIX.md` "Native encoders unavailable":
 *
 * - RAW (DNG) is ALWAYS written. It uses Camera2 `DngCreator`, which needs no
 *   NDK and never goes through libavif/libjxl. Both Standard Pro (lossless
 *   DNG) and Ultra-Max (uncompressed RAW12 DNG) survive a missing .so.
 * - The tonal container (AVIF / JXL) writes only when [NativeEncoders] reports
 *   `isAvailable = true`. Otherwise the route degrades to a JPEG fallback so
 *   the user still gets a viewable preview-resolution still.
 * - The HUD / diagnostics surface a one-shot "Native encoders unavailable;
 *   tonal output downgraded to JPEG" message when [Decision.fallbackJpeg] is
 *   `true` (per `FAILURE_MATRIX.md`).
 */
object EncoderRoute {
    /**
     * What the capture pipeline ends up writing for one capture. The capture
     * engine reads this BEFORE it allocates the encoder so it can pick the
     * right ImageReader format and bit depth.
     */
    data class Decision(
        val profile: ImagingProfile,
        /**
         * The RAW file that always gets written. Mirrors
         * [ImagingProfile.rawMode] - included here so the UI / sidecar writers
         * have a single source of truth.
         */
        val rawWritten: RawMode,
        /**
         * The tonal container actually written. `null` when [fallbackJpeg] is
         * `true` (the route writes a JPEG instead). When non-null, matches the
         * resolved [StillCaptureBundle.tonalContainer] passed to [decide].
         */
        val tonalWritten: TonalContainer?,
        /**
         * `true` when the route has substituted a JPEG for the missing native
         * encoder. The JPEG goes through Camera2's hardware encoder; no NDK
         * involvement. The HUD shows the downgrade message in this case.
         */
        val fallbackJpeg: Boolean,
        /**
         * Why the route picked the fallback (one-line, user-facing). `null`
         * when [fallbackJpeg] is `false`.
         */
        val downgradeReason: String?,
    ) {
        /**
         * Total file count that the capture engine is responsible for writing
         * for this decision (RAW + tonal-or-jpeg + sidecar(s)). Convenience for
         * progress reporting and storage budget checks.
         */
        val fileCountForCapture: Int
            get() =
                when {
                    rawWritten == RawMode.None -> 1
                    tonalWritten != null -> 2
                    fallbackJpeg -> 2
                    else -> 1
                }
    }

    /** User-facing message when the route falls back. Public for test parity. */
    const val DOWNGRADE_MESSAGE: String =
        "Native encoders unavailable; tonal output downgraded to JPEG."

    /**
     * Decide what the capture pipeline writes for [profile] given whether the
     * native encoder library is loaded.
     */
    fun decide(profile: ImagingProfile, nativeAvailable: Boolean): Decision =
        decide(legacyStillBundle(profile), nativeAvailable)

    /**
     * Decides capture outputs from a resolved [StillCaptureBundle] (independent RAW vs HDR tiers).
     * [Decision.profile] is [storageProfileFromBundle] for MediaStore folder layout only.
     */
    fun decide(bundle: StillCaptureBundle, nativeAvailable: Boolean): Decision {
        val profile = storageProfileFromBundle(bundle)
        if (bundle.rawMode == RawMode.None) {
            val needsNative = bundle.tonalContainer.requiresNativeEncoder
            return if (!needsNative || nativeAvailable) {
                Decision(
                    profile = profile,
                    rawWritten = RawMode.None,
                    tonalWritten = bundle.tonalContainer,
                    fallbackJpeg = false,
                    downgradeReason = null,
                )
            } else {
                Decision(
                    profile = profile,
                    rawWritten = RawMode.None,
                    tonalWritten = null,
                    fallbackJpeg = true,
                    downgradeReason = DOWNGRADE_MESSAGE,
                )
            }
        }
        return Decision(
            profile = profile,
            rawWritten = bundle.rawMode,
            tonalWritten = null,
            fallbackJpeg = false,
            downgradeReason = null,
        )
    }

    /**
     * Convenience: which profiles in [ImagingProfile.all] will downgrade given
     * the current availability flag? Used by the diagnostics screen and by
     * the HUD's pre-capture banner.
     *
     * Implementation note: we enumerate the `data object` instances directly
     * rather than reading [ImagingProfile.all] because `data object` singletons
     * inside a sealed parent can be observed null during early companion init
     * on the JVM (the listOf(...) capture sees the in-progress
     * `StandardPro.INSTANCE` / `UltraMax.INSTANCE` field as null on the same
     * thread that triggered the parent class load). Touching the singletons
     * directly forces their full class initialization first.
     */
    fun downgradedProfiles(nativeAvailable: Boolean): List<ImagingProfile> {
        if (nativeAvailable) return emptyList()
        val candidates = listOf(ImagingProfile.StandardPro, ImagingProfile.UltraMax, ImagingProfile.JpegOnly)
        return candidates.filter { it !is ImagingProfile.JpegOnly && it.tonalContainer.requiresNativeEncoder }
    }
}

/**
 * Small extension property on [TonalContainer] - the AVIF / JXL containers
 * need the native encoder, JPEG (when added) does not. Centralized here so
 * the router and the diagnostics screen agree.
 */
val TonalContainer.requiresNativeEncoder: Boolean
    get() = when (this) {
        TonalContainer.Avif10BitHdr -> true
        TonalContainer.JpegXl12Bit -> true
        TonalContainer.Heic10Bit -> false
        TonalContainer.MotionPhotoJpeg8 -> false
        TonalContainer.Tiff16 -> false
        TonalContainer.JpegSdr8 -> false
    }
