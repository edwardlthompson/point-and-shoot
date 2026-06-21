package dev.pointandshoot

/**
 * Product default (2026-06-19): save DNGs as HAL/[DngCreator] delivers them — no post-save color
 * surgery (ASN/CM/FM/50708/LUT software line). [StillCaptureMetadata.applyToDngUri] Make/Model/EXIF
 * patches remain enabled. Capture-time color IQ overrides on RAW stills are skipped when [ENABLED].
 */
object PureHalDngSavePolicy {

    const val ENABLED: Boolean = true
}
