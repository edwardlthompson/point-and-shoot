package dev.pointandshoot

/**
 * Pure-data root-capability state machine + feature gate per BUILD_PLAN
 * §9 "Root-only enhancements (opt-in; every feature has a non-root fallback)".
 *
 * Point & Shoot is designed so that **100 % of the user-visible features
 * work on a stock, un-rooted device.** Root access is offered ONLY as a
 * way to unlock peak performance / quality / diagnostics on devices that
 * already have it (LineageOS user-debug, Magisk, KernelSU). Every
 * root-only feature MUST advertise:
 *   1. A clear *purpose* (what does it do beyond the non-root path?).
 *   2. A clear *fallback* (what happens when root is not present?).
 *   3. A non-deceptive label in the Root Only settings drawer.
 *
 * No Android imports - safe for unit testing on the JVM.
 */
object RootCapability {

    /** Bumped only when the JSON / persisted-state schema changes incompatibly. */
    const val SCHEMA_VERSION: Int = 1

    /**
     * The five disjoint states of the root-capability state machine:
     *
     *   * [Unknown] - probe has not yet run (the canonical initial value
     *     used by [RootGate.evaluate] when called from a UI surface that
     *     hasn't yet bound a probe result).
     *   * [NotAvailable] - the static probe walked every canonical SU
     *     path and found nothing AND no SU manager package.
     *   * [AvailableNotGranted] - at least one SU binary or manager
     *     package was found but the user has not yet tapped "Grant Su".
     *   * [Granted] - the user tapped "Grant Su" and the active probe
     *     read `uid=0` from `su -c id`.
     *   * [Denied] - the user tapped "Grant Su" and the active probe
     *     either timed out, returned non-zero, or the SU manager dialog
     *     was rejected.
     *
     * Transitions:
     *   * `Unknown -> NotAvailable | AvailableNotGranted` (Stage 1 static probe)
     *   * `AvailableNotGranted -> Granted | Denied` (Stage 2 active probe)
     *   * `Granted -> AvailableNotGranted` (user revokes via SU manager)
     *
     * The state is intentionally NOT a `sealed class` so it can be
     * persisted as a single `String` SharedPreferences value via [name].
     */
    enum class RootState {
        Unknown,
        NotAvailable,
        AvailableNotGranted,
        Granted,
        Denied;

        /** True iff a root-only feature should run its privileged path. */
        val grantsPrivileged: Boolean get() = this == Granted

        /**
         * True iff the "Grant Su" button should be enabled in this state.
         *
         * Only [AvailableNotGranted] (the canonical "we found SU paths,
         * tap Grant Su to escalate") and [Denied] (let the user retry
         * after a misclick on the SU manager dialog) qualify. The other
         * three states all suppress the button:
         *
         *   * [Unknown] - probe hasn't run yet; nothing to escalate.
         *   * [NotAvailable] - no SU binary found; an active probe would
         *     just crash with `Exception: su: not found` and confuse the
         *     user. The fallback path is the only path here.
         *   * [Granted] - already granted; the button would be a no-op.
         *
         * Surfaced as a pure-data accessor so the Compose drawer can ask
         * `state.canRequestGrant` and JVM tests can exhaustively pin the
         * single-row truth table without needing Robolectric.
         */
        val canRequestGrant: Boolean
            get() = this == AvailableNotGranted || this == Denied

        /** Human-readable, single-line status string for the HUD / drawer banner. */
        val displayName: String
            get() = when (this) {
                Unknown -> "Probing..."
                NotAvailable -> "Not available on this device"
                AvailableNotGranted -> "Available - tap Grant Su to unlock"
                Granted -> "Granted - root features unlocked"
                Denied -> "Denied - operating without root"
            }
    }

    /**
     * Every root-only enhancement Point & Shoot ships. Each entry MUST
     * have a non-blank purpose AND a non-blank fallback (validated by
     * [RootGate.requireFallbacks]) so the Root Only drawer can never
     * render a row that lies about what root unlocks.
     */
    enum class Feature {
        /**
         * `setprop persist.vendor.camera.<key>=<val>` for known-good
         * OnePlus / OPPO toggles to unlock hidden HFR / RAW modes the
         * standard `CameraManager` does not advertise.
         */
        VendorSetProp,

