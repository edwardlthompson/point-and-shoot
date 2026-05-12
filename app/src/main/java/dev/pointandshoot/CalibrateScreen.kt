package dev.pointandshoot

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp

/**
 * Compose-driven Calibrate flow per BUILD_PLAN \u00a77 ("Phase 4 - Calibration
 * mode" / "In-app Calibrate flow"). The screen lets the user:
 *
 *   1. Pick a reference target ([BundledReferenceTargets.All]).
 *   2. Load a chart photo via SAF (any image MIME).
 *   3. Tap the four chart corners in TL \u2192 TR \u2192 BR \u2192 BL order
 *      (overlay shows the drawn quad live).
 *   4. Tap Compute - we run [BitmapRgbPlane.fromBitmap] +
 *      [CalibrationSampler.sample] + [CalibrationMath.computeWbGains] +
 *      [CalibrationMath.computeCcm] and surface the resulting profile.
 *   5. Tap Save - we persist via [CalibrationProfileStorage.save].
 *
 * Supports loading a chart via SAF, or a **one-shot** bitmap from the live preview
 * ([PreviewEngineScreen] grabs the current [TextureView] frame for Sprint 6.2).
 * The same Compute / Save pipeline applies in both cases.
 */
@Composable
fun CalibrateScreen(
    onBack: () -> Unit,
    /** Optional chart supplied by the caller (e.g. live preview grab); adopted into local state once. */
    initialChartBitmap: Bitmap? = null,
    onInitialChartBitmapConsumed: () -> Unit = {},
) {
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        PnsAdbLog.i(context, "calibrate screen compose active")
    }

    var target by remember { mutableStateOf<ReferenceTarget>(BundledReferenceTargets.Generic24) }
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    var corners by remember { mutableStateOf<List<Offset>>(emptyList()) }
    var displayedSize by remember { mutableStateOf(IntSize.Zero) }
    var profile by remember { mutableStateOf<CalibrationProfile?>(null) }
    var status by remember { mutableStateOf("Pick a chart photo and tap the four corners (TL \u2192 TR \u2192 BR \u2192 BL).") }
    var statusIsError by remember { mutableStateOf(false) }
    var savedPath by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(initialChartBitmap) {
        val init = initialChartBitmap ?: return@LaunchedEffect
        bitmap?.recycle()
        bitmap = init
        corners = emptyList()
        profile = null
        savedPath = null
        statusIsError = false
        status = "Tap the four corners of the chart in TL \u2192 TR \u2192 BR \u2192 BL order."
        onInitialChartBitmapConsumed()
    }

    val pickImage = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        val newBitmap = decodeBitmap(context, uri)
        if (newBitmap == null) {
            statusIsError = true
            status = "Could not decode image."
            return@rememberLauncherForActivityResult
        }
        bitmap?.recycle()
        bitmap = newBitmap
        corners = emptyList()
        profile = null
        savedPath = null
        statusIsError = false
        status = "Tap the four corners of the chart in TL \u2192 TR \u2192 BR \u2192 BL order."
    }

    val insets = rememberSystemInsetsDp()
    val padding: PaddingValues = insets.asPaddingValues(extra = 12.dp)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedButton(onClick = {
                bitmap?.recycle()
                bitmap = null
                corners = emptyList()
                profile = null
                savedPath = null
                onBack()
            }) { Text("Back") }
            Text(
                text = "Calibrate",
                style = MaterialTheme.typography.titleLarge,
            )
        }

        TargetPickerRow(
            current = target,
            onChange = {
                target = it
                profile = null
                corners = emptyList()
                statusIsError = false
                status = "Tap the four corners of the chart in TL \u2192 TR \u2192 BR \u2192 BL order."
            },
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { pickImage.launch(arrayOf("image/*")) }) {
                Text(if (bitmap == null) "Load chart photo\u2026" else "Replace chart photo\u2026")
            }
            if (corners.isNotEmpty()) {
                OutlinedButton(onClick = {
                    corners = emptyList()
                    profile = null
                    statusIsError = false
                    status = "Corners cleared - tap TL, TR, BR, BL again."
                }) { Text("Reset corners") }
            }
        }

        // Image surface with tap-to-add-corner.
        val imageBitmap = bitmap
        if (imageBitmap != null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(imageBitmap.width.toFloat() / imageBitmap.height.toFloat())
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.Black)
                    .onSizeChanged { displayedSize = it }
                    .pointerInput(imageBitmap) {
                        detectTapGestures { tap ->
                            if (corners.size >= 4) return@detectTapGestures
                            corners = corners + tap
                            statusIsError = false
                            status = when (corners.size) {
                                1 -> "TL set. Tap TR (top-right)."
                                2 -> "TR set. Tap BR (bottom-right)."
                                3 -> "BR set. Tap BL (bottom-left)."
                                4 -> "All four corners set. Tap Compute to solve calibration."
                                else -> status
                            }
                        }
                    },
            ) {
                androidx.compose.foundation.Image(
                    bitmap = imageBitmap.asImageBitmap(),
                    contentDescription = "Chart photo",
                    modifier = Modifier.fillMaxSize(),
                )
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val labels = listOf("TL", "TR", "BR", "BL")
                    corners.forEachIndexed { idx, p ->
                        drawCircle(
                            color = PnsColors.PhotoOrange,
                            radius = 14f,
                            center = p,
                            style = Stroke(width = 4f),
                        )
                    }
                    if (corners.size == 4) {
                        val path = androidx.compose.ui.graphics.Path().apply {
                            moveTo(corners[0].x, corners[0].y)
                            lineTo(corners[1].x, corners[1].y)
                            lineTo(corners[2].x, corners[2].y)
                            lineTo(corners[3].x, corners[3].y)
                            close()
                        }
                        drawPath(
                            path = path,
                            color = PnsColors.PhotoOrange.copy(alpha = 0.55f),
                            style = Stroke(width = 3f),
                        )
                    }
                }
            }
        } else {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(4f / 3f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.White.copy(alpha = 0.05f)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "(no chart photo loaded)",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.5f),
                )
            }
        }

        // Status / instructions strip.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(
                    if (statusIsError) PnsColors.RecordRed.copy(alpha = 0.15f)
                    else Color.White.copy(alpha = 0.06f),
                )
                .padding(10.dp),
        ) {
            Text(
                text = status,
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.95f),
            )
        }

        // Compute / Save row.
        val canCompute by remember(bitmap, corners) {
            derivedStateOf { bitmap != null && corners.size == 4 }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                enabled = canCompute,
                onClick = {
                    val bm = bitmap ?: return@Button
                    val pickedCorners = corners
                    val box = displayedSize
                    if (box.width == 0 || box.height == 0) {
                        statusIsError = true
                        status = "Layout not measured yet - try again."
                        return@Button
                    }
                    val plane = BitmapRgbPlane.fromBitmap(bm)
                    // Tap coords are in layout pixels; convert to plane-pixel
                    // coordinates via the layout-to-plane ratio. The Image is
                    // sized to the Box (aspect ratio == bitmap aspect ratio)
                    // so a single per-axis ratio suffices.
                    val toPlaneX = plane.width.toFloat() / box.width.toFloat()
                    val toPlaneY = plane.height.toFloat() / box.height.toFloat()
                    val scaledCorners = ChartCorners(
                        tl = Point2(pickedCorners[0].x * toPlaneX, pickedCorners[0].y * toPlaneY),
                        tr = Point2(pickedCorners[1].x * toPlaneX, pickedCorners[1].y * toPlaneY),
                        br = Point2(pickedCorners[2].x * toPlaneX, pickedCorners[2].y * toPlaneY),
                        bl = Point2(pickedCorners[3].x * toPlaneX, pickedCorners[3].y * toPlaneY),
                    )
                    runCatching {
                        val samples = CalibrationSampler.sample(plane, target, scaledCorners)
                        val accepted = samples.filter { !it.rejected && it.patchRef != null }
                        require(accepted.size >= 6) {
                            "Only ${accepted.size} of ${samples.size} patches passed variance check; reframe the chart."
                        }
                        val neutralSamples = accepted
                            .filter { it.patchRef!!.role == ReferenceTarget.PatchRole.Neutral }
                            .map { it.mean }
                        val wb = CalibrationMath.computeWbGains(
                            neutralPatches = neutralSamples.ifEmpty { accepted.map { it.mean } },
                        )
                        val measuredAfterWb = accepted.map { sample ->
                            floatArrayOf(
                                (sample.mean[0] * wb.r).coerceIn(0f, 1f),
                                (sample.mean[1] * wb.g).coerceIn(0f, 1f),
                                (sample.mean[2] * wb.b).coerceIn(0f, 1f),
                            )
                        }
                        val targetRgb = accepted.map { it.patchRef!!.referenceRgb }
                        val ccm = CalibrationMath.computeCcm(measuredAfterWb, targetRgb)
                        CalibrationProfile(
                            wbGains = wb,
                            ccm = ccm,
                            bias = CalibrationProfile.Bias.Zero,
                            mtf50Lpph = null,
                            illuminant = target.illuminant,
                            capturedAtMs = System.currentTimeMillis(),
                            cameraId = "host-calibrate",
                            targetId = target.id,
                        )
                    }.fold(
                        onSuccess = { p ->
                            profile = p
                            statusIsError = false
                            status = "Profile computed: WB=(${"%.3f".format(p.wbGains.r)}, ${"%.3f".format(p.wbGains.g)}, ${"%.3f".format(p.wbGains.b)})  illum=${p.illuminant}"
                        },
                        onFailure = { ex ->
                            profile = null
                            statusIsError = true
                            status = "Compute failed: ${ex.message}"
                        },
                    )
                },
            ) { Text("Compute") }

            Button(
                enabled = profile != null,
                onClick = {
                    val p = profile ?: return@Button
                    val saved = CalibrationProfileStorage.save(context, p)
                    if (saved == null) {
                        statusIsError = true
                        status = "Save failed: external storage unavailable."
                    } else {
                        savedPath = saved.absolutePath
                        statusIsError = false
                        status = "Saved profile to ${saved.name}"
                    }
                },
            ) { Text("Save profile") }
        }

        profile?.let { p ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.White.copy(alpha = 0.04f))
                    .padding(10.dp),
            ) {
                Text(
                    text = "WB gains  r=${"%.3f".format(p.wbGains.r)} g=${"%.3f".format(p.wbGains.g)} b=${"%.3f".format(p.wbGains.b)}\n" +
                        "CCM row0  ${"%+.3f".format(p.ccm.m00)} ${"%+.3f".format(p.ccm.m01)} ${"%+.3f".format(p.ccm.m02)}\n" +
                        "CCM row1  ${"%+.3f".format(p.ccm.m10)} ${"%+.3f".format(p.ccm.m11)} ${"%+.3f".format(p.ccm.m12)}\n" +
                        "CCM row2  ${"%+.3f".format(p.ccm.m20)} ${"%+.3f".format(p.ccm.m21)} ${"%+.3f".format(p.ccm.m22)}\n" +
                        "illum     ${p.illuminant}    target ${p.targetId}",
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = Color.White.copy(alpha = 0.95f),
                )
            }
        }
        savedPath?.let { path ->
            Text(
                text = "Saved -> $path",
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.65f),
                fontFamily = FontFamily.Monospace,
            )
        }
    }
}

@Composable
private fun TargetPickerRow(
    current: ReferenceTarget,
    onChange: (ReferenceTarget) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("Target:", style = MaterialTheme.typography.labelLarge)
        BundledReferenceTargets.All.forEach { entry ->
            val isSelected = entry.id == current.id
            OutlinedButton(
                onClick = { onChange(entry) },
                enabled = !isSelected,
            ) { Text(entry.displayName) }
        }
    }
}

/**
 * Decode an SAF [Uri] into a [Bitmap] using ARGB_8888. Returns null on any
 * IO or decode failure.
 */
private fun decodeBitmap(context: android.content.Context, uri: Uri): Bitmap? {
    return try {
        context.contentResolver.openInputStream(uri)?.use { stream ->
            val opts = BitmapFactory.Options().apply {
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            BitmapFactory.decodeStream(stream, null, opts)
        }
    } catch (ex: SecurityException) {
        null
    } catch (ex: java.io.IOException) {
        null
    }
}

