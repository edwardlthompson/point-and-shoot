package dev.pointandshoot.fleet

import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.input.InputManager
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import android.view.InputDevice
import android.view.KeyEvent
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Fleet matrix slices for camera-launch intents and hardware button inventory (hardware shutter plan).
 */
object ProductHardwareLaunchScan {
    const val TAG = FleetDeviceMatrixBuilder.TAG
    const val HARDWARE_KEY_PROBE_FILENAME = "HARDWARE_KEY_PROBE_LATEST.json"
    const val HARDWARE_KEY_PROBE_MD = "HARDWARE_KEY_PROBE_LATEST.md"

    private const val PNS_PACKAGE = "dev.pointandshoot"

    private val INPUT_HEURISTIC = Regex("""camera|shutter|gpio|key|button|side|alert|shortcut""", RegexOption.IGNORE_CASE)

    private val KNOWN_SHUTTER_KEY_CODES: List<Pair<Int, String>> =
        listOf(
            KeyEvent.KEYCODE_CAMERA to "KEYCODE_CAMERA",
            KeyEvent.KEYCODE_FOCUS to "KEYCODE_FOCUS",
            KeyEvent.KEYCODE_VOLUME_UP to "KEYCODE_VOLUME_UP",
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE to "KEYCODE_MEDIA_PLAY_PAUSE",
        )

    private val STANDARD_EXCLUDED_PROGRAMMABLE =
        setOf(
            KeyEvent.KEYCODE_CAMERA,
            KeyEvent.KEYCODE_FOCUS,
            KeyEvent.KEYCODE_VOLUME_UP,
            KeyEvent.KEYCODE_VOLUME_DOWN,
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
            KeyEvent.KEYCODE_HEADSETHOOK,
        )

    fun scanLaunchIntents(context: Context): JSONObject {
        val app = context.applicationContext
        val pm = app.packageManager
        val stillImageCamera = scanIntentAction(pm, MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA)
        val stillImageCameraSecure = scanIntentAction(pm, MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA_SECURE)
        val videoCamera = scanIntentAction(pm, MediaStore.INTENT_ACTION_VIDEO_CAMERA)
        val imageCapture = scanIntentAction(pm, MediaStore.ACTION_IMAGE_CAPTURE)
        val roleHolder = readDefaultCameraRoleHolder(app)
        val pnsIsDefault = roleHolder == PNS_PACKAGE
        listOf(stillImageCamera, stillImageCameraSecure, videoCamera, imageCapture).forEach { slice ->
            slice.put("defaultRoleHolder", roleHolder ?: JSONObject.NULL)
            slice.put("pnsIsDefaultRole", pnsIsDefault)
        }
        val handlerCount = stillImageCamera.optInt("handlerCount", 0)
        Log.i(TAG, "hardwareLaunch scan handlers=$handlerCount roleHolder=${roleHolder ?: "null"} pnsDefault=$pnsIsDefault")
        return JSONObject().apply {
            put("stillImageCamera", stillImageCamera)
            put("stillImageCameraSecure", stillImageCameraSecure)
            put("videoCamera", videoCamera)
            put("imageCapture", imageCapture)
        }
    }

    fun scanHardwareButtons(context: Context): JSONObject {
        val app = context.applicationContext
        val inputDevices = enumerateInputDevices(app)
        val dedicatedLikely =
            jsonArrayAny(inputDevices) { it.optBoolean("heuristicMatch", false) } ||
                jsonArrayAny(inputDevices) {
                    it.optString("name").contains("gpio-keys", ignoreCase = true)
                }
        val programmableLikely =
            jsonArrayCount(inputDevices) { it.optBoolean("heuristicMatch", false) } >= 2 ||
                loadInteractiveProbe(app)?.optJSONArray("distinctKeyCodes")?.let { arr ->
                    programmableKeyCodesFromDistinct(arr).isNotEmpty()
                } == true
        val out =
            JSONObject().apply {
                put("knownShutterKeyCodes", knownShutterKeyCodesJson())
                put("inputDevices", inputDevices)
                put("dedicatedCameraKeyLikely", dedicatedLikely)
                put("programmableButtonLikely", programmableLikely)
                put("scanMethod", "static")
            }
        mergeInteractiveProbe(app, out)
        Log.i(
            TAG,
            "hardwareButtons scan inputDevices=${inputDevices.length()} dedicatedLikely=$dedicatedLikely programmableLikely=$programmableLikely scanMethod=${out.optString("scanMethod")}",
        )
        return out
    }

