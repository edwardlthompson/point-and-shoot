package dev.pointandshoot

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CaptureResult
import android.hardware.camera2.TotalCaptureResult
import android.media.Image
import android.net.Uri
import android.os.Build
import android.util.Log
import java.io.ByteArrayOutputStream
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

/**
 * Writes the **-JPEG-** band as its own MediaStore file (AVIF / JXL / downgrade JPEG), never as a
 * RAW companion. Input is a hardware JPEG [Image] from a separate still capture request.
 */
object IndependentTonalStillSaver {

    private const val TAG = "PNS.TonalStill"

    /** Wall-clock cap for full-resolution libavif/libjxl on the tonal encode executor. */
    private const val NATIVE_ENCODE_TIMEOUT_MS = 120_000L

    private val nativeEncodeExecutor = Executors.newSingleThreadExecutor { r ->
        Thread(r, "PNS.TonalNativeEncode").apply { isDaemon = true }
    }

    data class SaveOutcome(
        val uri: Uri?,
        val displayName: String?,
        val downgradeMessage: String?,
    )

    fun saveFromHardwareJpeg(
        appContext: Context,
        storageProfile: ImagingProfile,
        tonalBundle: StillCaptureBundle,
        jpegImage: Image,
        stillsLut: LutCatalog,
        characteristics: CameraCharacteristics,
        captureResult: TotalCaptureResult,
        orientationDegrees: Int,
        softwareJpegQuality: Int,
        filenameSuffix: String? = null,
        lightweightMetadata: Boolean = false,
    ): SaveOutcome {
        val jpegBytes = copyJpegImageToByteArray(jpegImage)
        return saveFromHardwareJpegBytes(
            appContext = appContext,
            storageProfile = storageProfile,
            tonalBundle = tonalBundle,
            jpegBytes = jpegBytes,
            stillsLut = stillsLut,
            characteristics = characteristics,
            captureResult = captureResult,
            orientationDegrees = orientationDegrees,
            softwareJpegQuality = softwareJpegQuality,
            filenameSuffix = filenameSuffix,
            lightweightMetadata = lightweightMetadata,
        )
    }