        /**
         * `setprop ctl.restart cameraserver` to recover from camera HAL
         * hangs without a full reboot.
         */
        CameraServerRestart,

        /**
         * Pin SoC governor to `performance` during long HFR captures
         * by writing the per-core `scaling_governor` files under
         * `/sys/devices/system/cpu/cpuN/cpufreq/`.
         */
        CpuGovernorPin,

        /**
         * Read `trip_point_*_temp` files under
         * `/sys/class/thermal/thermal_zoneN/` for earlier user-facing
         * thermal warnings than `PowerManager`'s 5-bucket API exposes.
         */
        ThermalTripRead,

        /**
         * `logcat -b crash,events,system,radio` via root for HAL crash
         * capture in the diagnostic dump.
         */
        LogcatSystemWide,

        /**
         * Read OEM vendor request keys that require `system_app` or root
         * signature (e.g. `com.oplus.*` private namespace keys).
         */
        VendorKeyProbe,

        /**
         * `persist.vendor.camera.preview.size=<W>x<H>` for unusual
         * configurations the standard `StreamConfigurationMap` does not
         * include.
         */
        ResolutionOverride,

        /**
         * Read `/sys/class/leds/lcd-backlight/brightness` for absolute
         * backlight cd/m^2 used in the exposure preview.
         */
        BacklightRead,
    }

    /**
     * The user-facing description of a single root-only feature: what
     * does enabling it do, what does Point & Shoot do without it, and
     * the canonical disabled-reason string.
     */
    data class FeatureDescriptor(
        val feature: Feature,
        val displayName: String,
        val purpose: String,
        val fallback: String,
    ) {
        init {
            require(displayName.isNotBlank()) { "displayName must not be blank for $feature" }
            require(purpose.isNotBlank()) { "purpose must not be blank for $feature" }
            require(fallback.isNotBlank()) { "fallback must not be blank for $feature" }
        }

        /**
         * Canonical disabled-reason string. Always starts with
         * `"Requires root. Fallback: "` so a single regex can extract
         * the fallback summary from any disabled row.
         */
        val disabledReason: String get() = "Requires root. Fallback: $fallback"
    }

    /** Catalog of every shipped root-only feature with its purpose + fallback. */
    val FEATURE_DESCRIPTORS: Map<Feature, FeatureDescriptor> = mapOf(
        Feature.VendorSetProp to FeatureDescriptor(
            feature = Feature.VendorSetProp,
            displayName = "Vendor camera setprop toggles",
            purpose = "Unlock hidden HFR / RAW modes via persist.vendor.camera.<key>=<val>.",
            fallback = "Standard Camera2 modes only; the HUD shows a 'Standard mode' badge.",
        ),
        Feature.CameraServerRestart to FeatureDescriptor(
            feature = Feature.CameraServerRestart,
            displayName = "Camera HAL recovery",
            purpose = "Recover from a hung camera HAL via setprop ctl.restart cameraserver, no reboot needed.",
            fallback = "Show a 'Camera HAL hung - please power-cycle the device' toast and force-close the app.",
        ),
        Feature.CpuGovernorPin to FeatureDescriptor(
            feature = Feature.CpuGovernorPin,
            displayName = "CPU governor pin during HFR",
            purpose = "Pin every CPU core to the performance governor during long HFR captures to avoid governor-induced frame drops.",
            fallback = "Use Android PowerManager.boostMode and accept that the kernel will downclock under thermal pressure; the HUD already surfaces phase9_thermal warnings.",
        ),
        Feature.ThermalTripRead to FeatureDescriptor(
            feature = Feature.ThermalTripRead,
            displayName = "Thermal trip-point read",
            purpose = "Read kernel /sys/class/thermal/thermal_zone*/trip_point_*_temp for earlier user-facing thermal warnings.",
            fallback = "Use PowerManager.currentThermalStatus (5 buckets) plus the existing phase9_thermal_*.json ringbuffer.",
        ),
        Feature.LogcatSystemWide to FeatureDescriptor(
            feature = Feature.LogcatSystemWide,
            displayName = "System-wide logcat ringbuffer",
            purpose = "Capture HAL crashes via logcat -b crash,events,system,radio for the diagnostic dump.",
            fallback = "DiagnosticsMode.dump writes a UID-filtered logcat -d - sufficient for app-side bugs but blind to HAL bugs.",
        ),
        Feature.VendorKeyProbe to FeatureDescriptor(
            feature = Feature.VendorKeyProbe,
            displayName = "Selective vendor-key probing",
            purpose = "Read OEM vendor request keys that require system_app or root signature (e.g. com.oplus.* private namespace).",
            fallback = "Infer capabilities from documented standard keys; missing data is logged in DODGE_PROFILE.md vendor-key gaps.",
        ),
        Feature.ResolutionOverride to FeatureDescriptor(
            feature = Feature.ResolutionOverride,
            displayName = "Force-resolution override",
            purpose = "Write persist.vendor.camera.preview.size=<W>x<H> for resolutions the standard StreamConfigurationMap does not include.",
            fallback = "StreamConfigurationMap filtering picks the closest supported size; the HUD shows the actual resolution.",
        ),
        Feature.BacklightRead to FeatureDescriptor(
            feature = Feature.BacklightRead,
            displayName = "Backlight brightness read",
            purpose = "Read /sys/class/leds/lcd-backlight/brightness for absolute backlight cd/m^2 used in the exposure preview.",
            fallback = "Trust WindowManager.LayoutParams.screenBrightness and the user's HDR toggle.",
        ),
    )
}

