package dev.pointandshoot.fleet

import dev.pointandshoot.MediaCodecCapabilityProbe
import dev.pointandshoot.ProResProbe
import org.json.JSONObject

/**
 * Expanded catalog evaluators (Milestone **18.1** / **18.2**).
 */
internal object CameraCapabilityCatalogEvaluators {

    fun evaluate(row: CameraCapabilityCatalog.CatalogRow, root: JSONObject): Triple<Boolean, Boolean?, String> =
        when {
            row.id == "raw.dng" -> gateTriple(root, "raw")
            row.id == "video.hfr" || row.id.startsWith("video.hfr.") -> gateTriple(root, "hfr")
            row.id == "face.detect" || row.id == "face.eye_af" || row.id == "face.priority_ae" -> gateTriple(root, "face")
            row.id == "video.dcg_hdr" -> gateTriple(root, "dcgZsl")
            row.id == "video.av1" || row.id.startsWith("video.av1.") -> gateTriple(root, "av1")
            row.id == "video.hevc" || row.id.startsWith("video.hevc.") -> gateTriple(root, "hevc10")
            row.id == "video.uhd60" -> gateTriple(root, "uhd60")
            row.id == "video.raw" || row.id == "video.raw_picker" -> gateTriple(root, "rawVideo")
            row.id == "video.vp9" || row.id.startsWith("video.vp9.") -> {
                val ok = MediaCodecCapabilityProbe.probeSyncSafe().supportsVp9
                Triple(ok, ok, "vp9Encoder=$ok")
            }
            row.id == "video.prores_probe" -> {
                val probe = ProResProbe.probeSync()
                Triple(probe.advertised, null, probe.detail)
            }
            row.id == "video.dual_iso" -> gateTriple(root, "dcgZsl")
            row.id == "video.anamorphic" -> Triple(true, null, "anamorphicSar=metadata")
            row.id == "still.referenceapp_leaf" -> Triple(false, null, "legacy_regression_lane")
            row.id == "dial.monochrome" -> monochromeDial(root)
            row.id == "still.monochrome_sensor" -> Triple(false, null, "probeOnly=monochrome_sensor_inventory")
            row.id == "af.macro_dedicated" -> Triple(false, null, "probeOnly=macro_dedicated_inventory")
            row.id == "legacy.camera1" -> Triple(false, null, "probeOnly=camera1_inventory")
            row.id == "legacy.mediarecorder_hfr_cap" -> Triple(false, null, "probeOnly=mediarecorder_hfr_cap_inventory")
            row.id == "product.toolbox" -> Triple(false, null, "probeOnly=toolbox_inventory")
            row.id == "product.still_image_camera_launch" -> stillImageCameraLaunch(root)
            row.id == "product.hardware_camera_key" -> hardwareCameraKey(root)
            row.id == "product.programmable_hardware_button" -> programmableHardwareButton(root)
            row.id == "gallery.lut_preview" -> Triple(false, null, "probeOnly=lut_preview_inventory")
            row.id == "video.dual" -> gateTriple(root, "dualVideo")
            row.id == "video.multicam_melt" -> gateTriple(root, "multicamMelt")
            row.id == "preview.pip" -> gateTriple(root, "pipPreview")
            row.id == "video.regular.1080p30" -> regular1080p30(root)
            row.id.startsWith("camerax.") -> cameraXMode(root, row.id.removePrefix("camerax.").uppercase())
            row.id == "lens.multi" -> Triple(focalSlotCount(root) > 0, null, "focalSlots=${focalSlotCount(root)}")
            row.id == "lens.focal_row" || row.id == "lens.fleet_focal_row" ->
                Triple(hasFocalRow(root), null, "focalRow=${hasFocalRow(root)}")
            row.id == "lens.uw" -> rolePresent(root, "uw")
            row.id == "lens.wide" -> rolePresent(root, "wide")
            row.id == "lens.tele" -> rolePresent(root, "tele")
            row.id == "lens.ois" -> lensHasOis(root)
            row.id == "lens.eis" -> lensHasEis(root)
            row.id == "lens.aperture" -> lensHasAnyAperture(root)
            row.id == "lens.variable_aperture" -> lensHasVariableAperture(root)
            row.id == "fleet.matrix" -> Triple(true, null, "matrix present")
            row.id == "fleet.parity_sweep" -> Triple(true, null, "parity runner shipped")
            row.id == "root.hfr_unlock" -> Triple(anyHfrAbove60(root), null, "matrix HFR ceiling")
            row.id == "root.max_res_unlock_cph2583" -> rootMaxResUnlock(root)
            row.id.startsWith("tether.") -> Triple(true, null, "tether product row")
            row.id.startsWith("perf.") -> perfProbe(root, row.id)
            row.id.startsWith("audio.") -> Triple(true, null, "audio product row")
            row.id.startsWith("encoder.") -> encoderProbe(row.id)
            else -> Triple(defaultProductSupported(row), null, "")
        }

