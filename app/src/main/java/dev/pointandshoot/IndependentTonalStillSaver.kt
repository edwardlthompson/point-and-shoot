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
import android.util.Log
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
        return when {
            decision.tonalWritten == TonalContainer.JpegXl12Bit && !decision.fallbackJpeg ->
                writeNativeJxl(appContext, storageProfile, rgb, w, h, characteristics, captureResult, loc, filenameSuffix)
            decision.tonalWritten == TonalContainer.Avif10BitHdr && !decision.fallbackJpeg ->
                writeNativeAvif(appContext, storageProfile, rgb, w, h, characteristics, captureResult, loc, filenameSuffix)
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
                    downgradeMessage = null,
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
                    CaptureStorage.CaptureKind.Avif10BitHdr,
                    enc.bytes,
                    characteristics,
                    captureResult,
                    location,
                    filenameSuffix,
                    downgradeMessage = null,
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
                )
            }
        }
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
    ): SaveOutcome {
        val px = rgb888ToArgbPixels(rgb888, width, height)
        val bmp = Bitmap.createBitmap(px, width, height, Bitmap.Config.ARGB_8888)
        var handle: CaptureStorage.Handle? = null
        return try {
            handle =
                CaptureStorage.openOutput(
                    appContext.applicationContext,
                    storageProfile,
                    CaptureStorage.CaptureKind.JpegSdr,
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
            StillCaptureMetadata.applyToJpegUri(
                appContext.applicationContext,
                uri,
                characteristics,
                captureResult,
                location = null,
            )
            Log.i(TAG, "tonal still saved as JPEG displayName=$displayName downgrade=${downgradeReason != null}")
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
        downgradeMessage: String?,
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
            StillCaptureMetadata.applyToJpegUri(
                appContext.applicationContext,
                uri,
                characteristics,
                captureResult,
                location = location,
            )
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