    fun captureInputDevicesRedacted(): String? =
        runCatching {
            val proc = Runtime.getRuntime().exec(arrayOf("dumpsys", "input"))
            val raw = proc.inputStream.bufferedReader().readText()
            proc.waitFor()
            FleetHalAppendix.redact(raw).take(80_000)
        }.onFailure { e ->
            Log.w(TAG, "dumpsys input capture failed: ${e.message}")
        }.getOrNull()

    fun hasDedicatedCameraKeyEvidence(root: JSONObject?): Boolean {
        if (root == null) return false
        val buttons = root.optJSONObject(FleetDeviceMatrix.KEY_PRODUCT)?.optJSONObject("hardwareButtons") ?: return false
        val probe = buttons.optJSONObject("interactiveProbe")
        if (probe?.optBoolean("cameraKeyConfirmed", false) == true) return true
        if (probe?.optBoolean("focusKeyConfirmed", false) == true) return true
        return buttons.optBoolean("dedicatedCameraKeyLikely", false)
    }

    fun extraShutterKeyCodes(root: JSONObject?): Set<Int> {
        val arr =
            root?.optJSONObject(FleetDeviceMatrix.KEY_PRODUCT)
                ?.optJSONObject("hardwareButtons")
                ?.optJSONObject("interactiveProbe")
                ?.optJSONArray("distinctKeyCodes")
                ?: return emptySet()
        return programmableKeyCodesFromDistinct(arr).toSet()
    }

    fun saveInteractiveProbe(context: Context, probeJson: JSONObject, markdown: String) {
        val dir = context.applicationContext.filesDir
        File(dir, HARDWARE_KEY_PROBE_FILENAME).writeText(probeJson.toString(2), Charsets.UTF_8)
        File(dir, HARDWARE_KEY_PROBE_MD).writeText(markdown, Charsets.UTF_8)
    }

    fun loadInteractiveProbe(context: Context): JSONObject? =
        runCatching {
            val f = File(context.applicationContext.filesDir, HARDWARE_KEY_PROBE_FILENAME)
            if (!f.exists()) return@runCatching null
            JSONObject(f.readText())
        }.getOrNull()

    fun buildInteractiveProbeFromEvents(events: List<HardwareKeyProbeEvent>): JSONObject {
        val distinct = events.map { it.keyCode }.distinct().sorted()
        val cameraConfirmed = distinct.contains(KeyEvent.KEYCODE_CAMERA)
        val focusConfirmed = distinct.contains(KeyEvent.KEYCODE_FOCUS)
        val eventsArr =
            JSONArray().apply {
                events.forEach { e ->
                    put(
                        JSONObject().apply {
                            put("keyCode", e.keyCode)
                            put("scanCode", e.scanCode)
                            put("action", e.actionLabel)
                            put("source", e.source)
                            put("repeatCount", e.repeatCount)
                            put("deviceId", e.deviceId)
                        },
                    )
                }
            }
        return JSONObject().apply {
            put("generatedAtEpochMs", System.currentTimeMillis())
            put("events", eventsArr)
            put("distinctKeyCodes", JSONArray(distinct))
            put("cameraKeyConfirmed", cameraConfirmed)
            put("focusKeyConfirmed", focusConfirmed)
            put(
                "programmableKeyCodes",
                JSONArray(programmableKeyCodesFromDistinct(JSONArray(distinct))),
            )
        }
    }

    data class HardwareKeyProbeEvent(
        val keyCode: Int,
        val scanCode: Int,
        val actionLabel: String,
        val source: Int,
        val repeatCount: Int,
        val deviceId: Int,
    )

