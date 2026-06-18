package dev.pointandshoot

import dev.pointandshoot.preview.createCaptureSessionHighSpeedOutputs
import dev.pointandshoot.preview.createCaptureSessionRegularOutputs
import dev.pointandshoot.preview.outputConfigurationsWithOptionalStreamUseCases

import android.graphics.SurfaceTexture
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.params.StreamConfigurationMap
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.media.MediaFormat
import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import android.util.Range
import android.util.Size
import android.view.Surface
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

internal data class EncoderProbeResult(
    val ok: Boolean,
    val measuredFps: Double,
    val note: String,
    val size: Size?,
    val fpsRange: Range<Int>?,
    val sessionKind: String,
) {
    fun toJson(): JSONObject =
        JSONObject().apply {
            put("ok", ok)
            put("measuredFps", measuredFps)
            put("note", note)
            put("sessionKind", sessionKind)
            put(
                "size",
                if (size == null) JSONObject.NULL else JSONObject().apply {
                    put("w", size.width)
                    put("h", size.height)
                },
            )
            put(
                "fpsRange",
                if (fpsRange == null) JSONObject.NULL else JSONObject().apply {
                    put("lower", fpsRange.lower)
                    put("upper", fpsRange.upper)
                },
            )
        }
}

/** Every (size, fpsRange) pair advertised for constrained high-speed video. */
internal fun enumerateAllHighSpeedCombos(map: StreamConfigurationMap?): List<Pair<Size, Range<Int>>> {
    if (map == null) return emptyList()
    val sizes = runCatching { map.highSpeedVideoSizes?.toList() }.getOrNull().orEmpty()
    if (sizes.isEmpty()) return emptyList()
    val out = mutableListOf<Pair<Size, Range<Int>>>()
    val seen = HashSet<String>()
    for (s in sizes) {
        val ranges = runCatching { map.getHighSpeedVideoFpsRangesFor(s) }.getOrNull() ?: continue
        for (r in ranges) {
            val key = "${s.width}x${s.height}_${r.lower}-${r.upper}"
            if (seen.add(key)) out += s to r
        }
    }
    return out
}

/** HFR probe subset: only combos whose range includes [desiredFps, desiredFps]. */
internal fun enumerateHighSpeedTargets(
    map: StreamConfigurationMap?,
    desiredFps: Int,
): List<Pair<Size, Range<Int>>> {
    if (map == null) return emptyList()
    val sizes = runCatching { map.highSpeedVideoSizes?.toList() }.getOrNull().orEmpty()
    if (sizes.isEmpty()) return emptyList()
    fun preferredOrder(): List<Size> = when {
        desiredFps >= 480 -> listOf(Size(1280, 720), Size(720, 480), Size(640, 480), Size(1920, 1080))
        desiredFps >= 240 -> listOf(Size(1920, 1080), Size(1280, 720), Size(720, 480), Size(640, 480))
        else -> listOf(Size(1920, 1080), Size(1280, 720), Size(720, 480), Size(640, 480))
    }
    val orderedSizes = (preferredOrder().filter { p -> sizes.any { it == p } } + sizes).distinct()
    val out = mutableListOf<Pair<Size, Range<Int>>>()
    for (s in orderedSizes) {
        val ranges = runCatching { map.getHighSpeedVideoFpsRangesFor(s) }.getOrNull() ?: continue
        val exact = ranges.firstOrNull { it.lower == desiredFps && it.upper == desiredFps }
        if (exact != null) {
            out += (s to exact)
            continue
        }
        val capped = ranges.firstOrNull { it.upper == desiredFps }
        if (capped != null) out += (s to capped)
    }
    return out
}

/**
 * Non-HFR preview/video combos: SurfaceTexture output size × AE target FPS range
 * where the range upper does not exceed the max FPS implied by min frame duration.
 */
