@file:Suppress("MagicNumber", "ComplexCondition")

package dev.pointandshoot

import android.app.Activity
import android.app.Presentation
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.hardware.display.DisplayManager
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Display
import android.view.Gravity
import android.view.PixelCopy
import android.view.SurfaceView
import android.view.TextureView
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import java.io.ByteArrayOutputStream
import java.lang.ref.WeakReference
import java.util.concurrent.atomic.AtomicBoolean

/**
 * HDMI / wireless-display clean feed plus MJPEG webcam-equivalent frames.
 * Never opens a second camera session — copies the live preview view.
 *
 * Robustness: last-good JPEG stays on PixelCopy miss, HDMI show retries after
 * display settle, audio prefers HDMI/eARC, MJPEG is independent of the cable.
 */
object PnsExternalOutput {
    const val TAG: String = "PNS.ExtOut"
    const val MJPEG_PORT: Int = 28770
    const val TYPE_HDMI: Int = 2
    const val TYPE_WIFI: Int = 3
    const val TYPE_OVERLAY: Int = 4
    private const val COPY_INTERVAL_MS: Long = 66L
    private const val WEBCAM_INTERVAL_MS: Long = 16L
    private const val MAX_EDGE: Int = 1920
    private const val WEBCAM_MAX_EDGE: Int = 3840
    private const val JPEG_QUALITY: Int = 78
    private const val WEBCAM_JPEG_QUALITY: Int = 82
    private const val DISPLAY_SETTLE_MS: Long = 450L
    private const val MIN_COPY_EDGE: Int = 8
    private const val MAX_SHOW_RETRIES: Int = 6

    @Volatile
    var latestJpeg: ByteArray? = null
        private set

    @Volatile
    var lastDisplayName: String? = null
        private set

    @Volatile
    var presentationActive: Boolean = false
        private set

    @Volatile
    var lastError: String? = null
        private set

    @Volatile
    var lastCopyWidth: Int = 0
        private set

    @Volatile
    var lastCopyHeight: Int = 0
        private set

    @Volatile
    var lastCopyMs: Long = 0
        private set

    @Volatile
    var achievedFps: Int = 0
        private set

    private var copyIntervalMs: Long = COPY_INTERVAL_MS
    private var copyMaxEdge: Int = MAX_EDGE
    private var lastCopyStartMs: Long = 0
    private var fpsWindowStartMs: Long = 0
    private var fpsWindowCount: Int = 0

    private val main: Handler by lazy { Handler(Looper.getMainLooper()) }
    private val copying = AtomicBoolean(false)
    private val jpegScratch = ByteArrayOutputStream(256 * 1024)
    private var displayManager: DisplayManager? = null
    private var presentation: CleanFeedPresentation? = null
    private var previewView: WeakReference<View>? = null
    private var hostActivity: WeakReference<Activity>? = null
    private var running = false
    private var copyFails: Int = 0
    private var showFails: Int = 0
    private val listener =
        object : DisplayManager.DisplayListener {
            override fun onDisplayAdded(displayId: Int) {
                Log.i(TAG, "displayAdded id=$displayId")
                main.removeCallbacks(settleTick)
                main.postDelayed(settleTick, DISPLAY_SETTLE_MS)
            }

            override fun onDisplayRemoved(displayId: Int) {
                Log.i(TAG, "displayRemoved id=$displayId")
                main.removeCallbacks(settleTick)
                main.post { reconcile() }
            }

            override fun onDisplayChanged(displayId: Int) {
                main.removeCallbacks(settleTick)
                main.postDelayed(settleTick, DISPLAY_SETTLE_MS)
            }
        }

    private val settleTick = Runnable { reconcile() }

    private val copyTick =
        object : Runnable {
            override fun run() {
                if (!running) return
                copyOnce()
                main.postDelayed(this, copyIntervalMs)
            }
        }

