package dev.pointandshoot

/** Mirrors [dev.pointandshoot.CameraCapabilitiesProbe] preview DNG bisect intent keys. */
object PreviewDngBisectIntentExtras {
    const val EXTRA_PNS_PREVIEW_DNG_SKIP_STILL_METADATA = "pns_preview_dng_skip_still_metadata"
    const val EXTRA_PNS_PREVIEW_DNG_SKIP_UNIQUE_CAMERA_MODEL = "pns_preview_dng_skip_unique_camera_model"
    const val EXTRA_PNS_PREVIEW_DNG_SKIP_JPEG_HINTS_STILL = "pns_preview_dng_skip_jpeg_hints_still"
    const val EXTRA_PNS_PREVIEW_DNG_FORCE_LEAF_RECONCILE = "pns_preview_dng_force_leaf_reconcile"
    const val EXTRA_PNS_PREVIEW_DNG_FORCE_BAYER_ASN = "pns_preview_dng_force_bayer_asn"
    const val EXTRA_PNS_PREVIEW_DNG_SKIP_SOFTWARE_DESC = "pns_preview_dng_skip_software_desc"
    const val EXTRA_PNS_PREVIEW_STILL_DNG_BACKEND = "pns_preview_still_dng_backend"

    /** Fleet exposure matrix E03 — skip pure-HAL AE_LOCK after precapture. */
    const val EXTRA_PNS_PREVIEW_DNG_SKIP_AE_LOCK = "pns_preview_dng_skip_ae_lock"

    /** Fleet exposure matrix E04 — override stopRepeating→still debounce ms (&gt;0). */
    const val EXTRA_PNS_PREVIEW_DNG_AFTER_STOP_DEBOUNCE_MS = "pns_preview_dng_after_stop_debounce_ms"

    /** Fleet exposure matrix E05 — skip ProShot weight-0 AE regions on RAW still. */
    const val EXTRA_PNS_PREVIEW_DNG_SKIP_AE_REGIONS = "pns_preview_dng_skip_ae_regions"

    /** Fleet exposure matrix E08 — CONTROL_AE_EXPOSURE_COMPENSATION steps (when advertised). */
    const val EXTRA_PNS_PREVIEW_DNG_AE_COMP_STEPS = "pns_preview_dng_ae_comp_steps"

    /** Fleet exposure matrix E09 — precapture settle uses TEMPLATE_STILL_CAPTURE. */
    const val EXTRA_PNS_PREVIEW_DNG_PRECAPTURE_STILL_TEMPLATE =
        "pns_preview_dng_precapture_still_template"

    /** Fleet exposure matrix E11 — skip StillCaptureIqPolicy on still. */
    const val EXTRA_PNS_PREVIEW_DNG_SKIP_STILL_IQ = "pns_preview_dng_skip_still_iq"

    /**
     * ProShot process rebuild (ADB-only): precapture via capture() while repeating runs (L6/f6),
     * then stop + STILL RAW±JPEG; no AE_LOCK; no Bayer ASN patch. Matrix cell PS01.
     */
    const val EXTRA_PNS_PREVIEW_DNG_PROSHOT_PIPELINE = "pns_preview_dng_proshot_pipeline"
}