    private fun gateTriple(root: JSONObject, key: String): Triple<Boolean, Boolean?, String> {
        val gates = firstCameraGate(root, key) ?: return Triple(false, null, "no gate")
        val adv = gates.optBoolean("advertised", false)
        val quick = FleetDeviceMatrix.parseScanTier(root) == FleetDeviceMatrix.ScanTier.QUICK
        val sess = if (quick) null else gates.optBoolean("sessionOk", false)
        val app = gates.optBoolean("appEnabled", false)
        return Triple(adv, sess, "advertised=$adv sessionOk=$sess appEnabled=$app")
    }

    private fun regular1080p30(root: JSONObject): Triple<Boolean, Boolean?, String> {
        val enc = root.optJSONObject(FleetDeviceMatrix.KEY_ENCODER)
        val hasMr = anyStreamSize(root, 1920, 1080) || anyStreamSize(root, 1080, 1920)
        val encRow = encoderHasFps(enc, 30)
        return Triple(hasMr || encRow, null, "halMr=$hasMr enc30=$encRow")
    }

    private fun cameraXMode(root: JSONObject, label: String): Triple<Boolean, Boolean?, String> {
        val cx = root.optJSONObject(FleetDeviceMatrix.KEY_CAMERA_X) ?: return Triple(false, null, "no cameraX slice")
        val byCam = cx.optJSONObject("availableByCamera") ?: return Triple(false, null, "empty")
        var found = false
        val keys = byCam.keys()
        while (keys.hasNext()) {
            val modes = byCam.optJSONArray(keys.next()) ?: continue
            for (i in 0 until modes.length()) {
                if (modes.optJSONObject(i)?.optString("label") == label) {
                    found = true
                    break
                }
            }
        }
        return Triple(found, null, if (found) "mode=$label" else "absent")
    }

    private fun stillImageCameraLaunch(root: JSONObject): Triple<Boolean, Boolean?, String> {
        val slice =
            root.optJSONObject(FleetDeviceMatrix.KEY_PRODUCT)
                ?.optJSONObject("hardwareLaunch")
                ?.optJSONObject("stillImageCamera")
        val registered = slice?.optBoolean("pnsRegistered", false) == true
        val resolvable = slice?.optBoolean("resolvable", false) == true
        return Triple(registered && resolvable, null, "pnsRegistered=$registered resolvable=$resolvable")
    }

    private fun hardwareCameraKey(root: JSONObject): Triple<Boolean, Boolean?, String> {
        val buttons = root.optJSONObject(FleetDeviceMatrix.KEY_PRODUCT)?.optJSONObject("hardwareButtons")
        val probe = buttons?.optJSONObject("interactiveProbe")
        val confirmed =
            probe?.optBoolean("cameraKeyConfirmed", false) == true ||
                probe?.optBoolean("focusKeyConfirmed", false) == true
        val likely = buttons?.optBoolean("dedicatedCameraKeyLikely", false) == true
        return Triple(confirmed || likely, null, "cameraKeyConfirmed=$confirmed dedicatedLikely=$likely")
    }

    private fun programmableHardwareButton(root: JSONObject): Triple<Boolean, Boolean?, String> {
        val buttons = root.optJSONObject(FleetDeviceMatrix.KEY_PRODUCT)?.optJSONObject("hardwareButtons")
        val probe = buttons?.optJSONObject("interactiveProbe")
        val codes = probe?.optJSONArray("programmableKeyCodes") ?: probe?.optJSONArray("distinctKeyCodes")
        val count = codes?.length() ?: 0
        val likely = buttons?.optBoolean("programmableButtonLikely", false) == true
        return Triple(count > 0 || likely, null, "programmableCodes=$count likely=$likely")
    }

    private fun focalSlotCount(root: JSONObject): Int =
        root.optJSONObject(FleetDeviceMatrix.KEY_PRODUCT)?.optJSONArray("focalSlots")?.length() ?: 0

    private fun hasFocalRow(root: JSONObject): Boolean =
        root.optJSONObject(FleetDeviceMatrix.KEY_PRODUCT)?.has("focalRow") == true

    private fun rolePresent(root: JSONObject, role: String): Triple<Boolean, Boolean?, String> {
        val focalRow = root.optJSONObject(FleetDeviceMatrix.KEY_PRODUCT)?.optJSONObject("focalRow")
        val id =
            when (role) {
                "uw" -> focalRow?.optString("uwCameraId")
                "wide" -> focalRow?.optString("wideCameraId")
                "tele" -> focalRow?.optString("teleCameraId")
                else -> null
            }
        return Triple(!id.isNullOrBlank(), null, "$role=$id")
    }

