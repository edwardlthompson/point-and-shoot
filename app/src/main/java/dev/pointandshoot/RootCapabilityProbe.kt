package dev.pointandshoot

import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit

/**
 * Two-stage root-capability probe per BUILD_PLAN §9.
 *
 * **Stage 1 (Static, runs on every cold start).** [probeStatic] walks a
 * fixed list of canonical SU paths using `java.io.File.exists()` only.
 * **No process is forked.** Returns [RootCapability.RootState.NotAvailable]
 * (no SU binary or manager package found) or
 * [RootCapability.RootState.AvailableNotGranted] (binary present, grant
 * status unknown). This stage never triggers an SU prompt and is safe
 * to run from `Application.onCreate`.
 *
 * **Stage 2 (Active, runs ONLY when the user taps "Grant Su").**
 * [requestGrant] forks `Process.exec("su -c id")` with a 5-second
 * timeout, reads stdout for `uid=0`, and returns
 * [RootCapability.RootState.Granted] or [RootCapability.RootState.Denied].
 * This call BLOCKS the calling thread; callers MUST run it off the main
 * thread (the host UI surfaces it through a `LaunchedEffect` /
 * coroutine).
 *
 * The probe is split into a pure-data static layer ([CANONICAL_SU_PATHS]
 * + [collateExistence]) and a small Android-side wrapper so the static
 * layer is JVM-testable without a device.
 */
object RootCapabilityProbe {

    /**
     * Canonical list of paths a rooted Android device may carry. Order
     * is fixed (and mirrored in [collateExistence]) so the unit tests
     * can assert deterministic behavior. A path makes the list ONLY
     * when it is documented by Magisk, KernelSU, or LineageOS upstream
     * - we intentionally do NOT scan arbitrary `$PATH` entries because
     * a present binary at, say, `/data/local/tmp/su` is a far weaker
     * signal than the canonical install location.
     */
    val CANONICAL_SU_PATHS: List<String> = listOf(
        "/system/xbin/su",
        "/system/bin/su",
        "/sbin/su",
        "/system/sbin/su",
        "/su/bin/su",
        "/system/app/Superuser.apk",
        "/data/adb/magisk",
        "/data/adb/ksu",
        "/data/adb/ksud",
    )

    /** Default timeout for the active probe's `su -c id` call. */
    const val ACTIVE_PROBE_TIMEOUT_SECONDS: Long = 5L

    /**
     * Pure-data layer of the static probe. Given an arbitrary
     * `pathExists` predicate (typically `File::exists` on Android, or a
     * `FakeFs` map on the JVM), returns the canonical [RootCapability.RootState]
     * for stage 1.
     *
     * The predicate is called once per entry in [CANONICAL_SU_PATHS]; a
     * single positive hit is enough to flip from `NotAvailable` to
     * `AvailableNotGranted` so the user can decide whether to tap
     * "Grant Su". We intentionally do NOT `stat()` the binary or check
     * setuid bits - a present `Superuser.apk` is also a positive
     * signal even though it's not directly executable.
     */
    fun collateExistence(pathExists: (String) -> Boolean): RootCapability.RootState {
        val anyHit = CANONICAL_SU_PATHS.any(pathExists)
        return if (anyHit) {
            RootCapability.RootState.AvailableNotGranted
        } else {
            RootCapability.RootState.NotAvailable
        }
    }

    /**
     * Stage 1 wrapper around [collateExistence] that uses
     * `java.io.File.exists()` for the predicate. Safe to call from any
     * thread (no I/O beyond a stat call per path); never throws.
     */
    fun probeStatic(): RootCapability.RootState =
        collateExistence { path -> runCatching { File(path).exists() }.getOrDefault(false) }

    /**
     * Stage 2 active probe. Forks `su -c id`, reads stdout, and returns
     * [RootCapability.RootState.Granted] iff stdout contains `uid=0`.
     * Returns [RootCapability.RootState.Denied] for every other outcome
     * (timeout, non-zero exit, no `uid=0` in stdout, IOException).
     *
     * The static probe MUST have already returned `AvailableNotGranted`
     * before this is called - the implementation will short-circuit to
     * `NotAvailable` if it cannot find an SU binary at all (defensive,
     * since the user could have tapped Grant Su after uninstalling the
     * SU manager).
     *
     * The block is wrapped in [runCatching] so a misbehaving SU manager
     * (process never starts, returns garbage, etc.) cannot crash the
     * calling thread.
     */
    fun requestGrant(timeoutSeconds: Long = ACTIVE_PROBE_TIMEOUT_SECONDS): RootCapability.RootState {
        if (probeStatic() == RootCapability.RootState.NotAvailable) {
            return RootCapability.RootState.NotAvailable
        }
        val outcome = runCatching {
            val pb = ProcessBuilder("su", "-c", "id")
            pb.redirectErrorStream(true)
            val process = pb.start()
            val finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS)
            if (!finished) {
                runCatching { process.destroyForcibly() }
                return@runCatching false
            }
            val stdout = BufferedReader(InputStreamReader(process.inputStream)).use { it.readText() }
            stdout.contains("uid=0")
        }
        return if (outcome.getOrDefault(false)) {
            RootCapability.RootState.Granted
        } else {
            RootCapability.RootState.Denied
        }
    }

    /**
     * Convenience helper for the unit tests: simulate the active probe
     * with a synthetic `idOutput` string. Returns `Granted` iff the
     * input contains `uid=0`, `Denied` otherwise. Decoupled from
     * [requestGrant] so tests can pin the parsing contract without
     * forking a process.
     */
    fun parseIdOutput(idOutput: String?): RootCapability.RootState {
        if (idOutput == null) return RootCapability.RootState.Denied
        return if (idOutput.contains("uid=0")) {
            RootCapability.RootState.Granted
        } else {
            RootCapability.RootState.Denied
        }
    }
}