    /**
     * Tonal encode from a copied hardware-JPEG buffer (safe after [Image.close] and session resume).
     */
    fun saveFromHardwareJpegBytes(
        appContext: Context,
        storageProfile: ImagingProfile,
        tonalBundle: StillCaptureBundle,
        jpegBytes: ByteArray,
        stillsLut: LutCatalog,
        characteristics: CameraCharacteristics,
        captureResult: TotalCaptureResult,
        orientationDegrees: Int,
        softwareJpegQuality: Int,
        filenameSuffix: String? = null,
        lightweightMetadata: Boolean = false,
    ): SaveOutcome {
        val decision = EncoderRoute.decide(tonalBundle, NativeEncoders.isAvailable)
        val decoded =
            BitmapFactory.decodeByteArray(jpegBytes, 0, jpegBytes.size)
                ?: run {
                    Log.w(TAG, "tonal still decode failed")
                    return SaveOutcome(null, null, null)
                }
        val oriented = rotateBitmap(decoded, orientationDegrees)
        if (oriented !== decoded) {
            decoded.recycle()
        }
        val w = oriented.width
        val h = oriented.height
        if (w <= 0 || h <= 0) {
            oriented.recycle()
            return SaveOutcome(null, null, null)
        }
        val rgb = bitmapToRgb888(oriented)
        oriented.recycle()
        StillCaptureColorApply.applyToRgb888InPlace(appContext, rgb, w, h, stillsLut)
        val loc = null
        val colorTarget = tonalBundle.colorSpace
        return when {
            decision.tonalWritten == TonalContainer.JpegXl12Bit && !decision.fallbackJpeg ->
                writeNativeJxl(
                    appContext,
                    storageProfile,
                    rgb,
                    w,
                    h,
                    characteristics,
                    captureResult,
                    loc,
                    filenameSuffix,
                    colorTarget,
                    lightweightMetadata = lightweightMetadata,
                )
            decision.tonalWritten == TonalContainer.Avif10BitHdr && !decision.fallbackJpeg ->
                writeNativeAvif(
                    appContext,
                    storageProfile,
                    rgb,
                    w,
                    h,
                    characteristics,
                    captureResult,
                    loc,
                    filenameSuffix,
                    colorTarget = colorTarget,
                    lightweightMetadata = lightweightMetadata,
                )
            decision.tonalWritten == TonalContainer.Heic10Bit && !decision.fallbackJpeg ->
                writeNativeHeic(
                    appContext,
                    storageProfile,
                    rgb,
                    w,
                    h,
                    characteristics,
                    captureResult,
                    loc,
                    filenameSuffix,
                    colorTarget = colorTarget,
                    lightweightMetadata = lightweightMetadata,
                )
            decision.tonalWritten == TonalContainer.Tiff16 && !decision.fallbackJpeg ->
                writeTiff16(
                    appContext,
                    storageProfile,
                    rgb,
                    w,
                    h,
                    characteristics,
                    captureResult,
                    filenameSuffix,
                    colorTarget,
                    lightweightMetadata = lightweightMetadata,
                )
            decision.tonalWritten == TonalContainer.MotionPhotoJpeg8 && !decision.fallbackJpeg ->
                writeFallbackJpeg(
                    appContext,
                    storageProfile,
                    rgb,
                    w,
                    h,
                    characteristics,
                    captureResult,
                    softwareJpegQuality,
                    filenameSuffix,
                    downgradeReason = null,
                    colorTarget = colorTarget,
                    outputKind = CaptureStorage.CaptureKind.MotionPhoto,
                    lightweightMetadata = lightweightMetadata,
                )
            else ->
                writeFallbackJpeg(
                    appContext,
                    storageProfile,
                    rgb,
                    w,
                    h,
                    characteristics,
                    captureResult,
                    softwareJpegQuality,
                    filenameSuffix,
                    downgradeReason = decision.downgradeReason,
                    colorTarget = colorTarget,
                    lightweightMetadata = lightweightMetadata,
                )
        }
    }

    /**
     * Sprint **15.29** — stacked RGB888 from NightScape (same tonal encode path as hardware JPEG).
     */
    fun saveFromStackedRgb888(
        appContext: Context,
        storageProfile: ImagingProfile,
        tonalBundle: StillCaptureBundle,
        rgb888: ByteArray,
        width: Int,
        height: Int,
        stillsLut: LutCatalog,
        characteristics: CameraCharacteristics,
        captureResult: TotalCaptureResult,
        orientationDegrees: Int,
        softwareJpegQuality: Int,
        filenameSuffix: String? = "nightscape",
    ): SaveOutcome {
        if (width <= 0 || height <= 0) return SaveOutcome(null, null, null)
        val rgb = rgb888.copyOf()
        StillCaptureColorApply.applyToRgb888InPlace(appContext, rgb, width, height, stillsLut)
        val decision = EncoderRoute.decide(tonalBundle, NativeEncoders.isAvailable)
        val loc = null
        val colorTarget = tonalBundle.colorSpace
        return when {
            decision.tonalWritten == TonalContainer.JpegXl12Bit && !decision.fallbackJpeg ->
                writeNativeJxl(
                    appContext,
                    storageProfile,
                    rgb,
                    width,
                    height,
                    characteristics,
                    captureResult,
                    loc,
                    filenameSuffix,
                    colorTarget = colorTarget,
                )
            decision.tonalWritten == TonalContainer.Avif10BitHdr && !decision.fallbackJpeg ->
                writeNativeAvif(
                    appContext,
                    storageProfile,
                    rgb,
                    width,
                    height,
                    characteristics,
                    captureResult,
                    loc,
                    filenameSuffix,
                    colorTarget = colorTarget,
                )
            decision.tonalWritten == TonalContainer.Heic10Bit && !decision.fallbackJpeg ->
                writeNativeHeic(
                    appContext,
                    storageProfile,
                    rgb,
                    width,
                    height,
                    characteristics,
                    captureResult,
                    loc,
                    filenameSuffix,
                    colorTarget = colorTarget,
                )
            decision.tonalWritten == TonalContainer.Tiff16 && !decision.fallbackJpeg ->
                writeTiff16(
                    appContext,
                    storageProfile,
                    rgb,
                    width,
                    height,
                    characteristics,
                    captureResult,
                    filenameSuffix,
                    colorTarget,
                )
            decision.tonalWritten == TonalContainer.MotionPhotoJpeg8 && !decision.fallbackJpeg ->
                writeFallbackJpeg(
                    appContext,
                    storageProfile,
                    rgb,
                    width,
                    height,
                    characteristics,
                    captureResult,
                    softwareJpegQuality,
                    filenameSuffix,
                    downgradeReason = null,
                    colorTarget = colorTarget,
                    outputKind = CaptureStorage.CaptureKind.MotionPhoto,
                )
            else ->
                writeFallbackJpeg(
                    appContext,
                    storageProfile,
                    rgb,
                    width,
                    height,
                    characteristics,
                    captureResult,
                    softwareJpegQuality,
                    filenameSuffix,
                    downgradeReason = decision.downgradeReason,
                    colorTarget = colorTarget,
                )
        }
    }

