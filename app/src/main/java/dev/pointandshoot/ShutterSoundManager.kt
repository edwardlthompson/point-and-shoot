package dev.pointandshoot

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.SoundPool
import android.media.ToneGenerator
import android.util.Log
import dev.pointandshoot.PnsLog
import org.json.JSONObject
import java.io.File

/**
 * Sprint **AS.2** — shutter sounds with app volume, optional haptic sync, import/export of pack choice.
 * Samples: CC0 BigSoundBank (see assets/sounds/shutter_cc0/SOURCE.txt).
 */
class ShutterSoundManager(
    private val appContext: Context,
) {
    private var soundPool: SoundPool? = null
    private val sampleIdByPack = mutableMapOf<ShutterSoundPack, Int>()
    private var focusConfirmSampleId: Int = 0
    private var toneGenerator: ToneGenerator? = null
    private var activeSampleStreamId: Int = 0

    /** Hardware S1 half-press — short focus-lock chirp (shutter volume; Silent pack = no-op). */
    fun playFocusConfirm(chrome: PreviewChromePreferences) {
        val pack = ShutterSoundPack.fromStorageKey(chrome.shutterSoundPackKey)
        if (pack == ShutterSoundPack.Silent) {
            PnsLog.d(TAG, "focusConfirm=silent")
            return
        }
        val volume = chrome.shutterSoundVolume.coerceIn(0f, 1f)
        if (volume <= 0f) return
        try {
            stopActiveSampleStream()
            val played = playFocusConfirmSample(volume)
            if (!played) {
                playFocusConfirmToneFallback(volume)
            }
            PnsLog.i(TAG, "focusConfirm volume=$volume sample=$played")
            PnsAdbLog.i(appContext, "focusConfirm ok=true volume=$volume sample=$played")
        } catch (e: Throwable) {
            PnsLog.w(TAG, "focusConfirm failed: ${e.message}")
            PnsAdbLog.i(appContext, "focusConfirm ok=false err=${e.message}")
        }
    }

    fun playShutter(chrome: PreviewChromePreferences, haptics: CaptureHaptics?) {
        val pack = ShutterSoundPack.fromStorageKey(chrome.shutterSoundPackKey)
        if (pack == ShutterSoundPack.Silent) {
            Log.d(TAG, "shutterSound=silent")
            return
        }
        val volume = chrome.shutterSoundVolume.coerceIn(0f, 1f)
        if (volume <= 0f) return
        runCatching {
            stopActiveSampleStream()
            val played = playSample(pack, volume)
            if (!played) {
                playToneFallback(pack, volume)
            }
            Log.i(
                TAG,
                "shutterSound pack=${pack.storageKey} volume=$volume sample=$played hapticSync=${chrome.shutterHapticSync}",
            )
            PnsAdbLog.i(appContext, "shutterSound ok=true pack=${pack.storageKey} volume=$volume sample=$played")
            if (chrome.shutterHapticSync) {
                haptics?.fireStillTickNow()
            }
        }.onFailure { e ->
            Log.w(TAG, "shutterSound failed: ${e.message}")
            PnsAdbLog.i(appContext, "shutterSound ok=false err=${e.message}")
        }
    }

    fun release() {
        runCatching { soundPool?.release() }
        soundPool = null
        sampleIdByPack.clear()
        focusConfirmSampleId = 0
        activeSampleStreamId = 0
        runCatching { toneGenerator?.release() }
        toneGenerator = null
    }

    fun exportPackConfig(chrome: PreviewChromePreferences): File {
        val dir = File(appContext.filesDir, EXPORT_DIR).apply { mkdirs() }
        val out = File(dir, EXPORT_FILE)
        val json =
            JSONObject()
                .put("shutterSoundPack", chrome.shutterSoundPackKey)
                .put("shutterSoundVolume", chrome.shutterSoundVolume.toDouble())
                .put("shutterHapticSync", chrome.shutterHapticSync)
        out.writeText(json.toString(2))
        Log.i(TAG, "shutterSoundExport path=${out.absolutePath}")
        return out
    }

    fun importPackConfig(file: File): PreviewChromePreferences? {
        if (!file.isFile) return null
        return runCatching {
            val o = JSONObject(file.readText())
            val base = PreviewChromePreferences.load(appContext)
            base.copy(
                shutterSoundPackKey = o.optString("shutterSoundPack", base.shutterSoundPackKey),
                shutterSoundVolume = o.optDouble("shutterSoundVolume", base.shutterSoundVolume.toDouble()).toFloat(),
                shutterHapticSync = o.optBoolean("shutterHapticSync", base.shutterHapticSync),
            )
        }.getOrNull()?.also {
            PreviewChromePreferences.save(appContext, it)
            Log.i(TAG, "shutterSoundImport pack=${it.shutterSoundPackKey}")
        }
    }

    private fun playSample(pack: ShutterSoundPack, volume: Float): Boolean {
        if (!pack.hasSample) return false
        val pool = soundPool ?: createSoundPool().also { soundPool = it }
        val sampleId =
            sampleIdByPack[pack] ?: run {
                val id = pool.load(appContext, pack.soundResId, 1)
                if (id != 0) sampleIdByPack[pack] = id
                id
            }
        if (sampleId == 0) return false
        val streamId = pool.play(sampleId, volume, volume, 1, 0, 1f)
        if (streamId != 0) {
            activeSampleStreamId = streamId
            return true
        }
        activeSampleStreamId = 0
        return false
    }

    private fun createSoundPool(): SoundPool {
        val attrs =
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
        val pool =
            SoundPool.Builder()
                .setMaxStreams(4)
                .setAudioAttributes(attrs)
                .build()
        ShutterSoundPack.entries.filter { it.hasSample }.forEach { pack ->
            val id = pool.load(appContext, pack.soundResId, 1)
            if (id != 0) sampleIdByPack[pack] = id
        }
        val focusId = pool.load(appContext, R.raw.focus_confirm, 1)
        if (focusId != 0) focusConfirmSampleId = focusId
        return pool
    }

    private fun playFocusConfirmSample(volume: Float): Boolean {
        val pool = soundPool ?: createSoundPool().also { soundPool = it }
        if (focusConfirmSampleId == 0) {
            val id = pool.load(appContext, R.raw.focus_confirm, 1)
            if (id != 0) focusConfirmSampleId = id
        }
        if (focusConfirmSampleId == 0) return false
        val streamId = pool.play(focusConfirmSampleId, volume, volume, 1, 0, 1f)
        if (streamId != 0) {
            activeSampleStreamId = streamId
            return true
        }
        activeSampleStreamId = 0
        return false
    }

    private fun playFocusConfirmToneFallback(volume: Float) {
        runCatching {
            releaseToneOnly()
            val tg = createToneGenerator(volume).also { toneGenerator = it }
            tg.startTone(ToneGenerator.TONE_PROP_ACK, 50)
            Log.w(TAG, "focusConfirm tone fallback")
        }
    }

    private fun stopActiveSampleStream() {
        if (activeSampleStreamId == 0) return
        runCatching { soundPool?.stop(activeSampleStreamId) }
        activeSampleStreamId = 0
    }

    /** Rare cold path if [SoundPool.play] returns 0 before decode finishes. */
    private fun playToneFallback(pack: ShutterSoundPack, volume: Float) {
        runCatching {
            releaseToneOnly()
            val tg = createToneGenerator(volume).also { toneGenerator = it }
            when (pack) {
                ShutterSoundPack.ClassicMechanical -> tg.startTone(ToneGenerator.TONE_PROP_ACK, 35)
                ShutterSoundPack.DigitalBeep -> tg.startTone(ToneGenerator.TONE_PROP_BEEP, 50)
                ShutterSoundPack.VintageClick -> tg.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 60)
                ShutterSoundPack.Silent -> Unit
            }
            Log.w(TAG, "shutterSound tone fallback pack=${pack.storageKey}")
        }
    }

    private fun releaseToneOnly() {
        runCatching { toneGenerator?.release() }
        toneGenerator = null
    }

    private fun createToneGenerator(volume: Float): ToneGenerator {
        val volPct = (volume * 100f).toInt().coerceIn(1, 100)
        @Suppress("DEPRECATION")
        return ToneGenerator(AudioManager.STREAM_NOTIFICATION, volPct)
    }

    companion object {
        private const val TAG = "PNS.ShutterSound"
        private const val EXPORT_DIR = "shutter_sound"
        private const val EXPORT_FILE = "shutter_sound_pack.json"
    }
}