    fun statusLine(): String {
        val hdmi =
            when {
                presentationActive -> lastDisplayName ?: "active"
                lastDisplayName != null -> "lost $lastDisplayName"
                else -> "idle"
            }
        val mjpeg =
            if (PnsMjpegStreamServer.isListening()) {
                "mjpeg:${PnsMjpegStreamServer.boundPort}"
            } else {
                "mjpeg:off"
            }
        val err = lastError?.let { " err=$it" } ?: ""
        return "HDMI $hdmi · $mjpeg$err"
    }

    fun attach(activity: Activity, preview: View?) {
        hostActivity = WeakReference(activity)
        if (preview != null) previewView = WeakReference(preview)
        if (PnsProductPrefs.mjpegWebcamEnabled(activity)) {
            PnsMjpegStreamServer.start(activity.applicationContext)
        } else {
            PnsMjpegStreamServer.stop()
        }
        if (running) {
            reconcile()
            return
        }
        running = true
        val dm = activity.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
        displayManager = dm
        dm.registerDisplayListener(listener, main)
        reconcile()
        main.removeCallbacks(copyTick)
        main.post(copyTick)
        Log.i(TAG, "attached")
    }

    fun detach() {
        running = false
        main.removeCallbacks(copyTick)
        main.removeCallbacks(settleTick)
        runCatching { displayManager?.unregisterDisplayListener(listener) }
        displayManager = null
        dismissPresentation()
        PnsMjpegStreamServer.stop()
        latestJpeg = null
        lastDisplayName = null
        lastError = null
        presentationActive = false
        previewView = null
        hostActivity = null
        copyFails = 0
        showFails = 0
        Log.i(TAG, "detached")
    }

    fun pickExternalDisplay(dm: DisplayManager): Display? {
        val displays = dm.displays
        val ranked =
            displays
                .filter { it.displayId != Display.DEFAULT_DISPLAY && it.flags and Display.FLAG_PRESENTATION != 0 }
                .sortedWith(
                    compareByDescending<Display> { typeRank(it) }
                        .thenByDescending { it.mode.physicalWidth * it.mode.physicalHeight },
                )
        return ranked.firstOrNull()
    }

    fun typeRank(display: Display): Int {
        val type =
            if (Build.VERSION.SDK_INT >= 30) {
                runCatching { Display::class.java.getMethod("getType").invoke(display) as Int }.getOrDefault(0)
            } else {
                0
            }
        return typeRankFor(type, display.flags and Display.FLAG_PRESENTATION != 0)
    }

    fun typeRankFor(type: Int, presentationFlag: Boolean): Int =
        when (type) {
            TYPE_HDMI -> 30
            TYPE_WIFI -> 20
            TYPE_OVERLAY -> 10
            else -> if (presentationFlag) 5 else 0
        }

    private fun reconcile() {
        val activity = hostActivity?.get() ?: return
        if (activity.isFinishing || activity.isDestroyed) {
            dismissPresentation()
            return
        }
        val dm = displayManager ?: return
        val wantHdmi = PnsProductPrefs.hdmiOutEnabled(activity)
        val display = if (wantHdmi) pickExternalDisplay(dm) else null
        if (display == null) {
            if (presentation != null) {
                Log.i(TAG, "hdmi gone — dismiss")
            }
            dismissPresentation()
            if (!wantHdmi) lastDisplayName = null
            return
        }
        lastDisplayName = display.name
        val existing = presentation
        if (existing != null && existing.display.displayId == display.displayId && existing.isShowing) {
            presentationActive = true
            showFails = 0
            lastError = null
            routeAudio(activity)
            return
        }
        dismissPresentation()
        val next =
            runCatching { CleanFeedPresentation(activity, display) }.getOrElse { err ->
                lastError = err.message
                Log.w(TAG, "presentation create: ${err.message}")
                scheduleShowRetry()
                return
            }
        runCatching {
            next.show()
            presentation = next
            presentationActive = true
            showFails = 0
            lastError = null
            routeAudio(activity)
            Log.i(TAG, "hdmi show name=${display.name} id=${display.displayId}")
        }.onFailure { err ->
            lastError = err.message
            Log.w(TAG, "presentation show: ${err.message}")
            runCatching { next.dismiss() }
            presentationActive = false
            scheduleShowRetry()
        }
    }

