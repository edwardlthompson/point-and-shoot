package dev.pointandshoot.fleet

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.Build
import android.util.Log
import dev.pointandshoot.CameraXExtensionProbe
import dev.pointandshoot.DeviceCameraCapabilityCache
import dev.pointandshoot.FleetCameraStartupScan
import dev.pointandshoot.MediaCodecCapabilityProbe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * Builds [FleetDeviceMatrix] JSON from existing shallow / fleet probes (Milestone **16.0** quick tier;
 * **16.1** full tier).
 */
object FleetDeviceMatrixBuilder {
    const val TAG = "PNS.FleetMatrix"

    data class BuildResult(
        val root: JSONObject,
        val scanDurationMs: Long,
        val cameraCount: Int,
        val degraded: Boolean,
        val diff: FleetDeviceMatrixDiff.DiffResult? = null,
    )

    /**
     * Session-free scan: [DeviceCameraCapabilityCache] per [cameraId], fleet profiles, focal map.
     */
    fun buildQuick(
        context: Context,
        prebuiltCameras: JSONArray? = null,
        prebuiltShallowRoot: JSONObject? = null,
        prebuiltDegraded: Boolean? = null,
    ): BuildResult {
        val t0 = System.nanoTime()
        val app = context.applicationContext
        val cm = app.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        var degraded = prebuiltDegraded ?: false
        val camerasJson =
            prebuiltCameras ?: run {
                val arr = JSONArray()
                for (id in cm.cameraIdList.sorted()) {
                    val cc = runCatching { cm.getCameraCharacteristics(id) }.getOrNull()
                    if (cc == null) {
                        degraded = true
                        continue
                    }
                    val map = cc.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
                    runCatching {
                        arr.put(DeviceCameraCapabilityCache.cameraJson(id, cc, map))
                    }.onFailure { e ->
                        degraded = true
                        Log.w(TAG, "cameraJson id=$id failed: ${e.message}")
                    }
                }
                arr
            }
        val shallowRoot =
            prebuiltShallowRoot
                ?: DeviceCameraCapabilityCache.buildRoot(app, camerasJson, degraded)
        val fleetSnap = FleetCameraProfileBuilder.buildSnapshot(app)
        val focalEntries = FleetCameraStartupScan.scanNow(app)
        val product = buildProductJson(app, fleetSnap, focalEntries, DeviceFeatureGates.build(cm))
        val durationMs = (System.nanoTime() - t0) / 1_000_000L
        val meta = scanMetaQuick(app, durationMs)
        val appendix =
            FleetDeviceMatrix.emptyAppendix().apply {
                put("shallowCache", shallowRoot)
            }
        val encoder = EncoderFleetSlice.build(app)
        val root = assembleRoot(
            meta = meta,
            cameras = camerasJson,
            product = product,
            cameraX = JSONObject.NULL,
            runtimeProbes = JSONObject.NULL,
            appendix = appendix,
            encoder = encoder,
        )
        return BuildResult(
            root = root,
            scanDurationMs = durationMs,
            cameraCount = camerasJson.length(),
            degraded = degraded,
        )
    }

