package dev.pointandshoot

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CaptureResult
import android.hardware.camera2.DngCreator
import android.hardware.camera2.TotalCaptureResult
import dev.pointandshoot.fleet.LeafDngHalReconcile
import dev.pointandshoot.LeafDngFleetPolicies
import dev.pointandshoot.fleet.ReferenceAppPipelineContract
import dev.pointandshoot.DngBayerAsShotNeutral
import android.location.Location
import android.media.Image
import android.os.SystemClock
import android.util.Log
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream

/**
 * Wraps Camera2's [DngCreator] for the two RAW DNG variants we need from
 * [ImagingProfile].
 *
 *   * `RawMode.LosslessCompressedDng`  (Standard Pro)
 *   * `RawMode.UncompressedRaw12Dng`   (Ultra-Max)
 *
 * The RAW12 path requires the camera session to deliver `ImageFormat.RAW12`
 * (or `RAW_SENSOR` with a 12-bit sensor) - this saver does not configure the
 * session; it only writes the bytes after [Image] readout.
 *
 * When still capture uses the same [CaptureRequest.SCALER_CROP_REGION] strategy as
 * [SensorCropGeometry] / [PreviewEngineScreen], the HAL delivers a RAW buffer that
 * matches the crop; [DngCreator] embeds standard DNG dimensions from
 * [TotalCaptureResult]. Host-side [DngDefaultUserCropRatios] mirrors the crop for
 * tooling; Adobe-style DefaultUserCrop tags are primarily derived by the platform
 * writer from characteristics + result.
 *
 * **UniqueCameraModel (DNG tag 50708):** public [android.hardware.camera2.DngCreator]
 * does not expose a setter. When [save] is given a non-blank [uniqueCameraModel], the written
 * bytes are post-processed with [TiffUniqueCameraModel50708.appendTag50708] (full-file buffer;
 * large RAWs may use substantial heap — failure falls back to the unpatched DNG).
 *
 * Desktop **calibration metadata**: public [android.hardware.camera2.DngCreator]
 * cannot set `ColorMatrix1` / `ForwardMatrix1`. When a [CalibrationProfile]
 * exists on disk, the capture engine writes a sibling `.pns-calibration.json`
 * sidecar (see [DngCalibrationSidecar] + [CaptureStorage.openCalibrationSidecarOutput])
 * with [DngColorTags] payloads for offline RAW tooling.
 *
 * Threading:
 *   * Construction is cheap and can happen on any thread.
 *   * [save] performs blocking IO and *must* be called off the main thread
 *     (use the IO executor of the capture pipeline).
 *
 * No proprietary blobs are involved - `DngCreator` is part of the AOSP
 * Camera2 framework.
 */
