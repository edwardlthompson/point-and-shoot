package dev.pointandshoot

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaCodec
import android.media.MediaCodecInfo.CodecCapabilities
import android.media.MediaFormat
import android.media.MediaMuxer
import android.net.Uri
import android.os.Build
import android.os.ParcelFileDescriptor
import android.util.Log
import java.nio.ByteBuffer
import kotlin.math.max

/**
 * Sprint **15.27** — frame-by-frame H.264 MP4 from intervalometer JPEG stills.
 * PTS = frameIdx × (1e6 / [FRAME_RATE]).
 */
@Suppress("MagicNumber")
class TimeLapseVideoEncoder {

    private var codec: MediaCodec? = null
    private var muxer: MediaMuxer? = null
    private var pfd: ParcelFileDescriptor? = null
    private var outputUri: Uri? = null
    private var videoTrack = -1
    private var muxerStarted = false
    private var encodeWidth = 0
    private var encodeHeight = 0
    private var colorFormat = CodecCapabilities.COLOR_FormatYUV420SemiPlanar
    private var inputDone = false
    private var frameCount = 0
    private val bufferInfo = MediaCodec.BufferInfo()

    fun encodeJpegFrame(
        context: Context,
        profile: ImagingProfile,
        jpegBytes: ByteArray,
    ): Result<Int> =
        runCatching {
            val decoded =
                BitmapFactory.decodeByteArray(jpegBytes, 0, jpegBytes.size)
                    ?: error("JPEG decode failed")
            try {
                val (w, h) = fitEncoderDimensions(decoded.width, decoded.height)
                require(w >= 64 && h >= 64) { "frame too small ${decoded.width}x${decoded.height}" }
                if (codec == null) {
                    openEncoder(context, profile, w, h)
                }
                val scaled =
                    if (decoded.width != encodeWidth || decoded.height != encodeHeight) {
                        Bitmap.createScaledBitmap(decoded, encodeWidth, encodeHeight, true)
                    } else {
                        decoded
                    }
                try {
                    queueFrame(scaled, frameCount)
                    drainEncoder(endOfStream = false)
                    frameCount++
                    Log.i(TAG, "encodeJpegFrame ok frame=$frameCount ${encodeWidth}x$encodeHeight")
                    frameCount
                } finally {
                    if (scaled !== decoded) {
                        runCatching { scaled.recycle() }
                    }
                }
            } finally {
                runCatching { decoded.recycle() }
            }
        }.onFailure { Log.w(TAG, "encodeJpegFrame failed: ${it.message}", it) }

    fun finish(context: Context): Result<Uri> =
        runCatching {
            val uri = outputUri ?: error("encoder not started")
            queueEndOfStream()
            drainEncoder(endOfStream = true)
            runCatching {
                if (muxerStarted) muxer?.stop()
            }
            runCatching { muxer?.release() }
            runCatching {
                codec?.stop()
                codec?.release()
            }
            muxer = null
            codec = null
            pfd?.close()
            pfd = null
            CaptureStorage.finalizePendingVideoInsert(context.applicationContext, uri)
            PnsAdbLog.i(
                context.applicationContext,
                "timelapseVideoSaved ok=true saved=$uri frames=$frameCount ${encodeWidth}x$encodeHeight",
            )
            Log.i(
                TAG,
                "finish ok uri=$uri frames=$frameCount ${encodeWidth}x$encodeHeight",
            )
            uri
        }.onFailure { err ->
            Log.w(TAG, "finish failed: ${err.message}", err)
            abort(context)
        }

    fun abort(context: Context) {
        runCatching {
            if (muxerStarted) muxer?.stop()
        }
        runCatching { muxer?.release() }
        runCatching {
            codec?.stop()
            codec?.release()
        }
        muxer = null
        codec = null
        runCatching { pfd?.close() }
        pfd = null
        outputUri?.let { uri ->
            runCatching { CaptureStorage.discardPendingVideo(context.applicationContext, uri) }
        }
        outputUri = null
        frameCount = 0
        inputDone = false
        muxerStarted = false
        videoTrack = -1
    }

    fun frameCount(): Int = frameCount

