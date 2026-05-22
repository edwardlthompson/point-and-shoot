package dev.pointandshoot

import android.graphics.ImageFormat
import android.media.Image
import android.util.Log
import androidx.camera.core.ImageProxy
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.NotFoundException
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.ReaderException
import com.google.zxing.common.HybridBinarizer
import java.util.EnumSet

/** ZXing decode result (Sprint **14.4** / Milestone 10.9). */
data class QrDecodeResult(
    val text: String,
    val format: String,
)

/**
 * Shared YUV → ZXing decode used by [QrScanScreen] (CameraX) and preview **QR** dial mode
 * (Camera2 YUV [ImageReader]).
 */
object QrCodeAnalyzer {
    const val TAG = "PNS.QrScan"

    private val supportedFormats: EnumSet<BarcodeFormat> =
        EnumSet.of(
            BarcodeFormat.QR_CODE,
            BarcodeFormat.AZTEC,
            BarcodeFormat.DATA_MATRIX,
            BarcodeFormat.PDF_417,
            BarcodeFormat.CODE_128,
            BarcodeFormat.CODE_39,
            BarcodeFormat.EAN_13,
            BarcodeFormat.EAN_8,
            BarcodeFormat.UPC_A,
            BarcodeFormat.UPC_E,
            BarcodeFormat.ITF,
            BarcodeFormat.CODABAR,
        )

    private val readerHints: Map<DecodeHintType, Any> =
        mapOf(DecodeHintType.POSSIBLE_FORMATS to supportedFormats)

    private val reader =
        MultiFormatReader().apply {
            setHints(readerHints)
        }

    fun tryDecodeFromImageProxy(image: ImageProxy): QrDecodeResult? =
        tryDecode(image.width, image.height, image.format) {
            copyYPlaneTight(image)
        }

    fun tryDecodeFromImage(image: Image): QrDecodeResult? =
        tryDecode(image.width, image.height, image.format) {
            copyYPlaneTight(image)
        }

    private inline fun tryDecode(
        width: Int,
        height: Int,
        format: Int,
        yPlaneProvider: () -> ByteArray?,
    ): QrDecodeResult? {
        if (format != ImageFormat.YUV_420_888) return null
        if (width <= 0 || height <= 0) return null
        val yTight = yPlaneProvider() ?: return null
        val source =
            PlanarYUVLuminanceSource(
                yTight,
                width,
                height,
                0,
                0,
                width,
                height,
                false,
            )
        val bitmap = BinaryBitmap(HybridBinarizer(source))
        return try {
            val result = reader.decodeWithState(bitmap)
            QrDecodeResult(
                text = result.text,
                format = result.barcodeFormat.toString(),
            )
        } catch (_: NotFoundException) {
            null
        } catch (e: ReaderException) {
            Log.w(TAG, "decode reader", e)
            null
        } finally {
            reader.reset()
        }
    }

    /** Y rows copied into a tight buffer when `rowStride > width` (stride-safe). */
    internal fun copyYPlaneTight(image: Image): ByteArray? {
        if (image.format != ImageFormat.YUV_420_888) return null
        val plane = image.planes.getOrNull(0) ?: return null
        if (plane.pixelStride != 1) return null
        val w = image.width
        val h = image.height
        if (w <= 0 || h <= 0) return null
        val rowStride = plane.rowStride
        val buf = plane.buffer.duplicate()
        buf.clear()
        val out = ByteArray(w * h)
        if (rowStride == w) {
            val n = minOf(buf.remaining(), w * h)
            buf.get(out, 0, n)
            return out
        }
        val rowScratch = ByteArray(rowStride)
        for (y in 0 until h) {
            buf.position(y * rowStride)
            val toRead = minOf(rowStride, buf.remaining())
            buf.get(rowScratch, 0, toRead)
            System.arraycopy(rowScratch, 0, out, y * w, w)
        }
        return out
    }

    internal fun copyYPlaneTight(image: ImageProxy): ByteArray? {
        if (image.format != ImageFormat.YUV_420_888) return null
        val plane = image.planes.getOrNull(0) ?: return null
        if (plane.pixelStride != 1) return null
        val w = image.width
        val h = image.height
        if (w <= 0 || h <= 0) return null
        val rowStride = plane.rowStride
        val buf = plane.buffer.duplicate()
        buf.clear()
        val out = ByteArray(w * h)
        if (rowStride == w) {
            val n = minOf(buf.remaining(), w * h)
            buf.get(out, 0, n)
            return out
        }
        val rowScratch = ByteArray(rowStride)
        for (y in 0 until h) {
            buf.position(y * rowStride)
            val toRead = minOf(rowStride, buf.remaining())
            buf.get(rowScratch, 0, toRead)
            System.arraycopy(rowScratch, 0, out, y * w, w)
        }
        return out
    }
}
