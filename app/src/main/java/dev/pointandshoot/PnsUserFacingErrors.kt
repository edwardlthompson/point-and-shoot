package dev.pointandshoot

/**
 * Short, user-facing copy for capture/storage failures. Raw [Throwable.message] values from OEM
 * stacks are mapped to stable phrases; keep logs on the throwing site for engineers.
 */
object PnsUserFacingErrors {
    fun stillCaptureFailure(t: Throwable?): String = classifyStorageOrEngine(t)

    fun bracketCaptureFailure(t: Throwable?): String = classifyStorageOrEngine(t)

    private fun classifyStorageOrEngine(t: Throwable?): String {
        val raw = t?.message?.lowercase().orEmpty()
        return when {
            raw.isBlank() -> "Could not save this capture."
            raw.contains("unsupported image format") ->
                "Could not save — this RAW layout is not supported for DNG on this device build."
            raw.contains("no raw buffer") || raw.contains("no jpeg buffer") ->
                "Could not save — the camera did not deliver the full image. Try again in a moment."
            raw.contains("busy") || raw.contains("encode_lane") || raw.contains("engine busy") ->
                "Could not save — the engine is still finishing the last capture. Try again in a moment."
            raw.contains("enospc") || raw.contains("no space") || raw.contains("quota") ->
                "Could not save — storage may be full."
            raw.contains("permission") || raw.contains("eacces") || raw.contains("eperm") ->
                "Could not save — the app could not write to storage."
            raw.contains("i/o") || raw.contains("ioexception") ->
                "Could not save — a read or write error occurred."
            else -> "Could not save this capture."
        }
    }
}