internal fun enumerateRegularVideoCombos(
    map: StreamConfigurationMap?,
    aeRanges: Array<Range<Int>>?,
): List<Pair<Size, Range<Int>>> {
    if (map == null || aeRanges.isNullOrEmpty()) return emptyList()
    val sizes = runCatching { map.getOutputSizes(SurfaceTexture::class.java)?.toList() }.getOrNull().orEmpty()
    if (sizes.isEmpty()) return emptyList()
    val out = mutableListOf<Pair<Size, Range<Int>>>()
    val seen = HashSet<String>()
    for (s in sizes) {
        val minNs = runCatching { map.getOutputMinFrameDuration(SurfaceTexture::class.java, s) }.getOrNull() ?: 0L
        val maxFps = if (minNs > 0) (1_000_000_000.0 / minNs.toDouble()) else 240.0
        for (r in aeRanges) {
            if (r.upper.toDouble() > maxFps * 1.02 + 1.0) continue
            val key = "${s.width}x${s.height}_${r.lower}-${r.upper}"
            if (seen.add(key)) out += s to r
        }
    }
    return out
}

internal fun supportsSurfaceEncoding(mime: String): Boolean {
    val list = MediaCodecList(MediaCodecList.REGULAR_CODECS)
    val infos = list.codecInfos.filter { it.isEncoder }
    for (info in infos) {
        if (!info.supportedTypes.any { it.equals(mime, ignoreCase = true) }) continue
        val caps = runCatching { info.getCapabilitiesForType(mime) }.getOrNull() ?: continue
        if (caps.colorFormats.any { it == MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface }) return true
    }
    return false
}

/**
 * Constrained high-speed capture session + repeating high-speed burst (existing path).
 */
internal fun runSingleHfrEncoderProbe(
    cm: CameraManager,
    camId: String,
    durationMs: Long,
    mime: String,
    size: Size,
    fpsRange: Range<Int>,
): EncoderProbeResult {
    val desiredFps = fpsRange.upper
    val ht = HandlerThread("PNS.HfrEnc")
    ht.start()
    val h = Handler(ht.looper)
    var codec: MediaCodec? = null
    var inputSurface: Surface? = null
    var camera: CameraDevice? = null
    var session: CameraCaptureSession? = null
    return try {
        codec = MediaCodec.createEncoderByType(mime)
        val format = MediaFormat.createVideoFormat(mime, size.width, size.height).apply {
            setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
            setInteger(MediaFormat.KEY_FRAME_RATE, desiredFps)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
            setInteger(MediaFormat.KEY_BIT_RATE, 10_000_000)
            runCatching { setInteger(MediaFormat.KEY_PRIORITY, 0) }
            runCatching { setInteger(MediaFormat.KEY_OPERATING_RATE, desiredFps) }
            runCatching { setInteger(MediaFormat.KEY_MAX_B_FRAMES, 0) }
        }
        codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        inputSurface = codec.createInputSurface()
        codec.start()
        val openLatch = CountDownLatch(1)
        cm.openCamera(
            camId,
            object : CameraDevice.StateCallback() {
                override fun onOpened(cd: CameraDevice) {
                    camera = cd
                    openLatch.countDown()
                }
                override fun onDisconnected(cd: CameraDevice) {
                    runCatching { cd.close() }
                    openLatch.countDown()
                }
                override fun onError(cd: CameraDevice, error: Int) {
                    runCatching { cd.close() }
                    openLatch.countDown()
                }
            },
            h,
        )
        if (!openLatch.await(4, TimeUnit.SECONDS) || camera == null) {
            return EncoderProbeResult(false, 0.0, "mime=$mime open_failed", size, fpsRange, "hfr")
        }
        val sessLatch = CountDownLatch(1)
        camera!!.createCaptureSessionHighSpeedOutputs(
            listOf(inputSurface!!),
            h,
            object : CameraCaptureSession.StateCallback() {
                override fun onConfigured(sess: CameraCaptureSession) {
                    session = sess
                    sessLatch.countDown()
                }
                override fun onConfigureFailed(sess: CameraCaptureSession) {
                    sessLatch.countDown()
                }
            },
        )
        if (!sessLatch.await(4, TimeUnit.SECONDS) || session == null) {
            return EncoderProbeResult(false, 0.0, "mime=$mime session_failed", size, fpsRange, "hfr")
        }
        val hsSession = session as? android.hardware.camera2.CameraConstrainedHighSpeedCaptureSession
            ?: return EncoderProbeResult(false, 0.0, "mime=$mime not_high_speed_session", size, fpsRange, "hfr")
        val req = camera!!.createCaptureRequest(CameraDevice.TEMPLATE_RECORD).apply {
            addTarget(inputSurface!!)
            set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, fpsRange)
        }.build()
        val burst = hsSession.createHighSpeedRequestList(req)
        hsSession.setRepeatingBurst(burst, null, h)
        val startNs = SystemClock.elapsedRealtimeNanos()
        val endNs = startNs + durationMs * 1_000_000L
        val wallUntilMs = SystemClock.elapsedRealtime() + maxOf(durationMs * 2 + 15_000L, 45_000L)
        var frames = 0L
        val info = MediaCodec.BufferInfo()
        while (SystemClock.elapsedRealtimeNanos() < endNs && SystemClock.elapsedRealtime() < wallUntilMs) {
            val outIndex = codec.dequeueOutputBuffer(info, 10_000)
            if (outIndex >= 0) {
                frames++
                codec.releaseOutputBuffer(outIndex, false)
            }
        }
        if (SystemClock.elapsedRealtime() >= wallUntilMs && SystemClock.elapsedRealtimeNanos() < endNs) {
            return EncoderProbeResult(
                false,
                0.0,
                "mime=$mime capture_wall_timeout size=${size.width}x${size.height}",
                size,
                fpsRange,
                "hfr",
            )
        }
        val measured = frames.toDouble() / (durationMs / 1000.0)
        EncoderProbeResult(
            true,
            measured,
            "mime=$mime size=${size.width}x${size.height} range=${fpsRange.lower}-${fpsRange.upper}",
            size,
            fpsRange,
            "hfr",
        )
    } catch (t: Throwable) {
        EncoderProbeResult(
            false,
            0.0,
            "mime=$mime size=${size.width}x${size.height} range=${fpsRange.lower}-${fpsRange.upper} ${t::class.java.simpleName}:${t.message}",
            size,
            fpsRange,
            "hfr",
        )
    } finally {
        runCatching { session?.stopRepeating() }
        runCatching { session?.close() }
        runCatching { camera?.close() }
        runCatching { codec?.stop() }
        runCatching { codec?.release() }
        runCatching { inputSurface?.release() }
        runCatching { ht.quitSafely() }
    }
}

