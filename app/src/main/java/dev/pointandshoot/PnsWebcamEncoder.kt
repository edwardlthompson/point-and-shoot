@file:Suppress("MagicNumber", "TooGenericExceptionCaught", "NestedBlockDepth", "LoopWithTooManyJumpStatements")

package dev.pointandshoot

import android.content.Context
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.util.Log
import android.view.Surface
import java.io.OutputStream
import java.lang.ref.WeakReference
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

/**
 * Camera2 Surface → H.264, no [android.media.MediaMuxer] / no DCIM write.
 * HTTP clients read Annex-B from [writeAnnexB].
 */
object PnsWebcamEncoder {
    const val TAG: String = "PNS.WebcamEnc"

    @Volatile
    var inputSurface: Surface? = null
        private set

    @Volatile
    var tier: PnsWebcamLadder.Tier? = null
        private set

    @Volatile
    var achievedFps: Int = 0
        private set

    @Volatile
    var lastError: String? = null
        private set

    @Volatile
    var floorIndex: Int = 0
        private set

    @Volatile
    var generation: Int = 0
        private set

    val isRunning: Boolean
        get() = running.get() && inputSurface?.isValid == true

    val isUhd60: Boolean
        get() = tier?.isUhd60 == true

    var onNeedSessionRebuild: (() -> Unit)? = null

    private val running = AtomicBoolean(false)
    private val startCode = byteArrayOf(0, 0, 0, 1)
    private val nals = ArrayBlockingQueue<ByteArray>(6)
    @Volatile private var header: ByteArray = ByteArray(0)
    private var codec: MediaCodec? = null
    private var worker: Thread? = null
    private var fpsWindowStart: Long = 0
    private var fpsWindowCount: Int = 0
    private var lastThermalCheckMs: Long = 0
    private var appContext: WeakReference<Context>? = null
    private val main: Handler by lazy { Handler(Looper.getMainLooper()) }

    fun prepare(context: Context, allowUhd: Boolean = true): Boolean {
        stopInternal(keepFloor = true)
        appContext = WeakReference(context.applicationContext)
        val pm = context.applicationContext.getSystemService(Context.POWER_SERVICE) as? PowerManager
        val thermal = pm?.currentThermalStatusCompat() ?: ApiLevelGuards.THERMAL_STATUS_NONE
        var idx = floorIndex
        while (idx <= PnsWebcamLadder.Tiers.lastIndex) {
            val pick = PnsWebcamLadder.pick(thermal, idx, allowUhd)
            idx = PnsWebcamLadder.Tiers.indexOf(pick)
            if (tryConfigure(pick)) {
                floorIndex = idx
                Log.i(TAG, "prepared ${pick.name} ${pick.width}x${pick.height}@${pick.fps} thermal=$thermal floor=$idx")
                return true
            }
            val next = PnsWebcamLadder.nextFloor(idx) ?: break
            idx = next
            floorIndex = next
        }
        lastError = lastError ?: "encoder configure failed"
        Log.w(TAG, "prepare failed: $lastError")
        return false
    }

    fun startDrain() {
        if (!running.get() || worker?.isAlive == true) return
        worker =
            thread(name = TAG, isDaemon = true) {
                drainLoop()
            }
    }

    fun dropTier(reason: String): Boolean {
        val next = PnsWebcamLadder.nextFloor(floorIndex) ?: return false
        Log.w(TAG, "dropTier $floorIndex→$next ($reason)")
        floorIndex = next
        lastError = reason
        return true
    }

    fun stop() {
        onNeedSessionRebuild = null
        stopInternal(keepFloor = false)
    }

    fun writeAnnexB(out: OutputStream) {
        val head = header
        if (head.isNotEmpty()) {
            out.write(head)
            out.flush()
        }
        var idle = 0
        while (running.get() && idle < 800) {
            val nal = nals.poll(50, TimeUnit.MILLISECONDS)
            if (nal == null) {
                idle++
                continue
            }
            idle = 0
            out.write(nal)
            out.flush()
        }
    }