    internal fun copyJpegImageToByteArray(image: Image): ByteArray {
        val buf = image.planes[0].buffer
        val bytes = ByteArray(buf.remaining())
        buf.get(bytes)
        return bytes
    }

    fun captureKindFor(decision: EncoderRoute.Decision): CaptureStorage.CaptureKind =
        when {
            decision.tonalWritten == TonalContainer.JpegXl12Bit && !decision.fallbackJpeg ->
                CaptureStorage.CaptureKind.JpegXl12Bit
            decision.tonalWritten == TonalContainer.Avif10BitHdr && !decision.fallbackJpeg ->
                CaptureStorage.CaptureKind.Avif10BitHdr
            decision.tonalWritten == TonalContainer.Heic10Bit && !decision.fallbackJpeg ->
                CaptureStorage.CaptureKind.Heic10Bit
            decision.tonalWritten == TonalContainer.MotionPhotoJpeg8 && !decision.fallbackJpeg ->
                CaptureStorage.CaptureKind.MotionPhoto
            decision.tonalWritten == TonalContainer.Tiff16 && !decision.fallbackJpeg ->
                CaptureStorage.CaptureKind.Tiff16
            else -> CaptureStorage.CaptureKind.JpegSdr
        }

    private fun writeNativeJxl(
        appContext: Context,
        storageProfile: ImagingProfile,
        rgb888: ByteArray,
        width: Int,
        height: Int,
        characteristics: CameraCharacteristics,
        captureResult: TotalCaptureResult,
        location: android.location.Location?,
        filenameSuffix: String?,
        colorTarget: ColorSpaceTarget,
        lightweightMetadata: Boolean = false,
    ): SaveOutcome {
        val plane1216 = rgb888ToRgb1216LittleEndian(rgb888, width, height)
        val stride = width * 6
        Log.i(TAG, "JXL encode start ${width}x$height")
        val enc =
            runNativeEncodeWithTimeout("JXL") {
                NativeEncoders.encodeJxl12Rec2020(plane1216, width, height, stride)
            }
        return when (enc) {
            is NativeEncoders.Result.Success -> {
                Log.i(TAG, "JXL encode ok bytes=${enc.bytes.size}")
                writeEncodedBytes(
                    appContext,
                    storageProfile,
                    CaptureStorage.CaptureKind.JpegXl12Bit,
                    enc.bytes,
                    characteristics,
                    captureResult,
                    location,
                    filenameSuffix,
                    colorTarget = colorTarget,
                    downgradeMessage = null,
                    lightweightMetadata = lightweightMetadata,
                )
            }
            null -> {
                Log.w(TAG, "JXL encode timed out — standalone JPEG fallback")
                writeFallbackJpeg(
                    appContext,
                    storageProfile,
                    rgb888,
                    width,
                    height,
                    characteristics,
                    captureResult,
                    softwareJpegQuality = 100,
                    filenameSuffix = filenameSuffix,
                    downgradeReason = "JPEG XL encode timed out; saved as JPEG instead.",
                    colorTarget = colorTarget,
                    lightweightMetadata = lightweightMetadata,
                )
            }
            else -> {
                Log.w(TAG, "JXL native encode unavailable — standalone JPEG fallback")
                writeFallbackJpeg(
                    appContext,
                    storageProfile,
                    rgb888,
                    width,
                    height,
                    characteristics,
                    captureResult,
                    softwareJpegQuality = 100,
                    filenameSuffix = filenameSuffix,
                    downgradeReason = EncoderRoute.DOWNGRADE_MESSAGE,
                    colorTarget = colorTarget,
                    lightweightMetadata = lightweightMetadata,
                )
            }
        }
    }

