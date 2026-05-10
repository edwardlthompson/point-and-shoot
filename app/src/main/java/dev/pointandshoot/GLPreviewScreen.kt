package dev.pointandshoot

import android.graphics.Bitmap
import android.opengl.GLSurfaceView
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView

/**
 * Live GLES preview screen that renders [TestPattern] through the bundled
 * [LutShaderProgram], driven by the user's [HudSettings] LUT selection.
 *
 * This is the BUILD_PLAN \u00a77 "Apply path -> Live preview / video"
 * screen. It exists ahead of the Phase 1 Camera2 capture engine so we can
 * (a) validate the entire shader pipeline on real hardware and (b) prove
 * the [HudSettings.stillsLut] / [HudSettings.videoLut] selections actually
 * drive the GLES uniforms. When the Camera2 engine arrives, the source
 * bitmap is replaced by a SurfaceTexture-backed `samplerExternalOES` and
 * the rest of the pipeline is unchanged.
 *
 * The screen is reachable from the probe home ("Live preview LUT" button)
 * or via `--es pns_screen glpreview` for ADB-driven validation runs.
 */
@Composable
fun GLPreviewScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val state = rememberHudSettings()

    val renderer = remember {
        LutPreviewRenderer(
            assetLoader = { path ->
                context.assets.open(path).bufferedReader().use { it.readText() }
            },
        )
    }

    val testBitmap: Bitmap = remember {
        Bitmap.createBitmap(TestPattern.WIDTH, TestPattern.HEIGHT, Bitmap.Config.ARGB_8888).apply {
            val pixels = TestPattern.generateArgb(TestPattern.WIDTH, TestPattern.HEIGHT)
            setPixels(pixels, 0, TestPattern.WIDTH, 0, 0, TestPattern.WIDTH, TestPattern.HEIGHT)
        }
    }

    val activeLut = state.current.stillsLut()

    LaunchedEffect(Unit) {
        Log.i(
            "PNS.AdbValidation",
            "glpreview screen compose active lut=${state.current.stillsLut().name}",
        )
    }

    LaunchedEffect(activeLut) {
        renderer.setSourceBitmap(testBitmap)
        renderer.setLut(activeLut.load())
    }

    DisposableEffect(Unit) {
        onDispose {
            renderer.setLut(null)
            if (!testBitmap.isRecycled) testBitmap.recycle()
        }
    }

    val insets = rememberSystemInsetsDp()
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .padding(insets.asPaddingValues(extra = 12.dp)),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedButton(onClick = onBack) { Text("Back") }
            Text(
                text = "GLES preview LUT (test pattern)",
                color = Color.White,
                style = MaterialTheme.typography.titleMedium,
            )
        }

        Text(
            text = "Active LUT: ${activeLut.displayName}  -  ${activeLut.spdx}",
            style = MaterialTheme.typography.bodySmall,
            color = Color.White.copy(alpha = 0.75f),
        )

        LutChipRow(state = state)

        Spacer(Modifier.height(4.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(420.dp)
                .background(Color.Black),
            contentAlignment = Alignment.Center,
        ) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    GLSurfaceView(ctx).apply {
                        setEGLContextClientVersion(3)
                        setRenderer(renderer)
                        renderMode = GLSurfaceView.RENDERMODE_WHEN_DIRTY
                        renderer.setSourceBitmap(testBitmap)
                        renderer.setLut(activeLut.load())
                        requestRender()
                    }
                },
                update = { glView ->
                    renderer.setLut(activeLut.load())
                    glView.requestRender()
                },
            )
        }

        Text(
            text = "8 color bars / 11-step wedge / smooth ramp.\n" +
                "Pick a different LUT above to see the apply path live.",
            style = MaterialTheme.typography.bodySmall,
            color = Color.White.copy(alpha = 0.55f),
        )
    }
}
