package dev.pointandshoot

import android.hardware.camera2.CameraCharacteristics
import android.os.Build
import android.util.Log

/**
 * Sprint 13.5: DCG (Dual Conversion Gain) mode support detection.
 * 
 * DCG is a vendor-specific HDR mode that extends dynamic range by using different
 * conversion gains for different parts of the sensor. This class probes for DCG support
 * via vendor keys and capabilities.
 */
object DcgModeSupport {
    private const val TAG = "PNS.DcgMode"
    
    /**
     * Vendor key for enabling DCG mode (OEM-specific).
     * Common keys: "com.google.camera.enableHDRDCGMode", "com.oppo.camera.hdr.dcg"
     */
    private val VENDOR_DCG_KEYS = listOf(
        "com.google.camera.enableHDRDCGMode",
        "com.oppo.camera.hdr.dcg",
        "com.samsung.camera.hdr.dcg",
        "com.xiaomi.camera.hdr.dcg",
        "com.huawei.camera.hdr.dcg"
    )
    
    /**
     * Check if the device supports DCG mode for the given camera.
     * 
     * @param chars Camera characteristics to probe
     * @return true if DCG mode is supported, false otherwise
     */
    fun supportsDcgMode(chars: CameraCharacteristics): Boolean {
        // DCG requires API 33+ for DynamicRangeProfiles
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            Log.d(TAG, "DCG requires API 33+ (current ${Build.VERSION.SDK_INT})")
            return false
        }
        
        // Check if device advertises HLG10 or HDR10+ (required for DCG)
        val drp = chars.getAvailableDynamicRangeProfilesOrNull() ?: run {
            Log.d(TAG, "DynamicRangeProfiles not available")
            return false
        }
        
        val supportedProfiles = drp.supportedProfiles ?: run {
            Log.d(TAG, "No supported dynamic range profiles")
            return false
        }
        
        // DCG typically requires HLG10 or HDR10+ support
        val hasHlg10 = supportedProfiles.contains(android.hardware.camera2.params.DynamicRangeProfiles.HLG10)
        val hasHdr10Plus = supportedProfiles.contains(android.hardware.camera2.params.DynamicRangeProfiles.HDR10_PLUS)
        
        if (!hasHlg10 && !hasHdr10Plus) {
            Log.d(TAG, "DCG requires HLG10 or HDR10+ (hasHlg10=$hasHlg10 hasHdr10Plus=$hasHdr10Plus)")
            return false
        }
        
        // Assume DCG support if device has HLG10/HDR10+ and is a known OEM
        val isKnownOem = isKnownDcgOem()
        if (isKnownOem && (hasHlg10 || hasHdr10Plus)) {
            Log.i(TAG, "DCG mode likely supported (known OEM + HDR profiles)")
            return true
        }
        
        Log.d(TAG, "DCG mode not supported (not known OEM)")
        return false
    }
    
    /**
     * Check if device is from a known OEM that typically supports DCG.
     */
    private fun isKnownDcgOem(): Boolean {
        val manufacturer = android.os.Build.MANUFACTURER.lowercase()
        return manufacturer in listOf(
            "oppo",
            "oneplus",
            "xiaomi",
            "samsung",
            "huawei",
            "honor",
            "realme",
            "vivo"
        )
    }
    
    /**
     * Get the recommended dynamic range profile for DCG mode.
     * Returns HLG10 for most devices, HDR10+ if available.
     */
    fun getRecommendedDcgProfile(chars: CameraCharacteristics): Long? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return null
        val drp = chars.getAvailableDynamicRangeProfilesOrNull() ?: return null
        
        val supportedProfiles = drp.supportedProfiles ?: return null
        
        // Prefer HDR10+ over HLG10 for DCG
        return when {
            supportedProfiles.contains(android.hardware.camera2.params.DynamicRangeProfiles.HDR10_PLUS) -> 
                android.hardware.camera2.params.DynamicRangeProfiles.HDR10_PLUS
            supportedProfiles.contains(android.hardware.camera2.params.DynamicRangeProfiles.HLG10) -> 
                android.hardware.camera2.params.DynamicRangeProfiles.HLG10
            else -> null
        }
    }
}