    private fun runNativeEncodeWithTimeout(
        label: String,
        block: () -> NativeEncoders.Result,
    ): NativeEncoders.Result? {
        val future = nativeEncodeExecutor.submit(Callable { block() })
        return try {
            future.get(NATIVE_ENCODE_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        } catch (e: TimeoutException) {
            future.cancel(true)
            Log.w(TAG, "$label encode timed out after ${NATIVE_ENCODE_TIMEOUT_MS}ms")
            null
        } catch (t: Throwable) {
            Log.w(TAG, "$label encode failed: ${t.message}")
            NativeEncoders.Result.NativeError(code = -1, message = t.message)
        }
    }

    private fun writeNativeAvif(
        appContext: Context,
        storageProfile: ImagingProfile,
        rgb888: ByteArray,
        width: Int,
        height: Int,
        characteristics: CameraCharacteristics,
        captureResult: TotalCaptureResult,
        location: android.location.Location?,
        filenameSuffix: String?,
        colorTarget: ColorSpaceTarget,
        outputKind: CaptureStorage.CaptureKind = CaptureStorage.CaptureKind.Avif10BitHdr,
        lightweightMetadata: Boolean = false,
    ): SaveOutcome {
        val yuv = rgb888ToYuv420(rgb888, width, height)
        val enc =
            runNativeEncodeWithTimeout("AVIF") {
                NativeEncoders.encodeAvif10Hdr(
                    yuv.planeY,
                    yuv.planeU,
                    yuv.planeV,
                    width,
                    height,
                    yuv.strideY,
                    yuv.strideUv,
                )
            }
        return when (enc) {
            is NativeEncoders.Result.Success ->
                writeEncodedBytes(
                    appContext,
                    storageProfile,
                    outputKind,
                    enc.bytes,
                    characteristics,
                    captureResult,
                    location,
                    filenameSuffix,
                    colorTarget = colorTarget,
                    downgradeMessage = null,
                    lightweightMetadata = lightweightMetadata,
                )
            null -> {
                Log.w(TAG, "AVIF encode timed out — standalone JPEG fallback")
                writeFallbackJpeg(
                    appContext,
                    storageProfile,
                    rgb888,
                    width,
                    height,
                    characteristics,
                    captureResult,
                    softwareJpegQuality = 92,
                    filenameSuffix = filenameSuffix,
                    downgradeReason = "AVIF encode timed out; saved as JPEG instead.",
                    colorTarget = colorTarget,
                    outputKind = outputKind,
                    lightweightMetadata = lightweightMetadata,
                )
            }
            else -> {
                Log.w(TAG, "AVIF native encode unavailable — standalone JPEG fallback")
                writeFallbackJpeg(
                    appContext,
                    storageProfile,
                    rgb888,
                    width,
                    height,
                    characteristics,
                    captureResult,
                    softwareJpegQuality = 92,
                    filenameSuffix = filenameSuffix,
                    downgradeReason = EncoderRoute.DOWNGRADE_MESSAGE,
                    colorTarget = colorTarget,
                    outputKind = outputKind,
                    lightweightMetadata = lightweightMetadata,
                )
            }
        }
    }

    private fun writeNativeHeic(
        appContext: Context,
        storageProfile: ImagingProfile,
        rgb888: ByteArray,
        width: Int,
        height: Int,
        characteristics: CameraCharacteristics,
        captureResult: TotalCaptureResult,
        location: android.location.Location?,
        filenameSuffix: String?,
        colorTarget: ColorSpaceTarget,
        lightweightMetadata: Boolean = false,
    ): SaveOutcome {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            return writeFallbackJpeg(
                appContext = appContext,
                storageProfile = storageProfile,
                rgb888 = rgb888,
                width = width,
                height = height,
                characteristics = characteristics,
                captureResult = captureResult,
                softwareJpegQuality = 100,
                filenameSuffix = filenameSuffix,
                downgradeReason = "HEIC needs API 30+; saved as JPEG instead.",
                colorTarget = colorTarget,
                lightweightMetadata = lightweightMetadata,
            )
        }
        val heicFormat =
            runCatching {
                Bitmap.CompressFormat::class.java.getField("HEIC").get(null) as Bitmap.CompressFormat
            }.getOrNull()
        if (heicFormat == null) {
            return writeFallbackJpeg(
                appContext = appContext,
                storageProfile = storageProfile,
                rgb888 = rgb888,
                width = width,
                height = height,
                characteristics = characteristics,
                captureResult = captureResult,
                softwareJpegQuality = 100,
                filenameSuffix = filenameSuffix,
                downgradeReason = "HEIC encoder unavailable; saved as JPEG instead.",
                colorTarget = colorTarget,
                lightweightMetadata = lightweightMetadata,
            )
        }
        val bmp = rgb888ToArgbBitmap(rgb888, width, height)
        val bytes =
            try {
                ByteArrayOutputStream().use { out ->
                    val ok = bmp.compress(heicFormat, 100, out)
                    if (ok) out.toByteArray() else null
                }
            } catch (t: Throwable) {
                Log.w(TAG, "HEIC encode failed: ${t.message}")
                null
            } finally {
                bmp.recycle()
            }
        if (bytes == null) {
            return writeFallbackJpeg(
                appContext = appContext,
                storageProfile = storageProfile,
                rgb888 = rgb888,
                width = width,
                height = height,
                characteristics = characteristics,
                captureResult = captureResult,
                softwareJpegQuality = 100,
                filenameSuffix = filenameSuffix,
                downgradeReason = "HEIC encode failed; saved as JPEG instead.",
                colorTarget = colorTarget,
                lightweightMetadata = lightweightMetadata,
            )
        }
        return writeEncodedBytes(
            appContext = appContext,
            storageProfile = storageProfile,
            kind = CaptureStorage.CaptureKind.Heic10Bit,
            bytes = bytes,
            characteristics = characteristics,
            captureResult = captureResult,
            location = location,
            filenameSuffix = filenameSuffix,
            colorTarget = colorTarget,
            downgradeMessage = null,
            lightweightMetadata = lightweightMetadata,
        )
    }