/**
 * Normal [CameraCaptureSession] + repeating request (for sub-HFR or when HFR list does not apply).
 */
internal fun runSingleRegularEncoderProbe(
    cm: CameraManager,
    camId: String,
    durationMs: Long,
    mime: String,
    size: Size,
    fpsRange: Range<Int>,
): EncoderProbeResult {
    val desiredFps = fpsRange.upper
    val ht = HandlerThread("PNS.RegEnc")
    ht.start()
    val h = Handler(ht.looper)
    var codec: MediaCodec? = null
    var inputSurface: Surface? = null
    var camera: CameraDevice? = null
    var session: CameraCaptureSession? = null
    return try {
        codec = MediaCodec.createEncoderByType(mime)
        val format = MediaFormat.createVideoFormat(mime, size.width, size.height).apply {
            setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
            setInteger(MediaFormat.KEY_FRAME_RATE, desiredFps)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
            setInteger(MediaFormat.KEY_BIT_RATE, 10_000_000)
            runCatching { setInteger(MediaFormat.KEY_PRIORITY, 0) }
            runCatching { setInteger(MediaFormat.KEY_OPERATING_RATE, desiredFps) }
            runCatching { setInteger(MediaFormat.KEY_MAX_B_FRAMES, 0) }
        }
        codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        inputSurface = codec.createInputSurface()
        codec.start()
        val openLatch = CountDownLatch(1)
        cm.openCamera(
            camId,
            object : CameraDevice.StateCallback() {
                override fun onOpened(cd: CameraDevice) {
                    camera = cd
                    openLatch.countDown()
                }
                override fun onDisconnected(cd: CameraDevice) {
                    runCatching { cd.close() }
                    openLatch.countDown()
                }
                override fun onError(cd: CameraDevice, error: Int) {
                    runCatching { cd.close() }
                    openLatch.countDown()
                }
            },
            h,
        )
        if (!openLatch.await(4, TimeUnit.SECONDS) || camera == null) {
            return EncoderProbeResult(false, 0.0, "mime=$mime open_failed", size, fpsRange, "regular")
        }
        val sessLatch = CountDownLatch(1)
        camera!!.createCaptureSessionRegularOutputs(
            listOf(inputSurface!!),
            h,
            object : CameraCaptureSession.StateCallback() {
                override fun onConfigured(sess: CameraCaptureSession) {
                    session = sess
                    sessLatch.countDown()
                }
                override fun onConfigureFailed(sess: CameraCaptureSession) {
                    sessLatch.countDown()
                }
            },
        )
        if (!sessLatch.await(4, TimeUnit.SECONDS) || session == null) {
            return EncoderProbeResult(false, 0.0, "mime=$mime session_failed", size, fpsRange, "regular")
        }
        val req = camera!!.createCaptureRequest(CameraDevice.TEMPLATE_RECORD).apply {
            addTarget(inputSurface!!)
            set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, fpsRange)
        }.build()
        session!!.setRepeatingRequest(req, null, h)
        val startNs = SystemClock.elapsedRealtimeNanos()
        val endNs = startNs + durationMs * 1_000_000L
        val wallUntilMs = SystemClock.elapsedRealtime() + maxOf(durationMs * 2 + 15_000L, 45_000L)
        var frames = 0L
        val info = MediaCodec.BufferInfo()
        while (SystemClock.elapsedRealtimeNanos() < endNs && SystemClock.elapsedRealtime() < wallUntilMs) {
            val outIndex = codec.dequeueOutputBuffer(info, 10_000)
            if (outIndex >= 0) {
                frames++
                codec.releaseOutputBuffer(outIndex, false)
            }
        }
        if (SystemClock.elapsedRealtime() >= wallUntilMs && SystemClock.elapsedRealtimeNanos() < endNs) {
            return EncoderProbeResult(
                false,
                0.0,
                "mime=$mime capture_wall_timeout size=${size.width}x${size.height}",
                size,
                fpsRange,
                "regular",
            )
        }
        val measured = frames.toDouble() / (durationMs / 1000.0)
        EncoderProbeResult(
            true,
            measured,
            "mime=$mime size=${size.width}x${size.height} range=${fpsRange.lower}-${fpsRange.upper}",
            size,
            fpsRange,
            "regular",
        )
    } catch (t: Throwable) {
        EncoderProbeResult(
            false,
            0.0,
            "mime=$mime size=${size.width}x${size.height} range=${fpsRange.lower}-${fpsRange.upper} ${t::class.java.simpleName}:${t.message}",
            size,
            fpsRange,
            "regular",
        )
    } finally {
        runCatching { session?.stopRepeating() }
        runCatching { session?.close() }
        runCatching { camera?.close() }
        runCatching { codec?.stop() }
        runCatching { codec?.release() }
        runCatching { inputSurface?.release() }
        runCatching { ht.quitSafely() }
    }
}

