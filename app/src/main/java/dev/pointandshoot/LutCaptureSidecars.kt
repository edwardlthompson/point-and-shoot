package dev.pointandshoot

import android.content.Context
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import java.time.Instant
import java.time.format.DateTimeFormatter

/**
 * Writes LUT reproducibility sidecars next to tonal still/video captures (BUILD_PLAN §7).
 * Bundled catalog LUTs use `.lutref.txt` via [CaptureStorage.openLutSidecarOutput].
 */
object LutCaptureSidecars {

    private const val TAG = "PNS.LutCapture"

    /**
     * Writes a **bundled** LUT sidecar for [catalog] ≠ [LutCatalog.None], pairing with
     * [captureDisplayName] (the primary file's MediaStore display name).
     *
     * @return `true` if a sidecar was written; `false` when [catalog] is **None**, API below 29,
     *   or MediaStore / IO failed (failures are logged, never thrown).
     */
    fun writeBundledLutSidecarIfNeeded(
        context: Context,
        profile: ImagingProfile,
        captureDisplayName: String,
        catalog: LutCatalog,
        captureKind: LutSidecar.CaptureKind,
        lutGridSize: Int = BuiltInLuts.DEFAULT_SIZE,
    ): Boolean {
        if (catalog == LutCatalog.None) return false
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return false
        val sha = LutSidecarWriter.sha256ForLut(catalog.load(lutGridSize))
        val utc = DateTimeFormatter.ISO_INSTANT.format(Instant.now())
        val ref =
            LutSidecar.bundledRefFor(
                catalog,
                captureDisplayName,
                captureKind,
                utc,
                lutGridSize,
                sha,
            )
        val text = LutSidecar.encode(ref)
        return runCatching {
            val handle =
                CaptureStorage.openLutSidecarOutput(
                    context.applicationContext,
                    profile,
                    captureDisplayName,
                    isBundled = true,
                )
            try {
                handle.output.use { it.write(text.toByteArray(Charsets.UTF_8)) }
                handle.close()
                true
            } catch (t: Throwable) {
                runCatching { handle.discard() }
                throw t
            }
        }.getOrElse { t ->
            Log.w(TAG, "bundled LUT sidecar write failed for $captureDisplayName", t)
            false
        }
    }
}
