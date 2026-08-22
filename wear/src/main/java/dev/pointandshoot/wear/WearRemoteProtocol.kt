package dev.pointandshoot.wear

/** Keep in lock-step with phone `PnsRemoteProtocol`. */
object WearRemoteProtocol {
    const val DEFAULT_PORT: Int = 28766
    const val STATUS_PATH: String = "/remote/status"
    const val COMMAND_PATH: String = "/remote"
    const val BLE_SERVICE_UUID: String = "8f7a0001-4e12-4c9a-9b3e-0d1a2f3c4b5a"
    const val BLE_WRITE_UUID: String = "8f7a0002-4e12-4c9a-9b3e-0d1a2f3c4b5a"
    val TIMER_SECONDS: IntArray = intArrayOf(3, 5, 10)

    enum class Action(val wire: String, val ble: Byte) {
        Shutter("shutter", 0x01),
        VideoStart("video_start", 0x02),
        VideoStop("video_stop", 0x03),
        VideoToggle("video_toggle", 0x04),
        Chapter("chapter", 0x05),
        Timer("timer", 0x10),
        CancelTimer("timer_cancel", 0x11),
    }
}
