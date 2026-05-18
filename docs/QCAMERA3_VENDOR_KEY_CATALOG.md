# QCamera3 Vendor Key Catalog - OnePlus 13 (CPH2655)

**Date:** 2026-05-17
**Device:** OnePlus 13 (CPH2655)
**Probe Method:** dumpsys media.camera
**API Level:** 36 (Android 16)

## Summary

This document catalogs all Qualcomm QCamera3 vendor keys discovered on the OnePlus 13 device for HFR and 10-bit video research. These keys are accessible via Camera2 API through `CameraCharacteristics.getAvailableSessionKeys()`, `getAvailableCaptureRequestKeys()`, `getAvailableCaptureResultKeys()`, and `getKeys()`.

## Key Findings for HFR and 10-bit Video

### Critical Keys for HFR (120fps)
- **org.codeaurora.qcamera3.platformCapabilities.EnableVSR** - Video Stabilization Rotation (may enable HFR-specific stabilization)
- **org.codeaurora.qcamera3.platformCapabilities.EnableVIULL** - Video ISP ULL (Ultra Low Latency - critical for HFR)
- **org.codeaurora.qcamera3.platformCapabilities.EnableAICameraHSR** - AI Camera High Speed Recording (direct HFR enable)

### Critical Keys for 10-bit/DCG Video
- **org.codeaurora.qcamera3.sessionParameters.EnableHDRDCGMode** - Dual Conversion Gain mode enable (int32[1])
- **org.codeaurora.qcamera3.sessionParameters.EnableQHDR** - Qualcomm HDR mode (int32[1])
- **org.codeaurora.qcamera3.sessionParameters.EnableAutoHDR** - Auto HDR mode (int32[1])
- **org.codeaurora.qcamera3.sessionParameters.HDRModePreference** - HDR mode preference (int32[1])
- **org.codeaurora.qcamera3.sessionParameters.numHDRexposure** - Number of HDR exposures (int32[1])
- **org.codeaurora.qcamera3.supportedHDRmodes.HDRModes** - Supported HDR modes (int32[1])
- **org.codeaurora.qcamera3.supportedHDRmodes.HDRDCGModes** - Supported DCG modes (int32[1])
- **org.codeaurora.qcamera3.available_video_hdr_modes.video_hdr_modes** - Available video HDR modes (int32[6])

### Other Session Parameters
- **org.codeaurora.qcamera3.sessionParameters.SnapshotHDRMode** - Snapshot HDR mode (int32[1])
- **org.codeaurora.qcamera3.sessionParameters.enableQLL** - QLL enable (int32[1])
- **org.codeaurora.qcamera3.sessionParameters.enableStatsVisualizer** - Stats visualizer enable (byte[1])
- **org.codeaurora.qcamera3.sessionParameters.EnableAFBracketing** - AF bracketing enable (int32[1])
- **org.codeaurora.qcamera3.sessionParameters.AICameraMode** - AI Camera mode (int32[1])
- **org.codeaurora.qcamera3.sessionParameters.EnableXCFAOptimization** - XCFA optimization enable (byte[1])
- **org.codeaurora.qcamera3.sessionParameters.ExtraPreviewMaxBuffers** - Extra preview max buffers (int32[1])
- **org.codeaurora.qcamera3.sessionParameters.EnableCinematicMode** - Cinematic mode enable (int32[1])

### Request Parameters
- **org.codeaurora.qcamera3.sharpness.strength** - Sharpness strength (int32[1])
- **org.codeaurora.qcamera3.ae_bracket.mode** - AE bracket mode (byte[1])
- **org.codeaurora.qcamera3.saturation.use_saturation** - Use saturation (int32[1])

### Characteristic Parameters (Read-only)
- **org.codeaurora.qcamera3.saturation.range** - Saturation range (int32[4])
- **org.codeaurora.qcamera3.shadingCorrection.enableShadingCorrection** - Shading correction enable (byte[1])
- **org.codeaurora.qcamera3.inSensorSHDRMode.inSensorSHDRMode** - In-sensor SHDR mode (byte[1])
- **org.codeaurora.qcamera3.exposure_metering.available_modes** - Available exposure metering modes (int32[3])
- **org.codeaurora.qcamera3.iso_exp_priority.iso_available_modes** - Available ISO priority modes (int32[8])
- **org.codeaurora.qcamera3.iso_exp_priority.exposure_time_range** - Exposure time range (int64[2])
- **org.codeaurora.qcamera3.sharpness.range** - Sharpness range (int32[2])
- **org.codeaurora.qcamera3.histogram.buckets** - Histogram buckets (int32[1])
- **org.codeaurora.qcamera3.histogram.max_count** - Histogram max count (int32[1])
- **org.codeaurora.qcamera3.instant_aec.instant_aec_available_modes** - Instant AEC available modes (int32[3])
- **org.codeaurora.qcamera3.manualWB.color_temperature_range** - Manual WB color temperature range (int32[2])
- **org.codeaurora.qcamera3.manualWB.gains_range** - Manual WB gains range (float[2])
- **org.codeaurora.qcamera3.available_video_hdr_modes.video_hdr_modes** - Available video HDR modes (int32[6])
- **org.codeaurora.qcamera3.platformCapabilities.IPEICACapabilities** - IPE/ICA capabilities (byte[8])
- **org.codeaurora.qcamera3.platformCapabilities.ExtendedMaxZoom** - Extended max zoom (float[1])
- **org.codeaurora.qcamera3.platformCapabilities.EnableVSR** - Enable VSR (int32[1])
- **org.codeaurora.qcamera3.platformCapabilities.EnableVIULL** - Enable VIULL (int32[1])
- **org.codeaurora.qcamera3.platformCapabilities.EnableAICameraHSR** - Enable AI Camera HSR (int32[1])
- **org.codeaurora.qcamera3.supportedHDRmodes.HDRModes** - Supported HDR modes (int32[1])
- **org.codeaurora.qcamera3.supportedHDRmodes.HDRDCGModes** - Supported DCG modes (int32[1])
- **org.codeaurora.qcamera3.stats.bsgc_available** - BSGC available (byte[1])
- **org.codeaurora.qcamera3.logicalCameraType.logical_camera_type** - Logical camera type (byte[1])

