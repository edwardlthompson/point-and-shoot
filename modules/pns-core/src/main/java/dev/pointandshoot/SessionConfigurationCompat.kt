package dev.pointandshoot

import android.annotation.SuppressLint
import android.graphics.ImageFormat
import android.graphics.SurfaceTexture
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.params.InputConfiguration
import android.hardware.camera2.params.OutputConfiguration
import android.hardware.camera2.params.SessionConfiguration
import android.media.ImageReader
import android.os.Build
import android.util.Log
import android.util.Size
import android.view.Surface
import java.util.concurrent.Executor

private const val HDR_SESS_LOG = "PNS.HdrDcgSession"

/**
 * Prefer API 35+ list constructor; below that use reflection on [SessionConfiguration.Builder] resolved via
 * [Class.getDeclaredClasses] (avoids Class.forName inner-class lookups that fail on some Android 16 builds).
 */
@SuppressLint("NewApi")
private fun buildSessionConfigurationFromOutputs(
    sessionType: Int,
    outputs: List<OutputConfiguration>,
): SessionConfiguration {
    if (Build.VERSION.SDK_INT >= 35) {
        return SessionConfiguration(sessionType, outputs.toMutableList())
    }
    val builderClass = SessionConfiguration::class.java.declaredClasses.firstOrNull { it.simpleName == "Builder" }
        ?: error("SessionConfiguration.Builder missing from runtime")
    val builder = builderClass.getConstructor(Int::class.javaPrimitiveType).newInstance(sessionType)
    val add = builderClass.getMethod("addOutputConfiguration", OutputConfiguration::class.java)
    for (oc in outputs) {
        add.invoke(builder, oc)
    }
    return builderClass.getMethod("build").invoke(builder) as SessionConfiguration
}

fun buildSessionConfigurationCompat(
    sessionType: Int,
    outputs: List<OutputConfiguration>,
): SessionConfiguration {
    return buildSessionConfigurationFromOutputs(sessionType, outputs)
}

/**
 * REGULAR (or other) session with [InputConfiguration] for YUV/private reprocess probe (Phase 5).
 *
 * API 35+: use [SessionConfiguration] two-arg constructor plus [SessionConfiguration.setInputConfiguration];
 * Builder is not reliably visible to reflection on some vendor builds (nested class stripped).
 * API 31-34: reflection on Builder (executor required for that path; unused on API 35+ for support checks).
 */
@SuppressLint("NewApi")
fun buildSessionConfigurationWithInput(
    sessionType: Int,
    outputs: List<OutputConfiguration>,
    input: InputConfiguration,
    executor: Executor,
): SessionConfiguration {
    check(Build.VERSION.SDK_INT >= 31) {
        "SessionConfiguration + InputConfiguration requires API 31+"
    }
    if (Build.VERSION.SDK_INT >= 35) {
        return SessionConfiguration(sessionType, outputs.toMutableList()).apply {
            setInputConfiguration(input)
        }
    }
    val builderClass = SessionConfiguration::class.java.declaredClasses.firstOrNull { it.simpleName == "Builder" }
        ?: error("SessionConfiguration.Builder missing from runtime")
    val builder = builderClass.getConstructor(Int::class.javaPrimitiveType).newInstance(sessionType)
    val add = builderClass.getMethod("addOutputConfiguration", OutputConfiguration::class.java)
    for (oc in outputs) {
        add.invoke(builder, oc)
    }
    builderClass.getMethod("setInputConfiguration", InputConfiguration::class.java).invoke(builder, input)
    builderClass.getMethod("setExecutor", Executor::class.java).invoke(builder, executor)
    return builderClass.getMethod("build").invoke(builder) as SessionConfiguration
}

/**
 * Preview-sized surface; optional [dynamicRangeProfile] for API 33+ ([OutputConfiguration.setDynamicRangeProfile]).
 */
fun isSessionSupportedWithDynamicRange(
    device: CameraDevice,
    sessionType: Int,
    size: Size,
    dynamicRangeProfile: Long?,
): Boolean {
    val st = SurfaceTexture(0)
    val surf = Surface(st)
    return try {
        st.setDefaultBufferSize(size.width, size.height)
        val oc = OutputConfiguration(surf)
        if (Build.VERSION.SDK_INT >= 33 && dynamicRangeProfile != null) {
            runCatching { oc.setDynamicRangeProfile(dynamicRangeProfile) }
        }
        val config = buildSessionConfigurationCompat(sessionType, listOf(oc))
        device.isSessionConfigurationSupported(config)
    } catch (e: Throwable) {
        Log.w(HDR_SESS_LOG, "isSessionSupportedWithDynamicRange: ${e.message}")
        false
    } finally {
        runCatching { surf.release() }
        runCatching { st.release() }
    }
}

/**
 * Same as [isSessionSupportedWithDynamicRange] but binds a [ImageReader] surface for [imageFormat]
 * (e.g. [ImageFormat.YUV_420_888]) — Phase 4 ten-bit / processing pipeline spots.
 */
@SuppressLint("NewApi")
fun isSessionSupportedWithDynamicRangeImageReader(
    device: CameraDevice,
    sessionType: Int,
    size: Size,
    imageFormat: Int,
    dynamicRangeProfile: Long?,
): Boolean {
    val reader = ImageReader.newInstance(size.width, size.height, imageFormat, 1)
    val surf = reader.surface
    return try {
        val oc = OutputConfiguration(surf)
        if (Build.VERSION.SDK_INT >= 33 && dynamicRangeProfile != null) {
            runCatching { oc.setDynamicRangeProfile(dynamicRangeProfile) }
        }
        val config = buildSessionConfigurationCompat(sessionType, listOf(oc))
        device.isSessionConfigurationSupported(config)
    } catch (e: Throwable) {
        Log.w(HDR_SESS_LOG, "isSessionSupportedWithDynamicRangeImageReader: ${e.message}")
        false
    } finally {
        runCatching { reader.close() }
    }
}

/**
 * Full [outputSurfaces] list (preview first) with [dynamicRangeProfile] on index **0** only —
 * matches [RawHdrExclusivityProbeScreen] preview+RAW probe shape for product preview sessions.
 */
@SuppressLint("NewApi")
fun isMultiOutputSessionSupportedWithDynamicRangeOnPreview(
    device: CameraDevice,
    outputSurfaces: List<Surface>,
    dynamicRangeProfile: Long,
): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU || outputSurfaces.isEmpty()) return false
    return try {
        val configs =
            outputSurfaces.mapIndexed { index, surface ->
                OutputConfiguration(surface).apply {
                    if (index == 0) {
                        runCatching { setDynamicRangeProfile(dynamicRangeProfile) }
                            .onFailure { e ->
                                Log.w(HDR_SESS_LOG, "setDynamicRangeProfile idx=0: ${e.message}")
                            }
                    }
                }
            }
        val sessionConfig = buildSessionConfigurationCompat(SessionConfiguration.SESSION_REGULAR, configs)
        device.isSessionConfigurationSupported(sessionConfig)
    } catch (e: Throwable) {
        Log.w(HDR_SESS_LOG, "multiOutputDynamicRangeOnPreview: ${e.message}")
        false
    }
}