    private fun tryConfigure(pick: PnsWebcamLadder.Tier): Boolean {
        return try {
            val format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, pick.width, pick.height)
            format.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
            format.setInteger(MediaFormat.KEY_BIT_RATE, pick.bitrate)
            format.setInteger(MediaFormat.KEY_FRAME_RATE, pick.fps)
            format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
            var enc = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
            try {
                format.setInteger(MediaFormat.KEY_PROFILE, MediaCodecInfo.CodecProfileLevel.AVCProfileHigh)
                enc.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            } catch (_: Exception) {
                runCatching { enc.release() }
                format.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
                enc = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
                enc.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            }
            val surface = enc.createInputSurface()
            enc.start()
            codec = enc
            inputSurface = surface
            tier = pick
            running.set(true)
            lastError = null
            generation += 1
            true
        } catch (err: Exception) {
            lastError = err.message
            Log.w(TAG, "configure ${pick.name}: ${err.message}")
            releaseCodec()
            false
        }
    }

    private fun drainLoop() {
        val buf = MediaCodec.BufferInfo()
        fpsWindowStart = SystemClock.elapsedRealtime()
        fpsWindowCount = 0
        while (running.get()) {
            maybeThermalStep()
            val enc = codec ?: break
            val ix =
                try {
                    enc.dequeueOutputBuffer(buf, 40_000L)
                } catch (err: Exception) {
                    lastError = err.message
                    Log.w(TAG, "dequeue: ${err.message}")
                    break
                }
            when {
                ix == MediaCodec.INFO_TRY_AGAIN_LATER -> continue
                ix == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    header = annexBFromFormat(enc.outputFormat)
                    Log.i(TAG, "outputFormat headerBytes=${header.size}")
                }
                ix >= 0 -> {
                    try {
                        val data = enc.getOutputBuffer(ix)
                        if (data != null && buf.size > 0) {
                            data.position(buf.offset)
                            data.limit(buf.offset + buf.size)
                            val raw = ByteArray(buf.size)
                            data.get(raw)
                            offerNal(toAnnexB(raw))
                            noteFps()
                        }
                    } catch (err: Exception) {
                        Log.w(TAG, "output: ${err.message}")
                    } finally {
                        runCatching { enc.releaseOutputBuffer(ix, false) }
                    }
                }
            }
        }
    }

    private fun maybeThermalStep() {
        val now = SystemClock.elapsedRealtime()
        if (now - lastThermalCheckMs < 2_000L) return
        lastThermalCheckMs = now
        val ctx = appContext?.get() ?: return
        val pm = ctx.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return
        val thermal = pm.currentThermalStatusCompat()
        val want = PnsWebcamLadder.indexForThermal(thermal)
        if (want > floorIndex) {
            main.post {
                if (dropTier("thermal=$thermal")) {
                    val app = appContext?.get()
                    if (app != null && prepare(app)) startDrain()
                    onNeedSessionRebuild?.invoke()
                }
            }
        }
    }

    private fun offerNal(nal: ByteArray) {
        if (!nals.offer(nal)) {
            nals.poll()
            nals.offer(nal)
        }
    }

    private fun noteFps() {
        fpsWindowCount++
        val now = SystemClock.elapsedRealtime()
        val elapsed = now - fpsWindowStart
        if (elapsed >= 1_000L) {
            achievedFps = ((fpsWindowCount * 1000L) / elapsed).toInt()
            fpsWindowStart = now
            fpsWindowCount = 0
            val t = tier
            Log.i(
                TAG,
                "encode ${t?.width ?: 0}x${t?.height ?: 0} ~${achievedFps}fps tier=${t?.name}",
            )
        }
    }

    private fun isAnnexB(raw: ByteArray): Boolean {
        if (raw.size < 4) return false
        if (raw[0] != 0.toByte() || raw[1] != 0.toByte()) return false
        if (raw[2] == 1.toByte()) return true
        return raw[2] == 0.toByte() && raw[3] == 1.toByte()
    }

    private fun toAnnexB(raw: ByteArray): ByteArray {
        if (isAnnexB(raw)) {
            return raw
        }
        val out = ByteArray(startCode.size + raw.size)
        System.arraycopy(startCode, 0, out, 0, startCode.size)
        System.arraycopy(raw, 0, out, startCode.size, raw.size)
        return out
    }

    private fun annexBFromFormat(format: MediaFormat): ByteArray {
        val chunks = ArrayList<ByteArray>()
        for (key in listOf("csd-0", "csd-1")) {
            val csd = format.getByteBuffer(key) ?: continue
            val copy = ByteArray(csd.remaining())
            csd.duplicate().get(copy)
            chunks += toAnnexB(copy)
        }
        val n = chunks.sumOf { it.size }
        val out = ByteArray(n)
        var o = 0
        for (c in chunks) {
            System.arraycopy(c, 0, out, o, c.size)
            o += c.size
        }
        return out
    }

    private fun stopInternal(keepFloor: Boolean) {
        running.set(false)
        worker?.join(400)
        worker = null
        releaseCodec()
        nals.clear()
        header = ByteArray(0)
        achievedFps = 0
        tier = null
        if (!keepFloor) floorIndex = 0
    }

    private fun releaseCodec() {
        runCatching { inputSurface?.release() }
        inputSurface = null
        runCatching { codec?.stop() }
        runCatching { codec?.release() }
        codec = null
    }
}
