package dev.pointandshoot

import android.content.Context
import android.graphics.ImageFormat
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.hardware.camera2.params.StreamConfigurationMap
import android.util.Range
import android.util.Size
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

@Composable
fun DodgeMappingScreen(
    onBackToProbe: () -> Unit,
) {
    val context = LocalContext.current
    var rows by remember { mutableStateOf(listOf<String>()) }

    LaunchedEffect(Unit) {
        rows = buildDodgeMappingRows(context)
    }

    val insets = rememberSystemInsetsDp()
    DodgeMappingContent(
        padding = insets.asPaddingValues(extra = 16.dp),
        rows = rows,
        onBackToProbe = onBackToProbe,
        onRefresh = { rows = buildDodgeMappingRows(context) },
    )
}

@Composable
private fun DodgeMappingContent(
    padding: PaddingValues,
    rows: List<String>,
    onBackToProbe: () -> Unit,
    onRefresh: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedButton(onClick = onBackToProbe) { Text("Back") }
            Button(onClick = onRefresh) { Text("Refresh") }
        }
        Text("Dodge mapping")
        Text("Working hypothesis (based on focal clusters + physical ids)")
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(rows) { r -> Text(r) }
        }
    }
}

private data class CameraInfo(
    val id: String,
    val facing: Int?,
    val focalMm: Float?,
    val physicalIds: List<String>,
    val maxRawOutputs: Int?,
    val maxHfr: Int?, // max upper FPS from constrained high speed, best-known
)

private enum class CameraRole {
    LOGICAL_BACK,
    MAIN_WIDE,
    ULTRAWIDE,
    TELE,
    FRONT,
    UNKNOWN,
}

private fun buildDodgeMappingRows(context: Context): List<String> {
    val cm = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
    val ids = runCatching { cm.cameraIdList.toList() }.getOrDefault(emptyList())
    if (ids.isEmpty()) return listOf("No cameras enumerated.")

    val infos = ids.mapNotNull { id ->
        val cc = runCatching { cm.getCameraCharacteristics(id) }.getOrNull() ?: return@mapNotNull null
        val facing = cc.get(CameraCharacteristics.LENS_FACING)
        val focal = cc.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)?.firstOrNull()
        val physical = runCatching { cc.physicalCameraIds.toList() }.getOrDefault(emptyList())
        val maxRaw = cc.get(CameraCharacteristics.REQUEST_MAX_NUM_OUTPUT_RAW)
        val maxHfr = extractMaxConstrainedHighSpeedFps(cc.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP))
        CameraInfo(
            id = id,
            facing = facing,
            focalMm = focal,
            physicalIds = physical.sorted(),
            maxRawOutputs = maxRaw,
            maxHfr = maxHfr,
        )
    }

    val backPhysical = infos.filter { it.facing == CameraCharacteristics.LENS_FACING_BACK && it.physicalIds.isEmpty() }
    val uw = backPhysical.minByOrNull { it.focalMm ?: Float.POSITIVE_INFINITY }?.id
    val tele = backPhysical.maxByOrNull { it.focalMm ?: Float.NEGATIVE_INFINITY }?.id
    val main = backPhysical
        .filter { it.id != uw && it.id != tele }
        .minByOrNull { kotlin.math.abs((it.focalMm ?: 0f) - (backPhysical.mapNotNull { x -> x.focalMm }.sorted().getOrNull(1) ?: 0f)) }
        ?.id
        ?: backPhysical.firstOrNull { it.id != uw && it.id != tele }?.id

    fun roleFor(i: CameraInfo): CameraRole {
        if (i.facing == CameraCharacteristics.LENS_FACING_FRONT) return CameraRole.FRONT
        if (i.facing == CameraCharacteristics.LENS_FACING_BACK && i.physicalIds.isNotEmpty()) return CameraRole.LOGICAL_BACK
        if (i.id == main) return CameraRole.MAIN_WIDE
        if (i.id == uw) return CameraRole.ULTRAWIDE
        if (i.id == tele) return CameraRole.TELE
        return CameraRole.UNKNOWN
    }

    return infos
        .sortedBy { it.id.toIntOrNull() ?: Int.MAX_VALUE }
        .map { i ->
            val role = roleFor(i)
            val facing = when (i.facing) {
                CameraCharacteristics.LENS_FACING_BACK -> "BACK"
                CameraCharacteristics.LENS_FACING_FRONT -> "FRONT"
                CameraCharacteristics.LENS_FACING_EXTERNAL -> "EXTERNAL"
                else -> "UNKNOWN"
            }

            val hfrTier = when {
                (i.maxHfr ?: 0) >= 480 -> "HFR>=480"
                (i.maxHfr ?: 0) >= 240 -> "HFR>=240"
                (i.maxHfr ?: 0) >= 120 -> "HFR>=120"
                else -> "HFR<120/none"
            }

            val raw = if ((i.maxRawOutputs ?: 0) > 0) "RAW=yes(${i.maxRawOutputs})" else "RAW=no"
            val phys = if (i.physicalIds.isEmpty()) "" else " physicalIds=${i.physicalIds}"
            "cameraId=${i.id} role=$role facing=$facing focalMm=${i.focalMm ?: "?"} $raw $hfrTier$phys"
        }
}

private fun extractMaxConstrainedHighSpeedFps(map: StreamConfigurationMap?): Int? {
    if (map == null) return null
    val sizes: List<Size> = runCatching { map.highSpeedVideoSizes?.toList() }.getOrNull().orEmpty()
    if (sizes.isEmpty()) return null

    var best: Int? = null
    for (s in sizes) {
        val ranges: Array<Range<Int>> =
            runCatching { map.getHighSpeedVideoFpsRangesFor(s) }.getOrNull() ?: continue
        for (r in ranges) {
            val u = r.upper
            if (best == null || u > best) best = u
        }
    }
    return best
}