internal fun capabilityNames(caps: IntArray?): JSONArray {
    val ja = JSONArray()
    if (caps == null) return ja
    for (c in caps) {
        val name = when (c) {
            CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_BACKWARD_COMPATIBLE -> "BACKWARD_COMPATIBLE"
            CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_MANUAL_SENSOR -> "MANUAL_SENSOR"
            CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_MANUAL_POST_PROCESSING -> "MANUAL_POST_PROCESSING"
            CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_RAW -> "RAW"
            CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_PRIVATE_REPROCESSING -> "PRIVATE_REPROCESSING"
            CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_READ_SENSOR_SETTINGS -> "READ_SENSOR_SETTINGS"
            CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_BURST_CAPTURE -> "BURST_CAPTURE"
            CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_YUV_REPROCESSING -> "YUV_REPROCESSING"
            CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_DEPTH_OUTPUT -> "DEPTH_OUTPUT"
            CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_CONSTRAINED_HIGH_SPEED_VIDEO -> "CONSTRAINED_HIGH_SPEED_VIDEO"
            CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_MOTION_TRACKING -> "MOTION_TRACKING"
            CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_LOGICAL_MULTI_CAMERA -> "LOGICAL_MULTI_CAMERA"
            CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_MONOCHROME -> "MONOCHROME"
            CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_SECURE_IMAGE_DATA -> "SECURE_IMAGE_DATA"
            CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_SYSTEM_CAMERA -> "SYSTEM_CAMERA"
            CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_ULTRA_HIGH_RESOLUTION_SENSOR -> "ULTRA_HIGH_RESOLUTION_SENSOR"
            CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_OFFLINE_PROCESSING -> "OFFLINE_PROCESSING"
            else -> "UNKNOWN_$c"
        }
        ja.put(name)
    }
    return ja
}