    /**
     * Full tier: deep caps + session probes + structured [cameras[]], CameraX slice, diff vs history.
     */
    suspend fun buildFull(
        context: Context,
        onProgress: suspend (String) -> Unit = {},
    ): BuildResult =
        withContext(Dispatchers.IO) {
            val t0 = System.nanoTime()
            val app = context.applicationContext
            val cm = app.getSystemService(Context.CAMERA_SERVICE) as CameraManager
            var degraded = false

            onProgress("Deep caps probe…")
            val deepRoot = DeepCapsProbeCore.probe(app, onProgress)
            val deepById = camerasById(deepRoot.optJSONArray("cameras"))

            onProgress("Session matrix probe…")
            val sessionRoot = SessionMatrixProbeCore.probe(app, onProgress)
            val sessionById = camerasById(sessionRoot.optJSONArray("cameras"))

            onProgress("HAL dumpsys (redacted)…")
            val halDumpsys = FleetHalAppendix.captureRedacted()

            if (CameraXExtensionProbe.cached == null) {
                onProgress("CameraX extension probe (sync)…")
                CameraXExtensionProbe.probe(app)
            }

            val fleetSnap = FleetCameraProfileBuilder.buildSnapshot(app)
            val profileById = fleetSnap.profiles.associateBy { it.cameraId }
            val focalEntries = FleetCameraStartupScan.scanNow(app)
            val deviceGates = DeviceFeatureGates.build(cm)

            val structuredCameras = JSONArray()
            for (id in cm.cameraIdList.sorted()) {
                val cc = runCatching { cm.getCameraCharacteristics(id) }.getOrNull()
                if (cc == null) {
                    degraded = true
                    continue
                }
                val map = cc.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
                val shallow =
                    runCatching {
                        DeviceCameraCapabilityCache.cameraJson(id, cc, map)
                    }.getOrElse { e ->
                        degraded = true
                        Log.w(TAG, "cameraJson id=$id failed: ${e.message}")
                        JSONObject().put("cameraId", id)
                    }
                val deepCam = deepById[id]?.also { FleetDeviceMatrixStructured.enrichDeepCamWithFaceModes(it, cc) }
                val sessionCam = sessionById[id]
                structuredCameras.put(
                    FleetDeviceMatrixStructured.buildCameraEntry(
                        shallow = shallow,
                        deepCam = deepCam,
                        sessionCam = sessionCam,
                        fleetProfile = profileById[id],
                        cc = cc,
                        deviceGates = deviceGates,
                    ),
                )
            }

            val shallowRoot = DeviceCameraCapabilityCache.buildRoot(app, structuredCameras, degraded)
            val product = buildProductJson(app, fleetSnap, focalEntries, deviceGates)
            val durationMs = (System.nanoTime() - t0) / 1_000_000L
            val meta = scanMetaFull(app, durationMs)
            val appendix =
                JSONObject().apply {
                    put("deepCaps", deepRoot)
                    put("sessionMatrix", sessionRoot)
                    put("shallowCache", shallowRoot)
                    if (halDumpsys != null) {
                        put("halDumpsysMediaCamera", halDumpsys)
                    }
                }
            val encoder = EncoderFleetSlice.build(app)
            val runtimeProbes =
                JSONObject().apply {
                    put("sessionMatrixSummary", sessionSummary(sessionRoot))
                }
            val compliance = complianceRollup(app, meta)
            val root =
                assembleRoot(
                    meta = meta,
                    cameras = structuredCameras,
                    product = product,
                    cameraX = FleetDeviceMatrixStructured.cameraXSlice(),
                    runtimeProbes = runtimeProbes,
                    appendix = appendix,
                    complianceRollup = compliance,
                    encoder = encoder,
                )

            val previous =
                runCatching {
                    val file = FleetDeviceMatrixStore.matrixFile(app)
                    if (file.exists()) JSONObject(file.readText()) else null
                }.getOrNull() ?: FleetDeviceMatrixStore.loadLatestHistory(app)
            val diff = FleetDeviceMatrixDiff.diff(previous, root)
            root.optJSONObject(FleetDeviceMatrix.KEY_APPENDIX)?.put("diffVsPrevious", diff.toJson())

            BuildResult(
                root = root,
                scanDurationMs = durationMs,
                cameraCount = structuredCameras.length(),
                degraded = degraded,
                diff = diff,
            )
        }

