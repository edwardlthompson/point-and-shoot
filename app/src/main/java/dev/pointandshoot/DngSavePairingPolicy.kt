package dev.pointandshoot

/**
 * Shipped DNG metadata pairing policy for [DngMetadataResolver.resolveForDngSave] and
 * [ReferenceAppDngCreatorPair.forSave] call sites in [PreviewEngineScreen].
 *
 * Stays **false** while RAW/JPEG outputs are unpinned from the preview physical id — see
 * `.cursor/rules/dng-logical-multicam-metadata-lock.mdc` and [AGENTS.md] CRITICAL DNG pairing.
 */
object DngSavePairingPolicy {
    const val ALLOW_PHYSICAL_TOTAL_RESULT_PAIRING: Boolean = false
}