/**
 * Pure-data feature gate that translates a [RootCapability.RootState]
 * into per-feature `enabled` / `disabledReason` flags for the Root Only
 * settings drawer.
 *
 * Decoupled from the existing [CapabilityGate] (which gates UI features
 * on hardware caps) because root state and hardware caps are independent
 * axes - a feature can be hardware-supported AND require root, or
 * hardware-supported AND root-free, etc.
 */
object RootGate {

    /**
     * One result per [RootCapability.Feature]. `enabled` is true ONLY
     * when the active state grants privileged access; otherwise the
     * canonical disabled reason (see [RootCapability.FeatureDescriptor.disabledReason])
     * is surfaced.
     */
    data class GateResult(
        val feature: RootCapability.Feature,
        val descriptor: RootCapability.FeatureDescriptor,
        val enabled: Boolean,
        val disabledReason: String?,
        val state: RootCapability.RootState,
    )

    /**
     * Evaluate every shipped feature against the supplied [state].
     * Returns a stable-order list (mirrors the `Feature` enum
     * declaration order) so the UI can render a consistent layout.
     */
    fun evaluate(state: RootCapability.RootState): List<GateResult> {
        return RootCapability.Feature.entries.map { feature ->
            val descriptor = RootCapability.FEATURE_DESCRIPTORS[feature]
                ?: error("missing FeatureDescriptor for $feature - is FEATURE_DESCRIPTORS in sync with the enum?")
            val enabled = state.grantsPrivileged
            GateResult(
                feature = feature,
                descriptor = descriptor,
                enabled = enabled,
                disabledReason = if (enabled) null else descriptor.disabledReason,
                state = state,
            )
        }
    }

    /**
     * Convenience accessor for the single-feature path. Returns the
     * canonical `GateResult` for [feature] with [state] applied.
     */
    fun evaluate(feature: RootCapability.Feature, state: RootCapability.RootState): GateResult {
        return evaluate(state).first { it.feature == feature }
    }

    /**
     * Sanity check used by the unit tests: every shipped feature MUST
     * have a non-blank purpose AND a non-blank fallback. This is
     * enforced at compile time by [RootCapability.FeatureDescriptor.init],
     * but a dedicated check makes the failure mode obvious if the catalog
     * map is ever incomplete.
     */
    fun requireFallbacks() {
        for (feature in RootCapability.Feature.entries) {
            val descriptor = RootCapability.FEATURE_DESCRIPTORS[feature]
                ?: error("missing FeatureDescriptor for $feature")
            require(descriptor.purpose.isNotBlank()) { "purpose blank for $feature" }
            require(descriptor.fallback.isNotBlank()) { "fallback blank for $feature" }
        }
    }
}