    private fun rgb888ToArgbBitmap(
        rgb888: ByteArray,
        width: Int,
        height: Int,
    ): Bitmap {
        val argb = IntArray(width * height)
        var src = 0
        for (i in argb.indices) {
            val r = rgb888[src++].toInt() and 0xFF
            val g = rgb888[src++].toInt() and 0xFF
            val b = rgb888[src++].toInt() and 0xFF
            argb[i] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
        }
        return Bitmap.createBitmap(argb, width, height, Bitmap.Config.ARGB_8888)
    }

    private fun writeTiff16(
        appContext: Context,
        storageProfile: ImagingProfile,
        rgb888: ByteArray,
        width: Int,
        height: Int,
        characteristics: CameraCharacteristics,
        captureResult: TotalCaptureResult,
        filenameSuffix: String?,
        colorTarget: ColorSpaceTarget,
        lightweightMetadata: Boolean = false,
    ): SaveOutcome {
        val tiff =
            RgbTiff16Encoder.encodeRgb888AsTiff16(
                rgb888,
                width,
                height,
                exif =
                    RgbTiff16Encoder.CaptureExif(
                        exposureTimeNs = captureResult.get(CaptureResult.SENSOR_EXPOSURE_TIME),
                        aperture = captureResult.get(CaptureResult.LENS_APERTURE),
                        iso = captureResult.get(CaptureResult.SENSOR_SENSITIVITY),
                        focalMm = captureResult.get(CaptureResult.LENS_FOCAL_LENGTH),
                    ),
            )
        return writeEncodedBytes(
            appContext = appContext,
            storageProfile = storageProfile,
            kind = CaptureStorage.CaptureKind.Tiff16,
            bytes = tiff,
            characteristics = characteristics,
            captureResult = captureResult,
            location = null,
            filenameSuffix = filenameSuffix,
            colorTarget = colorTarget,
            downgradeMessage = null,
            lightweightMetadata = lightweightMetadata,
        )
    }

