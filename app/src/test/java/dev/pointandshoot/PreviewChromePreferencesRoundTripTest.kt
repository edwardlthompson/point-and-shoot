package dev.pointandshoot

import android.content.SharedPreferences
import org.junit.Assert.assertEquals
import org.junit.Test

class PreviewChromePreferencesRoundTripTest {

    @Test
    fun saveLoad_roundTripsAllFields() {
        val prefs = InMemorySharedPreferences()
        val original =
            PreviewChromePreferences(
                maxBrightnessInPreview = false,
                dndWhileInPreview = false,
                dndWhileRecording = true,
                volumeKeysCapture = false,
                btRemoteShutter = true,
                hardwareCameraKeyCapture = false,
                saveLocationWithMedia = true,
                showOnScreenShutter = false,
                tapPreviewToCapture = false,
                liveChartCornerOverlay = true,
                staticPreviewRotationDeg = 180,
                selfTimerDelaySec = 5,
                stillCaptureJpegCompanion = false,
                previewFlashMode = PreviewFlashMode.Torch,
                inAppVideoEncodeWidth = 1920,
                inAppVideoEncodeHeight = 1080,
                inAppVideoCodecOrdinal = VideoCodec.H265.ordinal,
                inAppVideoFps = 60,
                inAppVideoColorSpaceOrdinal = 2,
                stillColorSpaceOrdinal = 1,
                stillExportKindOrdinal = 0,
                audioHiFiCapture = true,
                audioWindNoiseReduction = false,
                audioPreferExternalInput = false,
                shutterSoundPackKey = ShutterSoundPack.Silent.storageKey,
                shutterSoundVolume = 0.42f,
                shutterHapticSync = true,
                audioLightCompression = true,
                audioVoiceoverDucking = true,
            )
        PreviewChromePreferences.saveToStorage(prefs, original)
        val loaded = PreviewChromePreferences.loadFromStorage(prefs)
        assertEquals(original, loaded)
        assertEquals(PreviewChromePreferences.PREFS_SCHEMA_VERSION, prefs.getInt("prefs_schema_version", -1))
    }

    @Test
    fun schemaMigration_v0NormalizesLegacyValues() {
        val prefs = InMemorySharedPreferences()
        prefs.edit()
            .putInt("self_timer_delay_sec", 7)
            .putInt("static_preview_rotation_deg", 45)
            .putInt("preview_flash_mode_ordinal", 99)
            .putFloat("shutter_sound_volume", 2.5f)
            .commit()
        PreviewChromePreferences.runSchemaMigrations(prefs)
        assertEquals(0, prefs.getInt("self_timer_delay_sec", -1))
        assertEquals(90, prefs.getInt("static_preview_rotation_deg", -1))
        assertEquals(PreviewFlashMode.Auto.ordinal, prefs.getInt("preview_flash_mode_ordinal", -1))
        assertEquals(1f, prefs.getFloat("shutter_sound_volume", -1f), 0f)
        assertEquals(PreviewChromePreferences.PREFS_SCHEMA_VERSION, prefs.getInt("prefs_schema_version", -1))
    }
}

/** Minimal in-memory [SharedPreferences] for JVM unit tests (no Robolectric). */
private class InMemorySharedPreferences : SharedPreferences {
    private val map = mutableMapOf<String, Any?>()

    override fun getAll(): MutableMap<String, *> = map.toMutableMap()

    override fun getString(key: String?, defValue: String?): String? =
        map[key] as? String ?: defValue

    override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? =
        @Suppress("UNCHECKED_CAST")
        (map[key] as? MutableSet<String>) ?: defValues

    override fun getInt(key: String?, defValue: Int): Int =
        map[key] as? Int ?: defValue

    override fun getLong(key: String?, defValue: Long): Long =
        map[key] as? Long ?: defValue

    override fun getFloat(key: String?, defValue: Float): Float =
        map[key] as? Float ?: defValue

    override fun getBoolean(key: String?, defValue: Boolean): Boolean =
        map[key] as? Boolean ?: defValue

    override fun contains(key: String?): Boolean = map.containsKey(key)

    override fun edit(): SharedPreferences.Editor = Editor()

    override fun registerOnSharedPreferenceChangeListener(
        listener: SharedPreferences.OnSharedPreferenceChangeListener?,
    ) = Unit

    override fun unregisterOnSharedPreferenceChangeListener(
        listener: SharedPreferences.OnSharedPreferenceChangeListener?,
    ) = Unit

    private inner class Editor : SharedPreferences.Editor {
        private val pending = mutableMapOf<String, Any?>()
        private val removals = mutableSetOf<String>()
        private var clearAll = false

        override fun putString(key: String?, value: String?): SharedPreferences.Editor {
            pending[key!!] = value
            return this
        }

        override fun putStringSet(key: String?, values: MutableSet<String>?): SharedPreferences.Editor {
            pending[key!!] = values
            return this
        }

        override fun putInt(key: String?, value: Int): SharedPreferences.Editor {
            pending[key!!] = value
            return this
        }

        override fun putLong(key: String?, value: Long): SharedPreferences.Editor {
            pending[key!!] = value
            return this
        }

        override fun putFloat(key: String?, value: Float): SharedPreferences.Editor {
            pending[key!!] = value
            return this
        }

        override fun putBoolean(key: String?, value: Boolean): SharedPreferences.Editor {
            pending[key!!] = value
            return this
        }

        override fun remove(key: String?): SharedPreferences.Editor {
            removals.add(key!!)
            return this
        }

        override fun clear(): SharedPreferences.Editor {
            clearAll = true
            return this
        }

        override fun commit(): Boolean {
            apply()
            return true
        }

        override fun apply() {
            if (clearAll) map.clear()
            removals.forEach { map.remove(it) }
            map.putAll(pending)
            pending.clear()
            removals.clear()
            clearAll = false
        }
    }
}