### Sensor Metadata
- **org.codeaurora.qcamera3.sensor_meta_data.EEPROMInformation** - EEPROM information (byte[13024])
- **org.codeaurora.qcamera3.sensor_meta_data.stereoCalibrationData** - Stereo calibration data (byte[1248])
- **org.codeaurora.qcamera3.sensor_meta_data.mountAngle** - Mount angle (int32[1])
- **org.codeaurora.qcamera3.sensor_meta_data.CSIPHYSlotInfo** - CSI PHY slot info (int32[1])
- **org.codeaurora.qcamera3.sensor_meta_data.cameraPosition** - Camera position (int32[1])
- **org.codeaurora.qcamera3.sensor_meta_data.sensorCaps** - Sensor capabilities (byte[248])

## Total Count

- **Session Parameters:** 13 keys
- **Request Parameters:** 3 keys
- **Characteristic Parameters:** 30+ keys
- **Total:** 46+ QCamera3 vendor keys discovered

## Next Steps for HFR (120fps) Research

1. **Test `EnableAICameraHSR`** - Set via SessionConfiguration.setSessionParameters (API 33+)
   - This key directly enables AI Camera High Speed Recording
   - May bypass OMX encoder limitations by using Qualcomm's HFR path

2. **Test `EnableVIULL`** - Set via SessionConfiguration.setSessionParameters
   - Ultra Low Latency mode may enable higher FPS recording
   - Could work with CameraConstrainedHighSpeedCaptureSession

3. **Test `EnableVSR`** - Set via SessionConfiguration.setSessionParameters
   - Video Stabilization Rotation may be required for HFR
   - Test both enabled and disabled states

4. **Monitor result keys** - Check if vendor key acceptance is reflected in result metadata
   - Look for HFR-specific result keys (may need deeper probe)

## Next Steps for 10-bit/DCG Video Research

1. **Test `EnableHDRDCGMode`** - Set via SessionConfiguration.setSessionParameters
   - This is the primary DCG enable key
   - Already partially implemented in DcgModeSupport.kt (via DynamicRangeProfiles)
   - Test direct vendor key setting as alternative to DynamicRangeProfiles

2. **Test `EnableQHDR` and `EnableAutoHDR`** - Set via SessionConfiguration.setSessionParameters
   - These may enable 10-bit HDR video recording
   - Test with different HDR mode preferences

3. **Test `HDRModePreference`** - Set via SessionConfiguration.setSessionParameters
   - May allow selecting specific HDR modes (HLG10, HDR10+, etc.)
   - Document available mode values

4. **Query `supportedHDRmodes.HDRDCGModes`** - Read characteristic to see supported DCG modes
   - This tells us which DCG modes the hardware supports
   - Use this to inform UI options

5. **Query `available_video_hdr_modes.video_hdr_modes`** - Read characteristic for video HDR support
   - May indicate 10-bit video capability
   - Test each mode for 10-bit output

## Implementation Recommendations

### For Sprint 13.3 (HFR 120fps)
1. Add `VendorKeyGuard` usage for `EnableAICameraHSR`, `EnableVIULL`, `EnableVSR`
2. Create root-gated toggle for "Qualcomm HFR Unlock" in Root Only drawer
3. Test setting these keys via SessionConfiguration.setSessionParameters
4. Verify if 120fps recording succeeds with vendor keys enabled
5. Monitor result keys for HFR activation confirmation

### For Sprint 13.2 (10-bit Video)
1. Add `VendorKeyGuard` usage for `EnableHDRDCGMode`, `EnableQHDR`, `EnableAutoHDR`
2. Test direct vendor key setting as alternative to DynamicRangeProfiles
3. Query `supportedHDRmodes.HDRDCGModes` and `available_video_hdr_modes.video_hdr_modes`
4. Create root-gated toggle for "Qualcomm 10-bit Unlock" in Root Only drawer
5. Test if vendor keys enable 10-bit HEVC encoding bypassing OMX limitations

## Root Property Research (Phase 2)

The following vendor properties should be probed via `getprop` with root:
- `persist.vendor.camera.hfr.mode`
- `persist.vendor.camera.video.hdr.enable`
- `persist.vendor.camera.enc.hfr`
- `vendor.camera.hfr.support`
- `ro.vendor.camera.hfr.support` (read-only)

## References

- VendorKeyGuard.kt for vendor key infrastructure
- PreviewEngineScreen.kt for session parameter usage examples (EnableAFBracketing, macro.closeup.enable)
- BUILD_PLAN.md Milestone 13 for HFR and 10-bit video requirements
- RootCapability.kt for root-based property access infrastructure
