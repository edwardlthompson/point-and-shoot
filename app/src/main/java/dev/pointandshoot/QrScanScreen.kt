package dev.pointandshoot

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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/** Minimum wall time between decode attempts on the analysis thread (Sprint 10.9). */
internal const val QR_SCAN_DECODE_MIN_INTERVAL_MS = 280L

@Suppress("FunctionNaming")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QrScanScreen(
    hasCameraPermission: Boolean,
    onRequestCameraPermission: () -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val appContext = context.applicationContext
    val scope = rememberCoroutineScope()
    val snackbarHost = LocalPnsSnackbarHostState.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var previewView by remember { mutableStateOf<PreviewView?>(null) }
    var lastText by remember { mutableStateOf<String?>(null) }
    var lastFormat by remember { mutableStateOf<String?>(null) }
    var lastAction by remember { mutableStateOf<QrScanAction?>(null) }
    val lastDecodeAttemptMs = remember { AtomicLong(0L) }

    LaunchedEffect(lastText, lastFormat) {
        val text = lastText ?: return@LaunchedEffect
        val format = lastFormat
        lastAction = QrScanResultActions.resolve(text, format)
        QrScanResultActions.present(scope, snackbarHost, appContext, text, format)
    }

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
                    val decoded = QrCodeAnalyzer.tryDecodeFromImageProxy(image)
                    if (decoded != null) {
                        Log.i(
                            QrCodeAnalyzer.TAG,
                            "decode ok format=${decoded.format} len=${decoded.text.length}",
                        )
                        mainExecutor.execute {
                            lastText = decoded.text
                            lastFormat = decoded.format
                        }
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
                    val viewUri = lastAction as? QrScanAction.ViewUri
                    Row(
                        modifier = Modifier.padding(top = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        if (viewUri != null) {
                            OutlinedButton(
                                onClick = {
                                    val ok = QrScanResultActions.launchViewUri(appContext, viewUri.uri)
                                    if (!ok) {
                                        scope.pnsShowSnackbar(
                                            snackbarHost,
                                            "No app to open this",
                                            clipboardDetail = lastText,
                                            clipboardAppContext = appContext,
                                        )
                                    }
                                },
                            ) {
                                Text(viewUri.actionLabel)
                            }
                        }
                        TextButton(
                            onClick = {
                                lastText?.let { QrScanResultActions.copyToClipboard(appContext, it) }
                            },
                        ) {
                            Text("Copy")
                        }
                    }
                }
            }
        }
    }
}
