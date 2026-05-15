package dev.pointandshoot

import android.graphics.ImageFormat
import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.NotFoundException
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.ReaderException
import com.google.zxing.common.HybridBinarizer
import java.util.EnumSet
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

private const val TAG = "PNS.QrScan"

/** Minimum wall time between decode attempts on the analysis thread (Sprint 10.9). */
internal const val QR_SCAN_DECODE_MIN_INTERVAL_MS = 280L

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

/**
 * CameraX **Preview** + **ImageAnalysis** (YUV_420_888) with ZXing decode.
 * Y rows are copied into a tight buffer when `rowStride > width` (stride-safe).
 */
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

@Suppress("FunctionNaming")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QrScanScreen(
    hasCameraPermission: Boolean,
    onRequestCameraPermission: () -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var previewView by remember { mutableStateOf<PreviewView?>(null) }
    var lastText by remember { mutableStateOf<String?>(null) }
    var lastFormat by remember { mutableStateOf<String?>(null) }
    val reader = remember { MultiFormatReader().apply { setHints(readerHints) } }
    val lastDecodeAttemptMs = remember { AtomicLong(0L) }

    BackHandler(onBack = onBack)

    DisposableEffect(lifecycleOwner, hasCameraPermission, previewView) {
        val pv = previewView
        if (!hasCameraPermission || pv == null) {
            return@DisposableEffect onDispose { }
        }
        val released = AtomicBoolean(false)
        val appCtx = context.applicationContext
        val mainExecutor = ContextCompat.getMainExecutor(context)
        val decodeExecutor = Executors.newSingleThreadExecutor()
        val cameraProviderFuture = ProcessCameraProvider.getInstance(appCtx)
        val bindRunnable = Runnable {
            if (released.get()) return@Runnable
            val provider = runCatching { cameraProviderFuture.get() }.getOrNull() ?: return@Runnable
            if (released.get()) return@Runnable
            val rotation = pv.display.rotation
            val preview =
                Preview.Builder()
                    .setTargetRotation(rotation)
                    .build()
                    .also { it.surfaceProvider = pv.surfaceProvider }
            val analysis =
                ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
                    .setTargetRotation(rotation)
                    .build()
            analysis.setAnalyzer(decodeExecutor) { image ->
                try {
                    val now = android.os.SystemClock.elapsedRealtime()
                    if (now - lastDecodeAttemptMs.get() < QR_SCAN_DECODE_MIN_INTERVAL_MS) {
                        return@setAnalyzer
                    }
                    lastDecodeAttemptMs.set(now)
                    val yTight = copyYPlaneTight(image) ?: return@setAnalyzer
                    val w = image.width
                    val h = image.height
                    val source =
                        PlanarYUVLuminanceSource(
                            yTight,
                            w,
                            h,
                            0,
                            0,
                            w,
                            h,
                            false,
                        )
                    val bitmap = BinaryBitmap(HybridBinarizer(source))
                    try {
                        val result = reader.decodeWithState(bitmap)
                        Log.i(
                            TAG,
                            "decode ok format=${result.barcodeFormat} len=${result.text.length}",
                        )
                        mainExecutor.execute {
                            lastText = result.text
                            lastFormat = result.barcodeFormat.toString()
                        }
                    } catch (_: NotFoundException) {
                    } catch (e: ReaderException) {
                        Log.w(TAG, "decode reader", e)
                    } finally {
                        reader.reset()
                    }
                } finally {
                    image.close()
                }
            }
            provider.unbindAll()
            provider.bindToLifecycle(
                lifecycleOwner,
                CameraSelector.DEFAULT_BACK_CAMERA,
                preview,
                analysis,
            )
        }
        cameraProviderFuture.addListener(bindRunnable, mainExecutor)
        onDispose {
            released.set(true)
            mainExecutor.execute {
                runCatching {
                    if (cameraProviderFuture.isDone) {
                        cameraProviderFuture.get().unbindAll()
                    }
                }
                decodeExecutor.shutdown()
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("QR / barcode scan") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = "Back",
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 16.dp),
        ) {
            if (!hasCameraPermission) {
                Text(
                    text = "Camera permission is required for live scan.",
                    style = MaterialTheme.typography.bodyLarge,
                )
                Spacer(modifier = Modifier.height(12.dp))
                Button(onClick = onRequestCameraPermission) {
                    Text("Grant camera permission")
                }
            } else {
                AndroidView(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .weight(1f),
                    factory = { ctx ->
                        PreviewView(ctx).apply {
                            scaleType = PreviewView.ScaleType.FILL_CENTER
                            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                            post { previewView = this }
                        }
                    },
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "CameraX ImageAnalysis (YUV) + ZXing. Throttle ≈ ${QR_SCAN_DECODE_MIN_INTERVAL_MS}ms.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = if (lastText != null) "Last: $lastFormat" else "No code yet — point at a QR or barcode.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                if (lastText != null) {
                    Text(
                        text = lastText!!,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        }
    }
}
