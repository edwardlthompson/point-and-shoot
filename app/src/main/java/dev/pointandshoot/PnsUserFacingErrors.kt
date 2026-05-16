package dev.pointandshoot

/**
 * Short, user-facing copy for capture/storage failures. Raw [Throwable.message] values from OEM
 * stacks are mapped to stable phrases; keep logs on the throwing site for engineers.
 */
object PnsUserFacingErrors {
    private const val MAX_TECHNICAL_COPY_CHARS = 4000

    fun stillCaptureFailure(t: Throwable?): String = classifyStorageOrEngine(t)

    fun bracketCaptureFailure(t: Throwable?): String = classifyStorageOrEngine(t)

    /**
     * Whether a snackbar **Retry** affordance is appropriate (transient HAL / pipeline conditions).
     * When true, callers typically pass [onRetry] to [pnsShowSnackbar] instead of clipboard Copy.
     */
    fun shouldOfferRetryAfterStillFailure(t: Throwable?): Boolean = isRetryableCaptureThrowable(t)

    fun shouldOfferRetryAfterBracketFailure(t: Throwable?): Boolean = isRetryableCaptureThrowable(t)

    private fun isRetryableCaptureThrowable(t: Throwable?): Boolean {
        val raw = t?.message?.lowercase().orEmpty()
        val cn = t?.javaClass?.simpleName?.lowercase().orEmpty()
        return when {
            raw.contains("encode_lane") || raw.contains("engine busy") -> true
            raw.contains("busy") && !raw.contains("permission") && !raw.contains("eacces") -> true
            raw.contains("no raw buffer") || raw.contains("no jpeg buffer") -> true
            raw.contains("timed out") || raw.contains("timeout") -> true
            raw.contains("retry") -> true
            cn.contains("timeout") -> true
            else -> false
        }
    }

    /** Longer OEM / stack text for clipboard (Milestone 10 Sprint 10.15). */
    fun technicalDetailForCopy(t: Throwable?): String {
        val m = t?.message?.trim().orEmpty()
        val cn = t?.javaClass?.simpleName ?: "Throwable"
        return if (m.isBlank()) cn else "$cn: $m".take(MAX_TECHNICAL_COPY_CHARS)
    }

    private fun classifyStorageOrEngine(t: Throwable?): String {
        val raw = t?.message?.lowercase().orEmpty()
        return when {
            raw.isBlank() -> "Could not save this capture."
            raw.contains("unsupported image format") ->
                "Could not save — this RAW layout is not supported for DNG on this device build."
            raw.contains("no raw buffer") || raw.contains("no jpeg buffer") ->
                "Could not save — the camera did not deliver the full image. Try again in a moment."
            raw.contains("tonal jpeg path unavailable") || raw.contains("jpeg still session not ready") ->
                "Could not save — the JPEG still path is not ready. Wait for preview, then try again."
            raw.contains("raw still session not ready") || raw.contains("session is updating") ->
                "Could not save — the camera session is still updating. Wait a moment, then try again."
            raw.contains("no tonal tier") ->
                "Could not save — JPEG tier was not ready. Open IMG, set JPEG, and try again."
            raw.contains("tonal still save failed") ->
                "Could not save the JPEG/AVIF/JXL file. Try again."
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