    private fun writeFallbackJpeg(
        appContext: Context,
        storageProfile: ImagingProfile,
        rgb888: ByteArray,
        width: Int,
        height: Int,
        characteristics: CameraCharacteristics,
        captureResult: TotalCaptureResult,
        softwareJpegQuality: Int,
        filenameSuffix: String?,
        downgradeReason: String?,
        colorTarget: ColorSpaceTarget,
        outputKind: CaptureStorage.CaptureKind = CaptureStorage.CaptureKind.JpegSdr,
        lightweightMetadata: Boolean = false,
    ): SaveOutcome {
        val px = rgb888ToArgbPixels(rgb888, width, height)
        val bmp = Bitmap.createBitmap(px, width, height, Bitmap.Config.ARGB_8888)
        var handle: CaptureStorage.Handle? = null
        return try {
            handle =
                CaptureStorage.openOutput(
                    appContext.applicationContext,
                    storageProfile,
                    outputKind,
                    useLocationBridge = false,
                    filenameSuffix = filenameSuffix,
                )
            val q = softwareJpegQuality.coerceIn(70, 100)
            if (!bmp.compress(Bitmap.CompressFormat.JPEG, q, handle.output)) {
                throw IllegalStateException("JPEG compress failed")
            }
            val displayName = handle.displayName
            val uri = handle.uri
            handle.close()
            handle = null
            // Always tag fallback JPEG payload with the intended output color space.
            if (!lightweightMetadata) {
                val stripPrivacy = PreviewChromePreferences.load(appContext).stripExifPrivacyTags
                StillCaptureMetadata.applyToJpegUri(
                    appContext.applicationContext,
                    uri,
                    characteristics,
                    captureResult,
                    location = null,
                    colorSpaceTarget = colorTarget,
                    stripPrivacyExif = stripPrivacy,
                )
                updateImageDescription(appContext.applicationContext, uri, outputKind, colorTarget, captureResult)
            }
            Log.i(
                TAG,
                "tonal still saved kind=${outputKind.extension} displayName=$displayName downgrade=${downgradeReason != null}",
            )
            SaveOutcome(uri, displayName, downgradeReason)
        } catch (t: Throwable) {
            Log.w(TAG, "tonal JPEG fallback save failed", t)
            runCatching { handle?.discard() }
            SaveOutcome(null, null, downgradeReason)
        } finally {
            bmp.recycle()
        }
    }

