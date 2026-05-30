package dev.pointandshoot

import android.content.Context
import android.media.Image
import android.net.Uri
import android.util.Log
import dev.pointandshoot.fleet.FleetCapabilityGate
import dev.pointandshoot.fleet.OnePlus13FleetPolicy

/**
 * Milestone **13.6** — RAW video lane (no [android.media.MediaRecorder]).
 * Drains preview-session RAW [android.media.ImageReader] frames into [RawVideoWriter].
 */
class RawVideoRecordingController(
    private val appContext: Context,
) {
    @Volatile
    var isRecording: Boolean = false
        private set

    private var writer: RawVideoWriter? = null
    private var storageHandle: CaptureStorage.Handle? = null
    private var savedUri: Uri? = null
    private var width: Int = 0
    private var height: Int = 0
    private var format: Int = 0
    private var framesWritten: Int = 0

    fun fleetSupportsRawVideo(cameraId: String?): Boolean {
        val id = cameraId?.trim().orEmpty()
        if (id.isEmpty()) return false
        FleetCapabilityGate.featureGate(appContext, id, "rawVideo")?.let { gate ->
            return gate.appEnabled && gate.sessionOk
        }
        if (!OnePlus13FleetPolicy.appliesToDevice()) return false
        return id == OnePlus13FleetPolicy.CANONICAL_WIDE ||
            id == OnePlus13FleetPolicy.CANONICAL_UW ||
            id == OnePlus13FleetPolicy.CANONICAL_TELE
    }

    fun startRecording(
        cameraId: String,
        profile: ImagingProfile,
        width: Int,
        height: Int,
        imageFormat: Int,
        dualIsoMerge: Boolean = false,
    ): Boolean {
        if (isRecording) return true
        if (!fleetSupportsRawVideo(cameraId)) {
            Log.w(TAG, "rawVideo start blocked fleet cam=$cameraId")
            return false
        }
        return runCatching {
            val handle =
                CaptureStorage.openMcrawVideoOutput(
                    appContext,
                    profile,
                    filenameSuffix = "rawvid",
                )
            storageHandle = handle
            writer =
                RawVideoWriter(
                    out = handle.output,
                    width = width,
                    height = height,
                    imageFormat = imageFormat,
                    dualIsoMerge = dualIsoMerge,
                )
            this.width = width
            this.height = height
            this.format = imageFormat
            isRecording = true
            framesWritten = 0
            Log.i(TAG, "rawVideoStart ${width}x$height format=$imageFormat dualIso=$dualIsoMerge")
            PnsAdbLog.i(appContext, "rawVideoStart format=$imageFormat ${width}x$height dualIso=$dualIsoMerge")
            true
        }.getOrElse { e ->
            Log.e(TAG, "rawVideo start failed", e)
            tearDownFailed()
            false
        }
    }

    fun offerFrame(image: Image) {
        if (!isRecording) {
            runCatching { image.close() }
            return
        }
        val w = writer ?: run {
            runCatching { image.close() }
            return
        }
        runCatching {
            val ts = image.timestamp
            w.appendFrame(image, ts)
            framesWritten++
            if (framesWritten == 1 || framesWritten % 15 == 0) {
                Log.i(TAG, "rawVideoFrame n=$framesWritten")
            }
        }.onFailure { e ->
            Log.w(TAG, "rawVideo frame append failed", e)
        }
        runCatching { image.close() }
    }

    fun stopRecording(): Uri? {
        if (!isRecording && savedUri != null) return savedUri
        runCatching { writer?.finish() }
        val frames = framesWritten
        writer = null
        isRecording = false
        val handle = storageHandle
        storageHandle = null
        val uri =
            if (frames > 0 && handle != null) {
                runCatching { handle.close() }.getOrNull()
                handle.uri
            } else {
                handle?.let { runCatching { it.discard() } }
                null
            }
        savedUri = uri
        val bytes =
            uri?.let { u ->
                runCatching {
                    appContext.contentResolver.openFileDescriptor(u, "r")?.use { it.statSize }
                }.getOrNull()
            }
        val ok = uri != null && frames > 0
        Log.i(
            TAG,
            "rawVideoSaved ok=$ok frames=$frames bytes=${bytes ?: 0} uri=$uri " +
                "${width}x$height format=$format",
        )
        PnsAdbLog.i(
            appContext,
            "rawVideoSaved ok=$ok frames=$frames bytes=${bytes ?: 0} lane=RAW " +
                "format=$format ${width}x$height",
        )
        return uri
    }

    fun discard() {
        writer = null
        isRecording = false
        storageHandle?.let { runCatching { it.discard() } }
        storageHandle = null
        savedUri = null
    }

    private fun tearDownFailed() {
        writer = null
        isRecording = false
        storageHandle?.let { runCatching { it.discard() } }
        storageHandle = null
    }

    companion object {
        private const val TAG = "PNS.RawVideo"
    }
}
