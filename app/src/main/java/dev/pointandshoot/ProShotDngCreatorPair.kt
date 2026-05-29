package dev.pointandshoot

import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.hardware.camera2.TotalCaptureResult
import dev.pointandshoot.fleet.OnePlus13FleetPolicy

/**
 * ProShot `m0.RunnableC0539s`: `new DngCreator(openedCharacteristics, stillTotalCaptureResult)`.
 * No [DngMetadataResolver] fork on leaf sessions (empty [CameraCharacteristics.getPhysicalCameraIds]).
 */
object ProShotDngCreatorPair {
    fun forSave(
        cm: CameraManager,
        sessionCameraId: String,
        sessionCharacteristics: CameraCharacteristics,
        totalResult: TotalCaptureResult,
        previewPhysicalCameraId: String?,
        allowPhysicalTotalResultPairing: Boolean,
    ): Pair<CameraCharacteristics, TotalCaptureResult> {
        if (
            OnePlus13FleetPolicy.useProShotPureDngSave() &&
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
