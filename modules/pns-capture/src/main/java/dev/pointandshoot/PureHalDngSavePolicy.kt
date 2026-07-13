package dev.pointandshoot

/**
 * Product default (2026-06-19): save DNGs as HAL/[DngCreator] delivers them — no post-save **color
 * matrix / ForwardMatrix / 50708 / LUT** surgery. [StillCaptureMetadata.applyToDngUri] Make/Model/EXIF
 * patches remain enabled.
 *
 * When [ENABLED], skip **app color surgery** on the still request
 * ([LegacyLeafStillColorCorrection], linear-raw COLOR_CORRECTION overrides, preview-exposure latch).
 * Do **not** skip capability-gated [StillCaptureIqPolicy] (lens shading / advertised edge-NR-tonemap):
 * those keys are HAL inputs ProShot-class apps set so `DngCreator` embeds correct ASN/black levels —
 * fleet-generic, not model-gated (see REG-20260712-001).
 *
 * **Exception (2026-07-12):** [DngBayerAsnSyncPolicy] may still patch IFD0 **AsShotNeutral only**
 * (Bayer R + HAL B) under pure-HAL — see REG-20260712-007 / same-scene UW proof.
 */
object PureHalDngSavePolicy {

    const val ENABLED: Boolean = true
}