    private fun openEncoder(
        context: Context,
        profile: ImagingProfile,
        width: Int,
        height: Int,
    ) {
        encodeWidth = width
        encodeHeight = height
        val (uri, fd) = CaptureStorage.openVideoOutputReadWritePfd(context.applicationContext, profile)
        outputUri = uri
        pfd = fd
        val mime = "video/avc"
        val enc = MediaCodec.createEncoderByType(mime)
        colorFormat = pickEncoderColorFormat(enc, mime)
        val bitRate = max(1_200_000, encodeWidth * encodeHeight * 4)
        val format =
            MediaFormat.createVideoFormat(mime, encodeWidth, encodeHeight).apply {
                setInteger(MediaFormat.KEY_COLOR_FORMAT, colorFormat)
                setInteger(MediaFormat.KEY_BIT_RATE, bitRate)
                setInteger(MediaFormat.KEY_FRAME_RATE, FRAME_RATE)
                setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 2)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N_MR1) {
                    runCatching { setInteger(MediaFormat.KEY_PRIORITY, 0) }
                }
            }
        enc.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        enc.start()
        codec = enc
        muxer = MediaMuxer(fd.fileDescriptor, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        Log.i(TAG, "openEncoder ${encodeWidth}x$encodeHeight colorFormat=$colorFormat uri=$uri")
    }

    private fun queueFrame(bitmap: Bitmap, frameIdx: Int) {
        val enc = codec ?: error("codec not started")
        var queued = false
        var attempts = 0
        while (!queued && attempts++ < 200) {
            val inIx = enc.dequeueInputBuffer(10_000)
            if (inIx >= 0) {
                val cap = enc.getInputBuffer(inIx) ?: error("null input buffer")
                cap.clear()
                fillNv12FromBitmap(cap, bitmap, encodeWidth, encodeHeight, colorFormat)
                val ptsUs = framePtsUs(frameIdx)
                enc.queueInputBuffer(inIx, 0, cap.position(), ptsUs, 0)
                queued = true
            } else {
                drainEncoder(endOfStream = false)
            }
        }
        check(queued) { "queueFrame timeout frame=$frameIdx" }
    }

    private fun queueEndOfStream() {
        if (inputDone) return
        val enc = codec ?: return
        var attempts = 0
        while (attempts++ < 200) {
            val inIx = enc.dequeueInputBuffer(10_000)
            if (inIx >= 0) {
                val ptsUs = framePtsUs(max(frameCount, 1))
                enc.queueInputBuffer(
                    inIx,
                    0,
                    0,
                    ptsUs,
                    MediaCodec.BUFFER_FLAG_END_OF_STREAM,
                )
                inputDone = true
                return
            }
            drainEncoder(endOfStream = false)
        }
        error("queueEndOfStream timeout")
    }

    private fun drainEncoder(endOfStream: Boolean) {
        val enc = codec ?: return
        val mux = muxer ?: return
        var iterations = 0
        while (iterations++ < 500) {
            val outIx = enc.dequeueOutputBuffer(bufferInfo, 10_000)
            when {
                outIx == MediaCodec.INFO_TRY_AGAIN_LATER -> {
                    if (!endOfStream) return
                    if (iterations > 400) break
                }
                outIx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    if (muxerStarted) error("format changed twice")
                    videoTrack = mux.addTrack(enc.outputFormat)
                    mux.start()
                    muxerStarted = true
                }
                outIx >= 0 -> {
                    val encoded = enc.getOutputBuffer(outIx)
                    if (muxerStarted && encoded != null && bufferInfo.size > 0) {
                        encoded.position(bufferInfo.offset)
                        encoded.limit(bufferInfo.offset + bufferInfo.size)
                        mux.writeSampleData(videoTrack, encoded, bufferInfo)
                    }
                    val eos = (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0
                    enc.releaseOutputBuffer(outIx, false)
                    if (eos) return
                }
            }
        }
    }

    companion object {
        const val TAG = "PNS.TimeLapse"
        const val FRAME_RATE = 30
        const val FRAME_INTERVAL_US = 1_000_000L / FRAME_RATE
        private const val MAX_LONG_EDGE = 1920

        fun framePtsUs(frameIdx: Int): Long = frameIdx.toLong() * FRAME_INTERVAL_US

        fun fitEncoderDimensions(srcWidth: Int, srcHeight: Int): Pair<Int, Int> {
            val srcW = srcWidth and -2
            val srcH = srcHeight and -2
            val longEdge = max(srcW, srcH)
            if (longEdge <= MAX_LONG_EDGE) return srcW to srcH
            val scale = MAX_LONG_EDGE.toFloat() / longEdge
            val w = ((srcW * scale).toInt() and -2).coerceAtLeast(64)
            val h = ((srcH * scale).toInt() and -2).coerceAtLeast(64)
            return w to h
        }

        private fun pickEncoderColorFormat(codec: MediaCodec, mime: String): Int {
            val caps = codec.codecInfo.getCapabilitiesForType(mime)
            val prefs =
                intArrayOf(
                    CodecCapabilities.COLOR_FormatYUV420SemiPlanar,
                    CodecCapabilities.COLOR_FormatYUV420Flexible,
                    CodecCapabilities.COLOR_FormatYUV420Planar,
                )
            for (p in prefs) {
                if (caps.colorFormats.contains(p)) return p
            }
            return caps.colorFormats.first()
        }

        private fun fillNv12FromBitmap(
            buf: ByteBuffer,
            bitmap: Bitmap,
            width: Int,
            height: Int,
            colorFormat: Int,
        ) {
            val pixels = IntArray(width * height)
            bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
            val ySize = width * height
            val uvW = width / 2
            val uvH = height / 2
            val yPlane = ByteArray(ySize)
            val uPlane = ByteArray(uvW * uvH)
            val vPlane = ByteArray(uvW * uvH)
            var yi = 0
            for (j in 0 until height) {
                for (i in 0 until width) {
                    val c = pixels[yi++]
                    val r = (c shr 16) and 0xFF
                    val g = (c shr 8) and 0xFF
                    val b = c and 0xFF
                    val y = ((66 * r + 129 * g + 25 * b + 128) shr 8) + 16
                    yPlane[j * width + i] = y.coerceIn(16, 235).toByte()
                }
            }
            for (j in 0 until height step 2) {
                for (i in 0 until width step 2) {
                    val c = pixels[j * width + i]
                    val r = (c shr 16) and 0xFF
                    val g = (c shr 8) and 0xFF
                    val b = c and 0xFF
                    val u = ((-38 * r - 74 * g + 112 * b + 128) shr 8) + 128
                    val v = ((112 * r - 94 * g - 18 * b + 128) shr 8) + 128
                    val uvIdx = (j / 2) * uvW + (i / 2)
                    uPlane[uvIdx] = u.coerceIn(16, 240).toByte()
                    vPlane[uvIdx] = v.coerceIn(16, 240).toByte()
                }
            }
            when (colorFormat) {
                CodecCapabilities.COLOR_FormatYUV420Planar -> {
                    buf.put(yPlane)
                    buf.put(uPlane)
                    buf.put(vPlane)
                }
                else -> {
                    buf.put(yPlane)
                    for (k in uPlane.indices) {
                        buf.put(uPlane[k])
                        buf.put(vPlane[k])
                    }
                }
            }
        }
    }
}
