package dev.pointandshoot.preview.session

import android.graphics.ImageFormat
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.MultiResolutionImageReader
import android.hardware.camera2.params.MultiResolutionStreamInfo
import android.hardware.camera2.params.OutputConfiguration
import android.hardware.camera2.params.StreamConfigurationMap
import android.media.ImageReader
import android.os.Build
import android.util.Size
import android.view.Surface
import androidx.annotation.RequiresApi
import dev.pointandshoot.ImagingProfile
import dev.pointandshoot.PerfBudget
import dev.pointandshoot.PhotoResolutionMode
import dev.pointandshoot.RawCaptureSupport

/**
 * JPEG companion / tonal still [ImageReader] setup for REGULAR preview sessions (H.CRI-5 slice 6).
 *
 * Extracted from `PreviewEngineScreen.configureJpegCompanionReader`.
 */
object PreviewSessionJpegCompanion {
    data class Input(
        val characteristics: CameraCharacteristics?,
        val streamConfigurationMap: StreamConfigurationMap?,
        val imagingProfileForStreams: ImagingProfile,
        val stillPhotoResolutionMode: PhotoResolutionMode,
        val wantsIndependentTonalStill: Boolean,
        val wantsJpegSidecarOnRaw: Boolean,
    )

    sealed class SkipReason {
        data object JpegOnlyNoSizes : SkipReason()

        data object TonalOff : SkipReason()

        data object NoJpegSizes : SkipReason()
    }

    sealed class Outcome {
        data class Skipped(
            val reason: SkipReason,
        ) : Outcome()

        data class MultiResolution(
            val reader: MultiResolutionImageReader,
            val outputConfigurations: List<OutputConfiguration>,
        ) : Outcome()

        data class SingleReader(
            val reader: ImageReader,
            val size: Size,
        ) : Outcome()
    }

    fun shouldAttachJpegSurface(
        jpegOnlySession: Boolean,
        wantsIndependentTonalStill: Boolean,
        wantsJpegSidecarOnRaw: Boolean,
        jpegSize: Size?,
    ): Boolean =
        jpegOnlySession ||
            (wantsIndependentTonalStill && jpegSize != null) ||
            (wantsJpegSidecarOnRaw && jpegSize != null)

    fun shouldTryExperimentalMultiResolution(
        jpegOnlySession: Boolean,
        stillPhotoResolutionMode: PhotoResolutionMode,
        characteristics: CameraCharacteristics?,
    ): Boolean =
        jpegOnlySession &&
            stillPhotoResolutionMode == PhotoResolutionMode.MaxResolution &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            characteristics != null

    fun configure(
        input: Input,
        surfaces: MutableList<Surface>,
        extraOutputConfigs: MutableList<OutputConfiguration>,
        onDebugLog: (String) -> Unit,
        onPipelineEvent: (width: Int, height: Int, stillResStorageId: String) -> Unit,
    ): Outcome {
        val chars = input.characteristics
        val mapForStreams = input.streamConfigurationMap
        val jpegSize =
            chars?.let {
                RawCaptureSupport.pickJpegOutputSizeForStill(it, input.stillPhotoResolutionMode)
            } ?: mapForStreams?.let { RawCaptureSupport.pickLargestJpegSize(it) }
        val jpegOnlySession = input.imagingProfileForStreams is ImagingProfile.JpegOnly
        if (
            !shouldAttachJpegSurface(
                jpegOnlySession = jpegOnlySession,
                wantsIndependentTonalStill = input.wantsIndependentTonalStill,
                wantsJpegSidecarOnRaw = input.wantsJpegSidecarOnRaw,
                jpegSize = jpegSize,
            )
        ) {
            val reason =
                when {
                    jpegOnlySession -> SkipReason.JpegOnlyNoSizes
                    !input.wantsIndependentTonalStill && !input.wantsJpegSidecarOnRaw -> SkipReason.TonalOff
                    else -> SkipReason.NoJpegSizes
                }
            return Outcome.Skipped(reason)
        }
        if (shouldTryExperimentalMultiResolution(jpegOnlySession, input.stillPhotoResolutionMode, chars)) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && chars != null) {
                val setup = tryCreateMultiResolutionJpegSetup(chars)
                if (setup != null) {
                    extraOutputConfigs.addAll(setup.outputConfigurations)
                    onDebugLog("JPEG MultiResolutionImageReader outputs=${setup.outputConfigurations.size}")
                    return Outcome.MultiResolution(setup.reader, setup.outputConfigurations)
                }
            }
        }
        val sz = jpegSize!!
        val reader =
            ImageReader.newInstance(
                sz.width,
                sz.height,
                ImageFormat.JPEG,
                PerfBudget.Defaults.STILL_IMAGE_READER_MAX_IMAGES,
            )
        surfaces.add(reader.surface)
        onDebugLog("JPEG ImageReader ${sz.width}x${sz.height}")
        onPipelineEvent(sz.width, sz.height, input.stillPhotoResolutionMode.storageId)
        return Outcome.SingleReader(reader, sz)
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private data class MultiResolutionJpegSetup(
        val reader: MultiResolutionImageReader,
        val outputConfigurations: List<OutputConfiguration>,
    )

    @RequiresApi(Build.VERSION_CODES.S)
    private fun tryCreateMultiResolutionJpegSetup(
        chars: CameraCharacteristics,
    ): MultiResolutionJpegSetup? =
        runCatching {
            @Suppress("UNCHECKED_CAST")
            val key =
                chars.keys.firstOrNull {
                    it.name == "android.scaler.multiResolutionStreamConfigurationMap"
                } as? CameraCharacteristics.Key<Any>
            val mrMap = key?.let { chars.get(it) } ?: return@runCatching null
            val infos =
                runCatching {
                    val m = mrMap.javaClass.getMethod("getOutputInfo", Int::class.javaPrimitiveType)
                    m.invoke(mrMap, ImageFormat.JPEG) as? Collection<*>
                }.getOrNull()
            val typedInfos =
                infos
                    ?.filterNotNull()
                    ?.takeIf { it.size > 1 }
                    ?.let { it as Collection<MultiResolutionStreamInfo> }
                    ?: return@runCatching null
            val reader =
                MultiResolutionImageReader(
                    typedInfos,
                    ImageFormat.JPEG,
                    PerfBudget.Defaults.STILL_IMAGE_READER_MAX_IMAGES,
                )
            @Suppress("UNCHECKED_CAST")
            val outputConfigurations =
                OutputConfiguration.createInstancesForMultiResolutionOutput(reader)
                    as Collection<OutputConfiguration>
            if (outputConfigurations.isEmpty()) {
                reader.close()
                return@runCatching null
            }
            MultiResolutionJpegSetup(reader, outputConfigurations.toList())
        }.getOrNull()
}
