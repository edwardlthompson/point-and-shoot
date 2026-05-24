package dev.pointandshoot



import androidx.annotation.RawRes



/**

 * Sprint **AS.2** — built-in shutter sound packs (CC0 samples in [R.raw]).

 */

enum class ShutterSoundPack(

    val storageKey: String,

    val label: String,

    @param:RawRes val soundResId: Int = 0,

) {

    ClassicMechanical("mechanical", "Mechanical", R.raw.shutter_digital),

    DigitalBeep("digital", "Digital", R.raw.shutter_mechanical),

    VintageClick("vintage", "Vintage", R.raw.shutter_vintage),

    Silent("silent", "Silent"),

    ;



    val hasSample: Boolean

        get() = soundResId != 0



    companion object {

        fun fromStorageKey(raw: String?): ShutterSoundPack =

            entries.firstOrNull { it.storageKey.equals(raw, ignoreCase = true) } ?: ClassicMechanical

    }

}