    private fun scheduleShowRetry() {
        if (showFails >= MAX_SHOW_RETRIES) return
        showFails += 1
        val delay = DISPLAY_SETTLE_MS * showFails
        main.removeCallbacks(settleTick)
        main.postDelayed(settleTick, delay)
        Log.i(TAG, "hdmi retry $showFails/$MAX_SHOW_RETRIES in ${delay}ms")
    }

    private fun dismissPresentation() {
        presentationActive = false
        runCatching { presentation?.dismiss() }
        presentation = null
    }

    private fun routeAudio(context: Context) {
        val am = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
        val hdmi =
            am.getDevices(AudioManager.GET_DEVICES_OUTPUTS).firstOrNull { device ->
                device.type == AudioDeviceInfo.TYPE_HDMI ||
                    device.type == AudioDeviceInfo.TYPE_HDMI_ARC ||
                    (Build.VERSION.SDK_INT >= 31 && device.type == AudioDeviceInfo.TYPE_HDMI_EARC)
            } ?: return
        if (Build.VERSION.SDK_INT >= 31) {
            runCatching { am.setCommunicationDevice(hdmi) }
        }
    }

    private fun findCopySource(root: View): View {
        if (root is SurfaceView || root is TextureView) return root
        if (root is ViewGroup) {
            for (i in 0 until root.childCount) {
                val found = findCopySource(root.getChildAt(i))
                if (found is SurfaceView || found is TextureView) return found
            }
        }
        return root
    }

