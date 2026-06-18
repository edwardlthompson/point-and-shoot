package dev.pointandshoot.preview.mock

import android.graphics.Bitmap
import android.opengl.GLSurfaceView
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import dev.pointandshoot.Lut3D
import dev.pointandshoot.LutPreviewRenderer
import dev.pointandshoot.TestPattern

private const val GLES_CLIENT_VERSION = 3

/**
 * GLES viewport that renders [TestPattern] through [LutPreviewRenderer].
 * Used by [UnifiedMockPreviewScreen] — no Camera2 session, no resume-policy fork.
 */
@Suppress("FunctionNaming")
@Composable
internal fun MockPreviewGlViewport(
    activeLut: Lut3D?,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current

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

    DisposableEffect(Unit) {
        onDispose {
            renderer.setLut(null)
            if (!testBitmap.isRecycled) testBitmap.recycle()
        }
    }

    AndroidView(
        modifier = modifier.fillMaxSize(),
        factory = { ctx ->
            GLSurfaceView(ctx).apply {
                setEGLContextClientVersion(GLES_CLIENT_VERSION)
                setRenderer(renderer)
                renderMode = GLSurfaceView.RENDERMODE_WHEN_DIRTY
                renderer.setSourceBitmap(testBitmap)
                renderer.setLut(activeLut)
                requestRender()
            }
        },
        update = { glView ->
            renderer.setLut(activeLut)
            glView.requestRender()
        },
    )
}