    /**
     * Quick scan + persist when cache miss or [forceRescan].
     */
    fun buildQuickAndSave(context: Context, forceRescan: Boolean = false): BuildResult? {
        if (!forceRescan) {
            FleetDeviceMatrixStore.loadValid(context)?.let { cached ->
                return BuildResult(
                    root = cached,
                    scanDurationMs = cached.optJSONObject(FleetDeviceMatrix.KEY_SCAN_META)?.optLong("scanDurationMs") ?: 0L,
                    cameraCount = FleetDeviceMatrix.cameraCount(cached),
                    degraded = cached.optJSONObject(FleetDeviceMatrix.KEY_APPENDIX)?.optJSONObject("shallowCache")
                        ?.optBoolean("degraded", false)
                        ?: false,
                )
            }
        }
        val built = buildQuick(context)
        FleetDeviceMatrixStore.saveWithArtifacts(context, CameraCapabilityCatalogBuilder.attachTo(built.root), rotatePreviousToHistory = forceRescan)
        if (forceRescan) {
            MediaCodecCapabilityProbe.invalidateAndReprobe()
        }
        Log.i(
            TAG,
            "scanTier=quick cameras=${built.cameraCount} degraded=${built.degraded} ms=${built.scanDurationMs}",
        )
        return built
    }

    /**
     * Full scan + persist; rotates current matrix to history before write.
     */
    suspend fun buildFullAndSave(
        context: Context,
        onProgress: suspend (String) -> Unit = {},
    ): BuildResult {
        val previous = FleetDeviceMatrixStore.matrixFile(context).takeIf { it.exists() }?.let {
            runCatching { JSONObject(it.readText()) }.getOrNull()
        }
        val built = buildFull(context, onProgress)
        FleetDeviceMatrixStore.saveWithArtifacts(context, CameraCapabilityCatalogBuilder.attachTo(built.root), rotatePreviousToHistory = true)
        MediaCodecCapabilityProbe.invalidateAndReprobe()
        val diffSummary = built.diff?.summaryLines?.joinToString("; ") ?: "none"
        Log.i(
            TAG,
            "scanTier=full cameras=${built.cameraCount} degraded=${built.degraded} ms=${built.scanDurationMs} diff=$diffSummary",
        )
        if (built.diff?.hasChanges == true) {
            Log.i(TAG, "diffDetail changedCameras=${built.diff.changedCameraIds}")
        }
        return built.copy(diff = built.diff ?: FleetDeviceMatrixDiff.diff(previous, built.root))
    }

    private fun buildProductJson(
        context: Context,
        fleetSnap: FleetProfilesSnapshot,
        focalEntries: List<dev.pointandshoot.FleetCameraStartupScan.SlotEntry>,
        deviceGates: DeviceFeatureGates.Slice,
    ): JSONObject {
        val cm = context.getSystemService(android.content.Context.CAMERA_SERVICE) as android.hardware.camera2.CameraManager
        val ids = cm.cameraIdList.sorted()
        return JSONObject().apply {
            put(
                "focalSlots",
                JSONArray().apply {
                    focalEntries.forEach { e ->
                        put(
                            JSONObject().apply {
                                put("cameraId", e.cameraId)
                                put("focalMm35", e.focalMm35)
                                put("megapixels", e.megapixels)
                                put("grayscaled", e.grayscaled)
                            },
                        )
                    }
                },
            )
            put("focalRow", FleetFocalRowProductBuilder.build(context, ids, focalEntries))
            put("concurrencyGates", deviceGates.toJson())
            put("fleetProfiles", fleetSnap.toJson())
            put(
                "osFlavor",
                JSONObject().apply {
                    put("manufacturer", android.os.Build.MANUFACTURER)
                    put("brand", android.os.Build.BRAND)
                    put("sdkInt", android.os.Build.VERSION.SDK_INT)
                },
            )
        }
    }

    private fun scanMetaQuick(context: Context, durationMs: Long): JSONObject =
        baseScanMeta(context, FleetDeviceMatrix.ScanTier.QUICK, durationMs)

    private fun scanMetaFull(context: Context, durationMs: Long): JSONObject =
        baseScanMeta(context, FleetDeviceMatrix.ScanTier.FULL, durationMs)