class Dng12Saver(
    private val characteristics: CameraCharacteristics,
    private val profile: ImagingProfile,
) {

    /**
     * Write a single DNG to [destination]. The [image] must remain valid
     * (not closed) for the duration of the call. [captureResult] should be
     * the `TotalCaptureResult` matching that frame so the DNG metadata
     * (orientation, exposure, white balance, etc.) reflects the actual shot.
     *
     * The output stream is **not** closed by this method - the caller owns
     * the lifecycle of the stream. This makes it safe to pipe through
     * MediaStore inserts, content-resolver pipes, or app-private files.
     *
     * @return SaveStats describing wall time + bytes written.
     */
    fun save(
        image: Image,
        captureResult: TotalCaptureResult,
        destination: OutputStream,
        orientationDegrees: Int = 0,
        location: Location? = null,
        /**
         * Optional text stamped via [DngCreator.setDescription] (DNG auxiliary metadata).
         * Use [DngLutMetadata.formatSoftwareTag] so desktop tools can grep `LUT=` / `SHA256=`.
         */
        softwareDescription: String? = null,
        /**
         * When non-blank, appended as DNG tag **50708** (`UniqueCameraModel`) via
         * [TiffUniqueCameraModel50708] (requires buffering the full DNG in memory).
         */
        uniqueCameraModel: String? = null,
        /** Opened [android.hardware.camera2.CameraDevice] id for LegacyDevice leaf HAL reconcile. */
        sessionCameraId: String? = null,
        /**
         * When set for aux leaf (UW/tele), CM/FM in the DNG are patched from wide cam **2** while
         * [characteristics] / [captureResult] still pair with the opened aux session for [DngCreator].
         */
        wideCalibrationCharacteristics: CameraCharacteristics? = null,
        /** When non-null, gates [PnsAdbValidation] success lines to debuggable / diagnostics builds. */
        adbValidationContext: Context? = null,
    ): SaveStats {
        val started = SystemClock.elapsedRealtimeNanos()

        // GPS only when the caller passes a [Location] (still pipeline uses [PreviewController.locationForStillMetadata]).
        val fix = location
        val creator = DngCreator(characteristics, captureResult).apply {
            setOrientation(orientationDegrees.toExifOrientation())
            if (fix != null) {
                runCatching { setLocation(fix) }
                    .onFailure { Log.w(TAG, "DngCreator.setLocation failed", it) }
            }
            if (!softwareDescription.isNullOrBlank() && !DngSaveBisectState.skipDngSoftwareDescription) {
                runCatching { setDescription(softwareDescription) }
                    .onFailure { Log.w(TAG, "DngCreator.setDescription failed", it) }
            }
        }
        logDngColorDiag(characteristics, captureResult, sessionCameraId)

        // Compression hint: DngCreator on Android does not expose a public
        // "uncompressed vs lossless" toggle; the underlying TIFF writer is
        // lossless by default. The `UncompressedRaw12Dng` profile is honored
        // by configuring the *session* to deliver RAW12 frames - this saver
        // simply writes whatever bit depth Camera2 hands us. The profile is
        // recorded here for downstream observability.
        Log.d(TAG, "writing DNG profile=${profile.id} raw=${profile.rawMode}")

        val stamp50708 = !uniqueCameraModel.isNullOrBlank()
        val reconcileLeaf =
            !sessionCameraId.isNullOrBlank() &&
                ReferenceAppPipelineContract.leafPostSaveTiffReconcileEnabled(sessionCameraId)
        val wideCal = wideCalibrationCharacteristics != null
        Log.i(
            TAG,
            "dng openability diag cam=$sessionCameraId reconcile=$reconcileLeaf " +
                "wideCal=$wideCal stamp50708=$stamp50708 " +
                "pureReferenceApp=${LeafDngFleetPolicies.active.useReferenceAppPureDngSave()}",
        )
        if (!stamp50708 && !reconcileLeaf) {
            creator.writeImage(destination, image)
        } else {
            val preWriteBayerEstimate =
                if (
                    reconcileLeaf &&
                    LeafDngHalReconcile.shouldEstimateBayerBeforeWrite(sessionCameraId)
                ) {
                    DngBayerAsShotNeutral.estimateWithBayerRatios(
                        characteristics,
                        image,
                        captureResult,
                    )
                } else {
                    null
                }
            val preWriteBayerAsn = preWriteBayerEstimate?.asn
            val baos = ByteArrayOutputStream()
            creator.writeImage(baos, image)
            var rawBytes = baos.toByteArray()
            if (reconcileLeaf) {
                rawBytes =
                    LeafDngHalReconcile.applyPostDngCreatorPatches(
                        rawBytes,
                        characteristics,
                        captureResult,
                        image,
                        sessionCameraId = sessionCameraId,
                        preWriteBayerAsn = preWriteBayerAsn,
                        preWriteBayerEstimate = preWriteBayerEstimate,
                        wideCalibrationCharacteristics = wideCalibrationCharacteristics,
                        assetContext = adbValidationContext,
                    )
                adbValidationContext?.let {
                    PnsAdbLog.i(it, "dng leaf HAL reconcile ok cam=$sessionCameraId")
                }
            }
            if (stamp50708) {
                val patchResult =
                    runCatching {
                        TiffUniqueCameraModel50708.appendTag50708(rawBytes, uniqueCameraModel.trim())
                    }
                rawBytes =
                    patchResult.getOrElse { e ->
                        Log.w(TAG, "UniqueCameraModel (50708) append failed; writing unpatched DNG", e)
                        rawBytes
                    }
                if (patchResult.isSuccess) {
                    adbValidationContext?.let {
                        PnsAdbLog.i(it, "dng UniqueCameraModel 50708 IFD append ok")
                    }
                }
            }
            destination.write(rawBytes)
        }
        destination.flush()

        val elapsedNs = SystemClock.elapsedRealtimeNanos() - started
        // Save capture result metadata to sidecar file for gallery reading
        try {
            val metadataFile = File(destination.toString().replace(".dng", ".pns-metadata.json"))
            val metadata = mapOf(
                "fNumber" to captureResult.get(CaptureResult.LENS_APERTURE),
                "iso" to captureResult.get(CaptureResult.SENSOR_SENSITIVITY),
                "exposureTime" to captureResult.get(CaptureResult.SENSOR_EXPOSURE_TIME),
                "focalLength" to captureResult.get(CaptureResult.LENS_FOCAL_LENGTH),
                "aperture" to captureResult.get(CaptureResult.LENS_APERTURE)
            )
            val jsonString = metadata.entries.joinToString(",", "{", "}") { "${it.key}:${it.value}" }
            metadataFile.writeText(jsonString)
            Log.d("Dng12Saver", "Saved metadata sidecar: ${metadataFile.absolutePath}")
        } catch (e: Exception) {
            Log.e("Dng12Saver", "Failed to save metadata sidecar", e)
        }

        return SaveStats(elapsedNs = elapsedNs, profileId = profile.id, rawMode = profile.rawMode)
    }

    /** Convenience overload that writes to [file] (creating parents as needed). */
    fun save(
        image: Image,
        captureResult: TotalCaptureResult,
        file: File,
        orientationDegrees: Int = 0,
        location: Location? = null,
        softwareDescription: String? = null,
        uniqueCameraModel: String? = null,
        sessionCameraId: String? = null,
        wideCalibrationCharacteristics: CameraCharacteristics? = null,
        adbValidationContext: Context? = null,
    ): SaveStats {
        file.parentFile?.mkdirs()
        return FileOutputStream(file).use { os ->
            save(
                image,
                captureResult,
                os,
                orientationDegrees,
                location,
                softwareDescription,
                uniqueCameraModel,
                sessionCameraId,
                wideCalibrationCharacteristics,
                adbValidationContext,
            )
        }
    }

    data class SaveStats(
        val elapsedNs: Long,
        val profileId: String,
        val rawMode: RawMode,
    ) {
        val elapsedMs: Double get() = elapsedNs / 1_000_000.0
    }

    companion object {
        private const val TAG = "PNS.Dng"

        /**
         * Map a clockwise device-rotation value (0/90/180/270) to the EXIF
         * orientation enum that `DngCreator.setOrientation` expects.
         */
        private fun Int.toExifOrientation(): Int = when (((this % 360) + 360) % 360) {
            0 -> 1   // ORIENTATION_NORMAL
            90 -> 6  // ORIENTATION_ROTATE_90
            180 -> 3 // ORIENTATION_ROTATE_180
            270 -> 8 // ORIENTATION_ROTATE_270
            else -> 1
        }

        private fun logDngColorDiag(
            characteristics: CameraCharacteristics,
            captureResult: TotalCaptureResult,
            sessionCameraId: String?,
        ) {
            val cm1 = characteristics.get(CameraCharacteristics.SENSOR_COLOR_TRANSFORM1)
            val cm2 = characteristics.get(CameraCharacteristics.SENSOR_COLOR_TRANSFORM2)
            val fm1 = characteristics.get(CameraCharacteristics.SENSOR_FORWARD_MATRIX1)
            val fm2 = characteristics.get(CameraCharacteristics.SENSOR_FORWARD_MATRIX2)
            val gains = captureResult.get(CaptureResult.COLOR_CORRECTION_GAINS)
            val neutral = captureResult.get(CaptureResult.SENSOR_NEUTRAL_COLOR_POINT)
            val gainStr =
                if (gains != null) {
                    "R=${"%.3f".format(gains.red)} G=${"%.3f".format((gains.greenEven + gains.greenOdd) / 2f)} B=${"%.3f".format(gains.blue)}"
                } else {
                    "null"
                }
            Log.i(
                TAG,
                "dng color diag cam=$sessionCameraId gains=$gainStr " +
                    "cm1=${matrixDiag(cm1)} cm2=${matrixDiag(cm2)} " +
                    "fm1=${matrixDiag(fm1)} fm2=${matrixDiag(fm2)} neutral=${neutral?.contentToString() ?: "null"}",
            )
        }

        private fun matrixDiag(m: android.hardware.camera2.params.ColorSpaceTransform?): String {
            if (m == null) return "null"
            return runCatching {
                val e = m.getElement(0, 0)
                "${e.numerator}/${e.denominator}"
            }.getOrElse { "?" }
        }
    }
}