    private fun perfProbe(root: JSONObject, id: String): Triple<Boolean, Boolean?, String> {
        val cams = root.optJSONArray(FleetDeviceMatrix.KEY_CAMERAS) ?: return Triple(false, null, "no cameras")
        for (i in 0 until cams.length()) {
            val perf = cams.optJSONObject(i)?.optJSONObject("performanceProbes") ?: continue
            val openMs = perf.optLong("openCameraMs", -1L)
            if (openMs >= 0) return Triple(true, null, "openCameraMs=$openMs")
        }
        return Triple(id == "perf.battery_adaptive_fps", null, "no perf probes")
    }

    private fun encoderProbe(id: String): Triple<Boolean, Boolean?, String> {
        val slug = id.removePrefix("encoder.").replace('_', '.')
        val probe = MediaCodecCapabilityProbe.probeSyncSafe()
        val ok =
            when {
                slug.contains("avc") -> true
                slug.contains("hevc") -> probe.encoders.isNotEmpty()
                slug.contains("av1") -> probe.supportsAv1
                slug.contains("vp9") -> probe.supportsVp9
                else -> false
            }
        return Triple(ok, null, "encoder=$slug")
    }

    private fun anyHfrAbove60(root: JSONObject): Boolean {
        val cams = root.optJSONArray(FleetDeviceMatrix.KEY_CAMERAS) ?: return false
        for (i in 0 until cams.length()) {
            if (cams.optJSONObject(i)?.optInt("hfrMaxFpsAt1080", 0) ?: 0 > 60) return true
        }
        return false
    }

    private fun rootMaxResUnlock(root: JSONObject): Triple<Boolean, Boolean?, String> {
        val unlock =
            root.optJSONObject(FleetDeviceMatrix.KEY_PRODUCT)
                ?.optJSONObject("experimentalUnlockState")
                ?.optJSONObject("unlockLane")
                ?: return Triple(false, null, "unlockState=missing")
        val eligible = unlock.optBoolean("deviceEligibleCph2583", false)
        val rootGranted = unlock.optBoolean("rootGranted", false)
        val lastAttempt = unlock.optJSONObject("lastAttempt")
        val applied = lastAttempt?.optBoolean("applied", false) == true
        val supported = eligible && rootGranted && applied
        return Triple(
            supported,
            if (supported) true else null,
            "eligible=$eligible rootGranted=$rootGranted applied=$applied",
        )
    }

    private fun anyStreamSize(root: JSONObject, w: Int, h: Int): Boolean {
        val deep = root.optJSONObject(FleetDeviceMatrix.KEY_APPENDIX)?.optJSONObject("deepCaps") ?: return false
        val cams = deep.optJSONArray("cameras") ?: return false
        for (i in 0 until cams.length()) {
            val map = cams.optJSONObject(i)?.optJSONObject("streamConfigurationMap") ?: continue
            if (sizesContain(map, w, h)) return true
        }
        return false
    }