    private fun baseScanMeta(context: Context, tier: FleetDeviceMatrix.ScanTier, durationMs: Long): JSONObject {
        val mpc =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                Build.VERSION.MEDIA_PERFORMANCE_CLASS
            } else {
                null
            }
        return FleetDeviceMatrix.scanMeta(
            scanTier = tier,
            appVersionCode = FleetDeviceMatrixStore.currentVersionCode(context),
            sdkInt = Build.VERSION.SDK_INT,
            securityPatch = Build.VERSION.SECURITY_PATCH,
            fingerprintSha256Prefix = FleetDeviceMatrixStore.liveFingerprintPrefix(),
            scanDurationMs = durationMs,
            mediaPerformanceClass = positiveOrNull(mpc),
            firstApiLevel = deviceInitialSdkInt(),
            vendorApiLevel = null,
        )
    }

    private fun positiveOrNull(value: Int?): Int? = value?.takeIf { it > 0 }

    /** API 34+ when present on the device SDK stub. */
    private fun deviceInitialSdkInt(): Int? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return null
        return runCatching {
            Build.VERSION::class.java.getField("DEVICE_INITIAL_SDK_INT").getInt(null)
        }.getOrNull()?.takeIf { it > 0 }
    }

    private fun complianceRollup(context: Context, meta: JSONObject): JSONObject {
        val mpc = meta.opt("mediaPerformanceClass")
        return JSONObject().apply {
            put("note", "informational — not a shipping gate (16.12)")
            put("mediaPerformanceClass", mpc)
            put("sdkInt", meta.optInt("sdkInt"))
            put("firstApiLevel", meta.opt("firstApiLevel"))
            put("vendorApiLevel", meta.opt("vendorApiLevel"))
        }
    }

    private fun assembleRoot(
        meta: JSONObject,
        cameras: JSONArray,
        product: JSONObject,
        cameraX: Any,
        runtimeProbes: Any,
        appendix: JSONObject,
        complianceRollup: JSONObject? = null,
        encoder: JSONObject? = null,
    ): JSONObject =
        JSONObject().apply {
            put(FleetDeviceMatrix.KEY_SCHEMA_VERSION, FleetDeviceMatrix.SCHEMA_VERSION)
            put(FleetDeviceMatrix.KEY_SCAN_META, meta)
            put(FleetDeviceMatrix.KEY_DEVICE, FleetDeviceMatrix.defaultDeviceBlock())
            put(FleetDeviceMatrix.KEY_CAMERAS, cameras)
            put(FleetDeviceMatrix.KEY_PRODUCT, product)
            put(FleetDeviceMatrix.KEY_CAMERA_X, cameraX)
            put(FleetDeviceMatrix.KEY_RUNTIME_PROBES, runtimeProbes)
            put(FleetDeviceMatrix.KEY_COMPLIANCE_ROLLUP, complianceRollup ?: JSONObject.NULL)
            put(FleetDeviceMatrix.KEY_ENCODER, encoder ?: JSONObject.NULL)
            put(FleetDeviceMatrix.KEY_APPENDIX, appendix)
        }

    private fun camerasById(arr: JSONArray?): Map<String, JSONObject> {
        if (arr == null) return emptyMap()
        val out = linkedMapOf<String, JSONObject>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            out[o.optString("cameraId")] = o
        }
        return out
    }

    private fun sessionSummary(sessionRoot: JSONObject): JSONObject {
        val cams = sessionRoot.optJSONArray("cameras") ?: JSONArray()
        val arr = JSONArray()
        for (i in 0 until cams.length()) {
            val c = cams.optJSONObject(i) ?: continue
            arr.put(
                JSONObject().apply {
                    put("cameraId", c.optString("cameraId"))
                    put("openCameraMs", c.opt("openCameraMs"))
                    put("regular1080Ok", SessionMatrixProbeCore.sessionTestSupported(c, "regular_1920x1080"))
                    put("highSpeedOk", SessionMatrixProbeCore.highSpeedSessionOk(c))
                },
            )
        }
        return JSONObject().apply {
            put("cameras", arr)
        }
    }
}