    private fun scanIntentAction(pm: PackageManager, action: String): JSONObject {
        val intent = Intent(action)
        val matches =
            runCatching {
                pm.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)
            }.getOrElse { emptyList() }
        val handlers =
            JSONArray().apply {
                matches.forEach { ri ->
                    val ai = ri.activityInfo
                    put(
                        JSONObject().apply {
                            put("package", ai.packageName)
                            put("activity", ai.name)
                            put("isPns", ai.packageName == PNS_PACKAGE)
                        },
                    )
                }
            }
        val pnsRegistered =
            (0 until handlers.length()).any { i ->
                handlers.optJSONObject(i)?.optBoolean("isPns") == true
            }
        return JSONObject().apply {
            put("action", action)
            put("resolvable", matches.isNotEmpty())
            put("handlerCount", matches.size)
            put("handlers", handlers)
            put("pnsRegistered", pnsRegistered)
        }
    }

    private fun readDefaultCameraRoleHolder(context: Context): String? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return null
        return runCatching {
            val rm = context.getSystemService(RoleManager::class.java) ?: return@runCatching null
            val method = RoleManager::class.java.getMethod("getRoleHolders", String::class.java)
            @Suppress("UNCHECKED_CAST")
            (method.invoke(rm, "android.app.role.CAMERA") as? List<*>)?.firstOrNull() as? String
        }.getOrNull()
    }

    private fun jsonArrayAny(arr: JSONArray, predicate: (JSONObject) -> Boolean): Boolean {
        for (i in 0 until arr.length()) {
            val obj = arr.optJSONObject(i) ?: continue
            if (predicate(obj)) return true
        }
        return false
    }

    private fun jsonArrayCount(arr: JSONArray, predicate: (JSONObject) -> Boolean): Int {
        var count = 0
        for (i in 0 until arr.length()) {
            val obj = arr.optJSONObject(i) ?: continue
            if (predicate(obj)) count++
        }
        return count
    }

    private fun enumerateInputDevices(context: Context): JSONArray {
        val im = context.getSystemService(Context.INPUT_SERVICE) as InputManager
        val arr = JSONArray()
        for (id in im.inputDeviceIds) {
            val dev = InputDevice.getDevice(id) ?: continue
            val name = dev.name ?: "unknown"
            val descriptor = dev.descriptor ?: ""
            val heuristic = INPUT_HEURISTIC.containsMatchIn(name) || INPUT_HEURISTIC.containsMatchIn(descriptor)
            arr.put(
                JSONObject().apply {
                    put("deviceId", id)
                    put("name", name)
                    put("descriptor", descriptor)
                    put("vendorId", dev.vendorId)
                    put("productId", dev.productId)
                    put("sources", dev.sources)
                    put("keyboardType", dev.keyboardType)
                    put("heuristicMatch", heuristic)
                    put(
                        "isExternal",
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) dev.isExternal else JSONObject.NULL,
                    )
                },
            )
        }
        return arr
    }

    private fun knownShutterKeyCodesJson(): JSONArray =
        JSONArray().apply {
            KNOWN_SHUTTER_KEY_CODES.forEach { (code, name) ->
                put(
                    JSONObject().apply {
                        put("keyCode", code)
                        put("name", name)
                    },
                )
            }
        }

    private fun mergeInteractiveProbe(context: Context, out: JSONObject) {
        val probe = loadInteractiveProbe(context) ?: return
        out.put("interactiveProbe", probe)
        out.put("scanMethod", "static+interactive")
    }

    private fun programmableKeyCodesFromDistinct(distinct: JSONArray): List<Int> {
        val out = mutableListOf<Int>()
        for (i in 0 until distinct.length()) {
            val code = distinct.optInt(i, Int.MIN_VALUE)
            if (code == Int.MIN_VALUE) continue
            if (code in STANDARD_EXCLUDED_PROGRAMMABLE) continue
            out += code
        }
        return out
    }
}