    private fun sizesContain(map: JSONObject, w: Int, h: Int): Boolean {
        fun checkArr(key: String): Boolean {
            val arr = map.optJSONObject("outputSizesByFormat")?.optJSONArray(key)
                ?: map.optJSONObject("outputSizes")?.optJSONArray(key)
                ?: return false
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val ww = o.optInt("w")
                val hh = o.optInt("h")
                if ((ww == w && hh == h) || (ww == h && hh == w)) return true
            }
            return false
        }
        return checkArr("mediaRecorder") || checkArr("surfaceTexture")
    }

    private fun encoderHasFps(enc: JSONObject?, fps: Int): Boolean {
        if (enc == null) return false
        val best = enc.optJSONArray("bestByCameraFps") ?: return false
        for (i in 0 until best.length()) {
            val o = best.optJSONObject(i) ?: continue
            if (o.optInt("fps", -1) == fps) return true
        }
        return false
    }

    private fun lensHasAnyAperture(root: JSONObject): Triple<Boolean, Boolean?, String> {
        val cams = root.optJSONArray(FleetDeviceMatrix.KEY_CAMERAS) ?: return Triple(false, null, "no cameras")
        for (i in 0 until cams.length()) {
            val apertures = cams.optJSONObject(i)?.optJSONObject("lensInfo")?.optJSONArray("availableApertures")
            if (apertures != null && apertures.length() > 0) {
                return Triple(true, null, "apertureCount=${apertures.length()}")
            }
        }
        return Triple(false, null, "aperture=none")
    }

    private fun lensHasVariableAperture(root: JSONObject): Triple<Boolean, Boolean?, String> {
        val cams = root.optJSONArray(FleetDeviceMatrix.KEY_CAMERAS) ?: return Triple(false, null, "no cameras")
        for (i in 0 until cams.length()) {
            val apertures = cams.optJSONObject(i)?.optJSONObject("lensInfo")?.optJSONArray("availableApertures")
            if (apertures != null && apertures.length() > 1) {
                return Triple(true, null, "variableApertureCount=${apertures.length()}")
            }
        }
        return Triple(false, null, "variableAperture=none")
    }

    private fun lensHasOis(root: JSONObject): Triple<Boolean, Boolean?, String> {
        val cams = root.optJSONArray(FleetDeviceMatrix.KEY_CAMERAS) ?: return Triple(false, null, "no cameras")
        for (i in 0 until cams.length()) {
            val lens = cams.optJSONObject(i)?.optJSONObject("lensInfo") ?: continue
            val modes = lens.optJSONArray("opticalStabilizationModes") ?: continue
            for (j in 0 until modes.length()) {
                val m = modes.optString(j)
                if (m.isNotBlank() && !m.equals("OFF", ignoreCase = true)) {
                    return Triple(true, null, "ois=$m")
                }
            }
        }
        return Triple(false, null, "ois=off")
    }

    private fun lensHasEis(root: JSONObject): Triple<Boolean, Boolean?, String> {
        val cams = root.optJSONArray(FleetDeviceMatrix.KEY_CAMERAS) ?: return Triple(false, null, "no cameras")
        for (i in 0 until cams.length()) {
            val modes = cams.optJSONObject(i)?.optJSONArray("capabilitiesNormalized") ?: continue
            for (j in 0 until modes.length()) {
                if (modes.optString(j).contains("EIS", ignoreCase = true)) {
                    return Triple(true, null, "eis=advertised")
                }
            }
        }
        return Triple(false, null, "eis=off")
    }

    private fun monochromeDial(root: JSONObject): Triple<Boolean, Boolean?, String> {
        val fromFocalRow =
            root.optJSONObject(FleetDeviceMatrix.KEY_PRODUCT)
                ?.optJSONObject("focalRow")
                ?.optJSONObject("specialRoles")
                ?.optBoolean("dedicatedMonochrome", false)
                ?: false
        val monoCamId =
            root.optJSONObject(FleetDeviceMatrix.KEY_PRODUCT)
                ?.optJSONObject("focalRow")
                ?.optJSONObject("specialRoles")
                ?.optString("monochromeCameraId")
                ?.takeIf { it.isNotBlank() }
        val fromCapabilities = hasMonochromeCapability(root)
        val supported = fromFocalRow || fromCapabilities
        return Triple(
            supported,
            null,
            "dedicatedMonochrome=$fromFocalRow monoCamId=${monoCamId ?: "-"} capabilityMono=$fromCapabilities",
        )
    }

    private fun hasMonochromeCapability(root: JSONObject): Boolean {
        val cams = root.optJSONArray(FleetDeviceMatrix.KEY_CAMERAS) ?: return false
        for (i in 0 until cams.length()) {
            val caps = cams.optJSONObject(i)?.optJSONArray("capabilitiesNormalized") ?: continue
            for (j in 0 until caps.length()) {
                if (caps.optString(j).contains("MONOCHROME", ignoreCase = true)) {
                    return true
                }
            }
        }
        return false
    }

    private fun firstCameraGate(root: JSONObject, key: String): JSONObject? {
        root.optJSONObject(FleetDeviceMatrix.KEY_PRODUCT)?.optJSONObject("concurrencyGates")?.optJSONObject(key)?.let {
            return it
        }
        val cams = root.optJSONArray(FleetDeviceMatrix.KEY_CAMERAS) ?: return null
        for (i in 0 until cams.length()) {
            val g = cams.optJSONObject(i)?.optJSONObject("featureGates")?.optJSONObject(key)
            if (g != null) return g
        }
        return null
    }

    private fun defaultProductSupported(row: CameraCapabilityCatalog.CatalogRow): Boolean =
        when (row.appStatus) {
            CameraCapabilityCatalog.AppStatus.Shipped,
            CameraCapabilityCatalog.AppStatus.Partial,
            ->
                row.id.startsWith("still.") ||
                    row.id.startsWith("video.") ||
                    row.id.startsWith("hud.") ||
                    row.id.startsWith("af.") ||
                    row.id.startsWith("preview.") ||
                    row.id.startsWith("dial.") ||
                    row.id.startsWith("focal.")
            CameraCapabilityCatalog.AppStatus.ProbeOnly -> false
            CameraCapabilityCatalog.AppStatus.Planned -> false
            CameraCapabilityCatalog.AppStatus.NotApplicable -> false
        }
}