    private fun copyOnce() {
        if (!copying.compareAndSet(false, true)) return
        val raw = previewView?.get()
        val shown = presentation
        val wantMjpeg = PnsMjpegStreamServer.isListening()
        if (raw == null || (shown == null && !wantMjpeg)) {
            copying.set(false)
            return
        }
        val view = findCopySource(raw)
        if (view.width < MIN_COPY_EDGE || view.height < MIN_COPY_EDGE) {
            copying.set(false)
            return
        }
        copyMaxEdge =
            when {
                PnsUsbWebcam.active && PnsWebcamEncoder.isRunning -> 1280
                PnsUsbWebcam.active -> 1920
                else -> MAX_EDGE
            }
        val w = view.width.coerceAtMost(copyMaxEdge)
        val h = (view.height.toFloat() / view.width.toFloat() * w).toInt().coerceAtLeast(MIN_COPY_EDGE)
        lastCopyStartMs = android.os.SystemClock.elapsedRealtime()
        val bitmap =
            runCatching { Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888) }.getOrNull() ?: run {
                copying.set(false)
                return
            }
        when (view) {
            is SurfaceView -> pixelCopySurface(view, bitmap, shown)
            is TextureView -> copyFromTexture(view, bitmap, shown)
            else -> {
                lastError = "preview view is not SurfaceView/TextureView"
                finishCopy(bitmap, failed = true)
            }
        }
    }

    private fun pixelCopySurface(surface: SurfaceView, bitmap: Bitmap, shown: CleanFeedPresentation?) {
        if (Build.VERSION.SDK_INT < 26) {
            finishCopy(bitmap, failed = true)
            return
        }
        val surfaceValid = runCatching { surface.holder.surface?.isValid == true }.getOrDefault(false)
        if (!surfaceValid) {
            noteCopyFail("surface invalid")
            finishCopy(bitmap, failed = true)
            return
        }
        try {
            PixelCopy.request(
                surface,
                bitmap,
                { result -> onCopyResult(result == PixelCopy.SUCCESS, bitmap, shown) },
                main,
            )
        } catch (err: RuntimeException) {
            lastError = err.message
            Log.w(TAG, "pixelCopy: ${err.message}")
            finishCopy(bitmap, failed = true)
        }
    }

    private fun copyFromTexture(view: TextureView, bitmap: Bitmap, shown: CleanFeedPresentation?) {
        if (!view.isAvailable) {
            noteCopyFail("texture unavailable")
            finishCopy(bitmap, failed = true)
            return
        }
        val ok = view.getBitmap(bitmap) != null
        onCopyResult(ok, bitmap, shown)
    }

    private fun onCopyResult(ok: Boolean, bitmap: Bitmap, shown: CleanFeedPresentation?) {
        if (ok) {
            copyFails = 0
            lastError = null
            lastCopyWidth = bitmap.width
            lastCopyHeight = bitmap.height
            lastCopyMs = (android.os.SystemClock.elapsedRealtime() - lastCopyStartMs).coerceAtLeast(1)
            noteFps()
            adaptWebcamCadence()
            shown?.showFrame(bitmap)
            latestJpeg = compressJpeg(bitmap)
            if (shown == null) {
                runCatching { bitmap.recycle() }
            }
        } else {
            noteCopyFail("pixelCopy miss")
            runCatching { bitmap.recycle() }
        }
        copying.set(false)
    }

    private fun compressJpeg(bitmap: Bitmap): ByteArray? =
        runCatching {
            synchronized(jpegScratch) {
                jpegScratch.reset()
                val q = if (PnsUsbWebcam.active) WEBCAM_JPEG_QUALITY else JPEG_QUALITY
                bitmap.compress(Bitmap.CompressFormat.JPEG, q, jpegScratch)
                jpegScratch.toByteArray()
            }
        }.getOrNull()

    private fun noteCopyFail(reason: String) {
        copyFails += 1
        lastError = reason
        if (copyFails == 1 || copyFails % 30 == 0) {
            Log.w(TAG, "copy fail n=$copyFails $reason (keeping last JPEG)")
        }
    }

    private fun noteFps() {
        val now = android.os.SystemClock.elapsedRealtime()
        if (fpsWindowStartMs == 0L) fpsWindowStartMs = now
        fpsWindowCount += 1
        val elapsed = now - fpsWindowStartMs
        if (elapsed >= 1_000L) {
            achievedFps = ((fpsWindowCount * 1000L) / elapsed).toInt()
            fpsWindowStartMs = now
            fpsWindowCount = 0
            if (PnsUsbWebcam.active) {
                Log.i(TAG, "webcam copy ${lastCopyWidth}x${lastCopyHeight} ${lastCopyMs}ms ~${achievedFps}fps")
            }
        }
    }

    private fun adaptWebcamCadence() {
        if (!PnsUsbWebcam.active) {
            copyIntervalMs = COPY_INTERVAL_MS
            return
        }
        copyIntervalMs =
            when {
                lastCopyMs > 80L -> 66L
                lastCopyMs > 40L -> 33L
                else -> WEBCAM_INTERVAL_MS
            }
    }

    private fun finishCopy(bitmap: Bitmap, failed: Boolean) {
        if (failed) runCatching { bitmap.recycle() }
        copying.set(false)
    }

    private class CleanFeedPresentation(
        outer: Context,
        display: Display,
    ) : Presentation(outer, display) {
        private var image: ImageView? = null
        private var waiting: TextView? = null
        private var lastBitmap: Bitmap? = null

        override fun onCreate(savedInstanceState: Bundle?) {
            super.onCreate(savedInstanceState)
            val root = FrameLayout(context)
            root.setBackgroundColor(Color.BLACK)
            val iv =
                ImageView(context).apply {
                    scaleType = ImageView.ScaleType.FIT_CENTER
                    layoutParams =
                        FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            Gravity.CENTER,
                        )
                    contentDescription = "HDMI program feed"
                }
            val wait =
                TextView(context).apply {
                    text = "Point & Shoot — waiting for preview"
                    setTextColor(Color.WHITE)
                    textSize = 18f
                    gravity = Gravity.CENTER
                    layoutParams =
                        FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            Gravity.CENTER,
                        )
                }
            root.addView(iv)
            root.addView(wait)
            image = iv
            waiting = wait
            setContentView(root)
        }

        fun showFrame(bitmap: Bitmap) {
            val prev = lastBitmap
            lastBitmap = bitmap
            image?.setImageBitmap(bitmap)
            waiting?.visibility = View.GONE
            if (prev != null && prev !== bitmap) {
                runCatching { prev.recycle() }
            }
        }

        override fun onStop() {
            super.onStop()
            val prev = lastBitmap
            lastBitmap = null
            runCatching { prev?.recycle() }
        }
    }
}
