package dev.pointandshoot

import android.content.Context
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit

private const val SU_TIMEOUT_SEC = 8L

private const val EXIT_TIMEOUT = 124

private const val LOGCAT_TAIL_LINES = 12

private const val DUMPSYS_HEAD_LINES = 30

/**
 * Read-only **`su -c`** probes for **Milestone 7 Sprint 7.5** — exercises the catalog features with
 * safe shell I/O only (no `setprop` writes, no `ctl.restart cameraserver`, no governor writes).
 *
 * Logs one **`PNS.AdbValidation`** line per feature via [PnsAdbLog]. Destructive catalog entries log
 * **`ok=skipped`** with **`reason=destructive_requires_confirmation`**.
 */
object RootPrivilegedDiagnostics {

    /** Truncate for log lines (single-line). */
    fun excerptForLog(raw: String, maxLen: Int = 160): String =
        raw.trim().replace("\n", " ").take(maxLen)

    private fun runSu(context: Context, cmd: String): Pair<Int, String> {
        val pb = ProcessBuilder("su", "-c", cmd)
        pb.redirectErrorStream(true)
        return runCatching {
            val proc = pb.start()
            val finished = proc.waitFor(SU_TIMEOUT_SEC, TimeUnit.SECONDS)
            if (!finished) {
                runCatching { proc.destroyForcibly() }
                return@runCatching (EXIT_TIMEOUT to "timeout")
            }
            val out = BufferedReader(InputStreamReader(proc.inputStream)).use { it.readText() }
            proc.exitValue() to out
        }.getOrElse { e ->
            PnsAdbLog.w(context, "rootPrivScan su_fork_err err=${e::class.java.simpleName}")
            (1 to (e.message ?: e::class.java.simpleName))
        }
    }

    private fun logLine(context: Context, feature: RootCapability.Feature, ok: Boolean, exit: Int, detail: String) {
        val ex = excerptForLog(detail)
        PnsAdbLog.i(
            context,
            "rootPrivScan feature=${feature.name} ok=$ok exit=$exit excerpt=$ex",
        )
    }

