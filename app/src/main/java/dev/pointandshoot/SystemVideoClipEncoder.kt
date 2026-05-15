package dev.pointandshoot

import android.media.MediaCodec
import android.media.MediaCodecInfo.CodecCapabilities
import android.media.MediaFormat
import android.media.MediaMuxer
import android.os.Build
import android.os.ParcelFileDescriptor
import android.util.Log
import kotlin.math.max
import kotlin.math.min

/**
 * Writes a very short H.264/AAC-free MP4 clip for [MediaStore.ACTION_VIDEO_CAPTURE] return flows.
 * Uses YUV420 byte-buffer input (no EGL) so it can run after the Camera2 preview session without
 * sharing a capture surface.
 */
@Suppress("MagicNumber")
object SystemVideoClipEncoder {

    private const val TAG = "PNS.VideoClipEnc"

    fun encodeSolidColorClip(
        pfd: ParcelFileDescriptor,
        width: Int,
        height: Int,
        preferHighQuality: Boolean,
    ): Result<Unit> =
        runCatching {
            val w = width and -2
            val h = height and -2
            require(w >= 64 && h >= 64) { "size too small $w x $h" }
            val frameRate = 30
            val durationMs = if (preferHighQuality) 1200L else 800L
            val bitRate = if (preferHighQuality) 4_000_000 else 1_200_000
            val mime = "video/avc"
            val codec = MediaCodec.createEncoderByType(mime)
            val colorFormat = pickEncoderColorFormat(codec, mime)
            val format =
                MediaFormat.createVideoFormat(mime, w, h).apply {
                    setInteger(MediaFormat.KEY_COLOR_FORMAT, colorFormat)
                    setInteger(MediaFormat.KEY_BIT_RATE, bitRate)
                    setInteger(MediaFormat.KEY_FRAME_RATE, frameRate)
                    setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 2)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N_MR1) {
                        runCatching { setInteger(MediaFormat.KEY_PRIORITY, 0) }
                    }
                }
            codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            codec.start()
            val muxer = MediaMuxer(pfd.fileDescriptor, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            var videoTrack = -1
            var muxerStarted = false
            var inputDone = false
            val frameIntervalUs = 1_000_000L / frameRate
            var inputFrame = 0L
            val totalFrames = max(2, (durationMs * frameRate / 1000).toInt())
            val bufferInfo = MediaCodec.BufferInfo()
            var iteration = 0
            val maxIterations = totalFrames * 20 + 500
            try {
                while (iteration++ < maxIterations) {
                    if (!inputDone) {
                        val inIx = codec.dequeueInputBuffer(10_000)
                        if (inIx >= 0) {
                            val ptsUs = inputFrame * frameIntervalUs
                            if (inputFrame >= totalFrames) {
                                codec.queueInputBuffer(
                                    inIx,
                                    0,
                                    0,
                                    ptsUs,
                                    MediaCodec.BUFFER_FLAG_END_OF_STREAM,
                                )
                                inputDone = true
                            } else {
                                val cap = codec.getInputBuffer(inIx) ?: error("null input buffer")
                                cap.clear()
                                fillNv12Black(cap, w, h, colorFormat)
                                codec.queueInputBuffer(inIx, 0, cap.position(), ptsUs, 0)
                                inputFrame++
                            }
                        }
                    }
                    val outIx = codec.dequeueOutputBuffer(bufferInfo, 10_000)
                    when {
                        outIx == MediaCodec.INFO_TRY_AGAIN_LATER -> {}
                        outIx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                            if (muxerStarted) error("format changed twice")
                            val newFmt = codec.outputFormat
                            videoTrack = muxer.addTrack(newFmt)
                            muxer.start()
                            muxerStarted = true
                        }
                        outIx >= 0 -> {
                            val encoded = codec.getOutputBuffer(outIx)
                            if (muxerStarted && encoded != null && bufferInfo.size > 0) {
                                encoded.position(bufferInfo.offset)
                                encoded.limit(bufferInfo.offset + bufferInfo.size)
                                muxer.writeSampleData(videoTrack, encoded, bufferInfo)
                            }
                            val eos = (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0
                            codec.releaseOutputBuffer(outIx, false)
                            if (eos) break
                        }
                    }
                }
                check(iteration < maxIterations) { "video encode watchdog exceeded" }
            } finally {
                runCatching {
                    if (muxerStarted) muxer.stop()
                }
                runCatching { muxer.release() }
                runCatching {
                    codec.stop()
                    codec.release()
                }
            }
            Log.i(TAG, "encodeSolidColorClip ok ${w}x$h frames=$totalFrames highQ=$preferHighQuality")
            Unit
        }.onFailure { Log.w(TAG, "encodeSolidColorClip failed: ${it.message}", it) }

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

    /**
     * Limited-range dark gray (Y≈16, UV=128) for stable encoder output.
     */
    private fun fillNv12Black(buf: java.nio.ByteBuffer, width: Int, height: Int, colorFormat: Int) {
        val ySize = width * height
        val uvH = height / 2
        val uvW = width / 2
        when (colorFormat) {
            CodecCapabilities.COLOR_FormatYUV420Planar -> {
                for (i in 0 until ySize) buf.put(16.toByte())
                for (i in 0 until uvW * uvH) buf.put(128.toByte())
                for (i in 0 until uvW * uvH) buf.put(128.toByte())
            }
            else -> {
                for (i in 0 until ySize) buf.put(16.toByte())
                val uvCount = uvW * uvH * 2
                for (i in 0 until uvCount) buf.put(128.toByte())
            }
        }
    }
}
