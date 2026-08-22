@file:Suppress("MagicNumber")

package dev.pointandshoot

/**
 * FOSS watch / LAN / BLE remote contract. No Play Services.
 *
 * HTTP (phone [LanMediaTransferServer]):
 *   GET  /remote/status
 *   POST /remote?action=shutter|video_start|video_stop|video_toggle|chapter|timer|timer_cancel&sec=5
 *
 * BLE GATT write: one byte from [Ble] plus optional timer second in byte 1.
 */
object PnsRemoteProtocol {
    const val HTTP_STATUS_PATH: String = "/remote/status"
    const val HTTP_COMMAND_PATH: String = "/remote"
    const val HTTP_MJPEG_PATH: String = "/mjpeg"
    const val HTTP_SNAPSHOT_PATH: String = "/snapshot.jpg"
    const val HTTP_PROOFING_PATH: String = "/proofing"

    const val BLE_SERVICE_UUID: String = "8f7a0001-4e12-4c9a-9b3e-0d1a2f3c4b5a"
    const val BLE_WRITE_UUID: String = "8f7a0002-4e12-4c9a-9b3e-0d1a2f3c4b5a"
    const val BLE_STATUS_UUID: String = "8f7a0003-4e12-4c9a-9b3e-0d1a2f3c4b5a"
    const val BLE_ADVERTISE_NAME: String = "P&S Remote"

    val TIMER_SECONDS: IntArray = intArrayOf(3, 5, 10)

    enum class Action(val wire: String, val ble: Byte) {
        Shutter("shutter", 0x01),
        VideoStart("video_start", 0x02),
        VideoStop("video_stop", 0x03),
        VideoToggle("video_toggle", 0x04),
        Chapter("chapter", 0x05),
        Timer("timer", 0x10),
        CancelTimer("timer_cancel", 0x11),
        ;

        companion object {
            fun fromWire(raw: String?): Action? =
                entries.firstOrNull { it.wire.equals(raw?.trim(), ignoreCase = true) }

            fun fromBle(code: Byte): Action? =
                entries.firstOrNull { it.ble == code } ?: if (code == 0x10.toByte()) Timer else null
        }
    }

    data class Command(
        val action: Action,
        val timerSec: Int = 0,
        val source: String,
    ) {
        val normalizedTimerSec: Int
            get() = if (action == Action.Timer) timerSec.coerceIn(1, 60) else 0
    }

    fun parseQuery(action: String?, secRaw: String?): Command? {
        val parsed = Action.fromWire(action) ?: return null
        val sec = secRaw?.toIntOrNull() ?: 0
        return Command(parsed, sec, source = "http")
    }

    fun parseBle(bytes: ByteArray): Command? {
        if (bytes.isEmpty()) return null
        val action = Action.fromBle(bytes[0]) ?: return null
        val sec = if (bytes.size > 1) (bytes[1].toInt() and 0xff) else 0
        return Command(action, sec, source = "ble")
    }

    fun encodeBle(command: Command): ByteArray =
        if (command.action == Action.Timer) {
            byteArrayOf(command.action.ble, command.normalizedTimerSec.toByte())
        } else {
            byteArrayOf(command.action.ble)
        }

    fun statusJson(
        recording: Boolean,
        photoPrimary: Boolean,
        ready: Boolean,
        host: String,
        port: Int,
        timerSec: Int = 0,
        hdmi: String = "",
    ): String {
        val hdmiEsc = escape(hdmi)
        val hostEsc = escape(host)
        return """{"ok":true,"recording":$recording,"photo":$photoPrimary,"ready":$ready,""" +
            """"timer":$timerSec,"hdmi":"$hdmiEsc","host":"$hostEsc","port":$port}"""
    }

    private fun escape(s: String): String = s.replace("\\", "\\\\").replace("\"", "\\\"")
}
