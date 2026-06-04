package dev.pointandshoot

import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.hardware.camera2.TotalCaptureResult
import dev.pointandshoot.fleet.LegacyFleetPolicy

/**
 * ReferenceCam `m0.RunnableC0539s`: `new DngCreator(openedCharacteristics, stillTotalCaptureResult)`.
 * No [DngMetadataResolver] fork on leaf sessions (empty [CameraCharacteristics.getPhysicalCameraIds]).
 */
object ReferenceAppDngCreatorPair {
    fun forSave(
        cm: CameraManager,
        sessionCameraId: String,
        sessionCharacteristics: CameraCharacteristics,
        totalResult: TotalCaptureResult,
        previewPhysicalCameraId: String?,
        allowPhysicalTotalResultPairing: Boolean,
    ): Pair<CameraCharacteristics, TotalCaptureResult> {
        if (
            LegacyFleetPolicy.useReferenceAppPureDngSave() &&
            StillCaptureIqPolicy.isLeafBackCharacteristics(sessionCharacteristics)
        ) {
            return sessionCharacteristics to totalResult
        }
        return DngMetadataResolver.pairForDngCreator(
            cm,
            sessionCameraId,
            sessionCharacteristics,
            totalResult,
            previewPhysicalCameraId,
            allowPhysicalTotalResultPairing,
        )
    }
}