    private fun writeEncodedBytes(
        appContext: Context,
        storageProfile: ImagingProfile,
        kind: CaptureStorage.CaptureKind,
        bytes: ByteArray,
        characteristics: CameraCharacteristics,
        captureResult: TotalCaptureResult,
        location: android.location.Location?,
        filenameSuffix: String?,
        colorTarget: ColorSpaceTarget,
        downgradeMessage: String?,
        lightweightMetadata: Boolean = false,
    ): SaveOutcome {
        var handle: CaptureStorage.Handle? = null
        return try {
            handle =
                CaptureStorage.openOutput(
                    appContext.applicationContext,
                    storageProfile,
                    kind,
                    useLocationBridge = false,
                    filenameSuffix = filenameSuffix,
                )
            handle.output.write(bytes)
            val displayName = handle.displayName
            val uri = handle.uri
            handle.close()
            handle = null
            if (!lightweightMetadata) {
                val stripPrivacy = PreviewChromePreferences.load(appContext).stripExifPrivacyTags
                val metaLocation = if (stripPrivacy) null else location
                when (kind) {
                    CaptureStorage.CaptureKind.Avif10BitHdr ->
                        StillCaptureMetadata.applyToAvifUri(
                            appContext.applicationContext,
                            uri,
                            characteristics,
                            captureResult,
                            location = metaLocation,
                            colorSpaceTarget = colorTarget,
                            stripPrivacyExif = stripPrivacy,
                        )
                    CaptureStorage.CaptureKind.JpegSdr,
                    CaptureStorage.CaptureKind.MotionPhoto,
                    ->
                        StillCaptureMetadata.applyToJpegUri(
                            appContext.applicationContext,
                            uri,
                            characteristics,
                            captureResult,
                            location = metaLocation,
                            colorSpaceTarget = colorTarget,
                            stripPrivacyExif = stripPrivacy,
                        )
                    CaptureStorage.CaptureKind.Tiff16 ->
                        StillCaptureMetadata.applyToTiffUri(
                            appContext.applicationContext,
                            uri,
                            characteristics,
                            captureResult,
                            location = metaLocation,
                            stripPrivacyExif = stripPrivacy,
                        )
                    else -> Unit
                }
                updateImageDescription(appContext.applicationContext, uri, kind, colorTarget, captureResult)
            }
            Log.i(TAG, "tonal still saved kind=${kind.extension} displayName=$displayName")
            SaveOutcome(uri, displayName, downgradeMessage)
        } catch (t: Throwable) {
            Log.w(TAG, "tonal container save failed kind=${kind.extension}", t)
            runCatching { handle?.discard() }
            SaveOutcome(null, null, downgradeMessage)
        }
    }

    private fun rotateBitmap(src: Bitmap, orientationDegrees: Int): Bitmap {
        val deg = ((orientationDegrees % 360) + 360) % 360
        if (deg == 0) return src
        val m = Matrix().apply { postRotate(deg.toFloat()) }
        return Bitmap.createBitmap(src, 0, 0, src.width, src.height, m, true)
    }

    private fun updateImageDescription(
        context: Context,
        uri: Uri,
        kind: CaptureStorage.CaptureKind,
        colorTarget: ColorSpaceTarget,
        captureResult: TotalCaptureResult,
    ) {
        runCatching {
            val iso = captureResult.get(CaptureResult.SENSOR_SENSITIVITY)
            val expNs = captureResult.get(CaptureResult.SENSOR_EXPOSURE_TIME)
            val focal = captureResult.get(CaptureResult.LENS_FOCAL_LENGTH)
            val aperture = captureResult.get(CaptureResult.LENS_APERTURE)
            val desc =
                buildString {
                    append("pnsStill kind=").append(kind.extension)
                    append(" color=").append(colorTarget.displayName)
                    iso?.let { append(" iso=").append(it) }
                    expNs?.let { append(" expNs=").append(it) }
                    focal?.let { append(" focalMm=").append(String.format(java.util.Locale.US, "%.2f", it)) }
                    aperture?.let { append(" aperture=").append(String.format(java.util.Locale.US, "%.2f", it)) }
                }
            val values = android.content.ContentValues().apply {
                put(android.provider.MediaStore.Images.Media.DESCRIPTION, desc)
            }
            context.contentResolver.update(uri, values, null, null)
        }.onFailure { e ->
            Log.w(TAG, "MediaStore description update failed uri=$uri: ${e.message}")
        }
    }