    /**
     * When [state] is [RootCapability.RootState.Granted], runs read-only probes; otherwise logs a single skip line.
     */
    fun runScan(context: Context, state: RootCapability.RootState) {
        val app = context.applicationContext
        if (!PnsAdbLog.isEnabled(app)) {
            return
        }
        if (state != RootCapability.RootState.Granted) {
            PnsAdbLog.i(app, "rootPrivScan skipped state=${state.name}")
            return
        }

        // --- Read-only vendor camera props (catalog: VendorSetProp) ---
        val propKeys =
            listOf(
                "persist.vendor.camera.preview.size",
                "vendor.camera.aux.packagelist",
                "ro.vendor.build.fingerprint",
            )
        val propLines = StringBuilder()
        for (k in propKeys) {
            val (ex, out) = runSu(app, "getprop $k")
            if (ex == 0 && out.isNotBlank()) {
                propLines.append(k).append('=').append(out.trim()).append("; ")
            }
        }
        val propOk = propLines.isNotBlank()
        logLine(
            app,
            RootCapability.Feature.VendorSetProp,
            propOk,
            if (propOk) 0 else 1,
            if (propOk) propLines.toString() else "no_vendor_props",
        )

        // --- Destructive: cameraserver restart (not run) ---
        PnsAdbLog.i(
            app,
            "rootPrivScan feature=${RootCapability.Feature.CameraServerRestart.name} ok=false " +
                "exit=0 excerpt=skipped destructive_requires_confirmation",
        )

        // --- CPU governor read (catalog: CpuGovernorPin) ---
        val govPaths =
            listOf(
                "/sys/devices/system/cpu/cpu0/cpufreq/scaling_governor",
                "/sys/devices/system/cpu/cpu0/cpufreq/scaling_cur_freq",
            )
        val govSb = StringBuilder()
        for (p in govPaths) {
            val (ex, out) = runSu(app, "cat $p 2>/dev/null")
            if (ex == 0 && out.isNotBlank()) {
                govSb.append(p.substringAfterLast('/')).append('=').append(out.trim()).append(' ')
            }
        }
        val govOk = govSb.isNotBlank()
        logLine(app, RootCapability.Feature.CpuGovernorPin, govOk, if (govOk) 0 else 1, govSb.toString().ifBlank { "no_sysfs" })

        // --- Thermal trip (catalog: ThermalTripRead) ---
        val thermalPaths =
            listOf(
                "/sys/class/thermal/thermal_zone0/trip_point_0_temp",
                "/sys/class/thermal/thermal_zone0/trip_point_0_type",
                "/sys/class/thermal/thermal_zone0/temp",
            )
        val thSb = StringBuilder()
        for (p in thermalPaths) {
            val (ex, out) = runSu(app, "cat $p 2>/dev/null")
            if (ex == 0 && out.isNotBlank()) {
                thSb.append(p.substringAfterLast('/')).append('=').append(out.trim()).append(' ')
            }
        }
        val thOk = thSb.isNotBlank()
        logLine(app, RootCapability.Feature.ThermalTripRead, thOk, if (thOk) 0 else 1, thSb.toString().ifBlank { "no_thermal_sysfs" })

        // --- Short logcat tail (catalog: LogcatSystemWide) ---
        val (lcEx, lcOut) = runSu(app, "logcat -d -t $LOGCAT_TAIL_LINES *:E 2>/dev/null")
        logLine(
            app,
            RootCapability.Feature.LogcatSystemWide,
            lcEx == 0 && lcOut.isNotBlank(),
            lcEx,
            if (lcOut.isNotBlank()) lcOut else "empty_or_denied",
        )

        // --- dumpsys media.camera head (catalog: VendorKeyProbe) ---
        val (dsEx, dsOut) =
            runSu(
                app,
                "sh -c 'dumpsys media.camera 2>/dev/null | head -n $DUMPSYS_HEAD_LINES'",
            )
        logLine(
            app,
            RootCapability.Feature.VendorKeyProbe,
            dsEx == 0 && dsOut.isNotBlank(),
            dsEx,
            if (dsOut.isNotBlank()) dsOut else "empty_or_denied",
        )

        // --- Vendor highlight AE toggle (catalog: VendorHighlightAe) ---
        val vh = VendorHighlightAePrefs.isTryExtraModesEnabled(app)
        PnsAdbLog.i(
            app,
            "rootPrivScan feature=${RootCapability.Feature.VendorHighlightAe.name} ok=true exit=0 " +
                "excerpt=tryExtraModes=$vh",
        )

        // --- Resolution override prop read (catalog: ResolutionOverride) ---
        val (rxEx, rxOut) = runSu(app, "getprop persist.vendor.camera.preview.size")
        logLine(
            app,
            RootCapability.Feature.ResolutionOverride,
            rxEx == 0 && rxOut.isNotBlank(),
            rxEx,
            rxOut.ifBlank { "empty" },
        )

        // --- Backlight sysfs (catalog: BacklightRead) ---
        val blPaths =
            listOf(
                "/sys/class/leds/lcd-backlight/brightness",
                "/sys/class/backlight/panel0-backlight/brightness",
            )
        var blOut = ""
        var blOk = false
        for (p in blPaths) {
            val (ex, out) = runSu(app, "cat $p 2>/dev/null")
            if (ex == 0 && out.isNotBlank()) {
                blOut = "${p.substringAfterLast('/')}=${out.trim()}"
                blOk = true
                break
            }
        }
        logLine(app, RootCapability.Feature.BacklightRead, blOk, if (blOk) 0 else 1, blOut.ifBlank { "no_backlight_sysfs" })

        PnsAdbLog.i(app, "rootPrivScan suite=read_only_done")
    }
}