    private fun bitmapToRgb888(bmp: Bitmap): ByteArray {
        val w = bmp.width
        val h = bmp.height
        val px = IntArray(w * h)
        bmp.getPixels(px, 0, w, 0, 0, w, h)
        val rgb = ByteArray(w * h * 3)
        var o = 0
        for (c in px) {
            rgb[o++] = ((c shr 16) and 0xFF).toByte()
            rgb[o++] = ((c shr 8) and 0xFF).toByte()
            rgb[o++] = (c and 0xFF).toByte()
        }
        return rgb
    }

    private fun rgb888ToArgbPixels(rgb: ByteArray, width: Int, height: Int): IntArray {
        val px = IntArray(width * height)
        var o = 0
        for (i in px.indices) {
            val r = rgb[o++].toInt() and 0xFF
            val g = rgb[o++].toInt() and 0xFF
            val b = rgb[o++].toInt() and 0xFF
            px[i] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
        }
        return px
    }

    /** 12-bit RGB in 16-bit little-endian lanes for [NativeEncoders.encodeJxl12Rec2020]. */
    internal fun rgb888ToRgb1216LittleEndian(rgb888: ByteArray, width: Int, height: Int): ByteArray {
        val out = ByteArray(width * height * 6)
        var i = 0
        var o = 0
        while (i < rgb888.size) {
            val r8 = rgb888[i++].toInt() and 0xFF
            val g8 = rgb888[i++].toInt() and 0xFF
            val b8 = rgb888[i++].toInt() and 0xFF
            val r12 = (r8 shl 8) or r8
            val g12 = (g8 shl 8) or g8
            val b12 = (b8 shl 8) or b8
            out[o++] = (r12 and 0xFF).toByte()
            out[o++] = (r12 shr 8).toByte()
            out[o++] = (g12 and 0xFF).toByte()
            out[o++] = (g12 shr 8).toByte()
            out[o++] = (b12 and 0xFF).toByte()
            out[o++] = (b12 shr 8).toByte()
        }
        return out
    }

    /** Simple BT.601 YUV420 planes for AVIF native encode attempts. */
    internal fun rgb888ToYuv420(
        rgb888: ByteArray,
        width: Int,
        height: Int,
    ): Yuv420Planes {
        val ySize = width * height
        val uvW = (width + 1) / 2
        val uvH = (height + 1) / 2
        val planeY = ByteArray(ySize)
        val planeU = ByteArray(uvW * uvH)
        val planeV = ByteArray(uvW * uvH)
        var ri = 0
        for (row in 0 until height) {
            for (col in 0 until width) {
                val r = rgb888[ri++].toInt() and 0xFF
                val g = rgb888[ri++].toInt() and 0xFF
                val b = rgb888[ri++].toInt() and 0xFF
                val y = ((66 * r + 129 * g + 25 * b + 128) shr 8) + 16
                planeY[row * width + col] = y.coerceIn(0, 255).toByte()
                if (row % 2 == 0 && col % 2 == 0) {
                    val u = ((-38 * r - 74 * g + 112 * b + 128) shr 8) + 128
                    val v = ((112 * r - 94 * g - 18 * b + 128) shr 8) + 128
                    val uvIndex = (row / 2) * uvW + (col / 2)
                    planeU[uvIndex] = u.coerceIn(0, 255).toByte()
                    planeV[uvIndex] = v.coerceIn(0, 255).toByte()
                }
            }
        }
        return Yuv420Planes(planeY, planeU, planeV, strideY = width, strideUv = uvW)
    }

    internal data class Yuv420Planes(
        val planeY: ByteArray,
        val planeU: ByteArray,
        val planeV: ByteArray,
        val strideY: Int,
        val strideUv: Int,
    )
}
