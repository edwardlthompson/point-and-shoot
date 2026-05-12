# Camera2 - keys and core APIs (reference)

**Generated:** 2026-05-12 00:56:56 UTC  
**Source:** platforms/android-36/android.jar (resolved from local.properties sdk.dir) - API **36** (matches app compileSdk in app/build.gradle.kts).  
**Regenerate:** ``.\scripts\pns_gen_camera2_keys_reference.ps1`` from repo root.

---

## How to use this file

- **Java / Kotlin field names** below (`CONTROL_AE_MODE`, `SENSOR_INFO_TIMESTAMP_SOURCE`, ...) are the `public static final` **Key** identifiers on `CameraCharacteristics`, `CaptureRequest`, and `CaptureResult`. At runtime, each **Key** canonical string is `key.name` (e.g. `android.control.aeMode`) - use that when matching **vendor** keys or probe markdown.
- **Per-device truth** (OEM vendor tags, logical/physical map, stream sizes) is still only in **`CameraCharacteristics`** / **`availableCaptureRequestKeys`** on hardware - keep using **`pns_ae_highlight_probe_adb.ps1`** / exported **`PROBE_EXPORT_LATEST.md`** for fleet-specific names beyond this API-level list.
- **Face / eye / metering:** see **`## Face / eye tracking reference (Point & Shoot)`** at the end (filtered key subset + maintained appendix from ``docs/camera2_reference_face_eye_appendix.md``).
- **Official docs:** [android.hardware.camera2](https://developer.android.com/reference/android/hardware/camera2/package-summary)

---

## Summary counts

| Class | Key field count |
|-------|---------------|
| `CameraCharacteristics` | 125 |
| `CaptureRequest` | 68 |
| `CaptureResult` | 102 |

---

## CameraCharacteristics.Keys (static metadata)

- `AUTOMOTIVE_LENS_FACING`
- `AUTOMOTIVE_LOCATION`
- `COLOR_CORRECTION_AVAILABLE_ABERRATION_MODES`
- `COLOR_CORRECTION_AVAILABLE_MODES`
- `COLOR_CORRECTION_COLOR_TEMPERATURE_RANGE`
- `CONTROL_AE_AVAILABLE_ANTIBANDING_MODES`
- `CONTROL_AE_AVAILABLE_MODES`
- `CONTROL_AE_AVAILABLE_PRIORITY_MODES`
- `CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES`
- `CONTROL_AE_COMPENSATION_RANGE`
- `CONTROL_AE_COMPENSATION_STEP`
- `CONTROL_AE_LOCK_AVAILABLE`
- `CONTROL_AF_AVAILABLE_MODES`
- `CONTROL_AUTOFRAMING_AVAILABLE`
- `CONTROL_AVAILABLE_EFFECTS`
- `CONTROL_AVAILABLE_EXTENDED_SCENE_MODE_CAPABILITIES`
- `CONTROL_AVAILABLE_MODES`
- `CONTROL_AVAILABLE_SCENE_MODES`
- `CONTROL_AVAILABLE_SETTINGS_OVERRIDES`
- `CONTROL_AVAILABLE_VIDEO_STABILIZATION_MODES`
- `CONTROL_AWB_AVAILABLE_MODES`
- `CONTROL_AWB_LOCK_AVAILABLE`
- `CONTROL_LOW_LIGHT_BOOST_INFO_LUMINANCE_RANGE`
- `CONTROL_MAX_REGIONS_AE`
- `CONTROL_MAX_REGIONS_AF`
- `CONTROL_MAX_REGIONS_AWB`
- `CONTROL_POST_RAW_SENSITIVITY_BOOST_RANGE`
- `CONTROL_ZOOM_RATIO_RANGE`
- `DEPTH_DEPTH_IS_EXCLUSIVE`
- `DISTORTION_CORRECTION_AVAILABLE_MODES`
- `EDGE_AVAILABLE_EDGE_MODES`
- `FLASH_INFO_AVAILABLE`
- `FLASH_INFO_STRENGTH_DEFAULT_LEVEL`
- `FLASH_INFO_STRENGTH_MAXIMUM_LEVEL`
- `FLASH_SINGLE_STRENGTH_DEFAULT_LEVEL`
- `FLASH_SINGLE_STRENGTH_MAX_LEVEL`
- `FLASH_TORCH_STRENGTH_DEFAULT_LEVEL`
- `FLASH_TORCH_STRENGTH_MAX_LEVEL`
- `HOT_PIXEL_AVAILABLE_HOT_PIXEL_MODES`
- `INFO_DEVICE_STATE_SENSOR_ORIENTATION_MAP`
- `INFO_SESSION_CONFIGURATION_QUERY_VERSION`
- `INFO_SUPPORTED_HARDWARE_LEVEL`
- `INFO_VERSION`
- `JPEG_AVAILABLE_THUMBNAIL_SIZES`
- `LENS_DISTORTION`
- `LENS_DISTORTION_MAXIMUM_RESOLUTION`
- `LENS_FACING`
- `LENS_INFO_AVAILABLE_APERTURES`
- `LENS_INFO_AVAILABLE_FILTER_DENSITIES`
- `LENS_INFO_AVAILABLE_FOCAL_LENGTHS`
- `LENS_INFO_AVAILABLE_OPTICAL_STABILIZATION`
- `LENS_INFO_FOCUS_DISTANCE_CALIBRATION`
- `LENS_INFO_HYPERFOCAL_DISTANCE`
- `LENS_INFO_MINIMUM_FOCUS_DISTANCE`
- `LENS_INTRINSIC_CALIBRATION`
- `LENS_INTRINSIC_CALIBRATION_MAXIMUM_RESOLUTION`
- `LENS_POSE_REFERENCE`
- `LENS_POSE_ROTATION`
- `LENS_POSE_TRANSLATION`
- `LENS_RADIAL_DISTORTION`
- `LOGICAL_MULTI_CAMERA_SENSOR_SYNC_TYPE`
- `NOISE_REDUCTION_AVAILABLE_NOISE_REDUCTION_MODES`
- `REPROCESS_MAX_CAPTURE_STALL`
- `REQUEST_AVAILABLE_CAPABILITIES`
- `REQUEST_AVAILABLE_COLOR_SPACE_PROFILES`
- `REQUEST_AVAILABLE_DYNAMIC_RANGE_PROFILES`
- `REQUEST_MAX_NUM_INPUT_STREAMS`
- `REQUEST_MAX_NUM_OUTPUT_PROC`
- `REQUEST_MAX_NUM_OUTPUT_PROC_STALLING`
- `REQUEST_MAX_NUM_OUTPUT_RAW`
- `REQUEST_PARTIAL_RESULT_COUNT`
- `REQUEST_PIPELINE_MAX_DEPTH`
- `REQUEST_RECOMMENDED_TEN_BIT_DYNAMIC_RANGE_PROFILE`
- `SCALER_AVAILABLE_MAX_DIGITAL_ZOOM`
- `SCALER_AVAILABLE_ROTATE_AND_CROP_MODES`
- `SCALER_AVAILABLE_STREAM_USE_CASES`
- `SCALER_CROPPING_TYPE`
- `SCALER_DEFAULT_SECURE_IMAGE_SIZE`
- `SCALER_MANDATORY_CONCURRENT_STREAM_COMBINATIONS`
- `SCALER_MANDATORY_MAXIMUM_RESOLUTION_STREAM_COMBINATIONS`
- `SCALER_MANDATORY_PREVIEW_STABILIZATION_OUTPUT_STREAM_COMBINATIONS`
- `SCALER_MANDATORY_STREAM_COMBINATIONS`
- `SCALER_MANDATORY_TEN_BIT_OUTPUT_STREAM_COMBINATIONS`
- `SCALER_MANDATORY_USE_CASE_STREAM_COMBINATIONS`
- `SCALER_MULTI_RESOLUTION_STREAM_CONFIGURATION_MAP`
- `SCALER_STREAM_CONFIGURATION_MAP`
- `SCALER_STREAM_CONFIGURATION_MAP_MAXIMUM_RESOLUTION`
- `SENSOR_AVAILABLE_TEST_PATTERN_MODES`
- `SENSOR_BLACK_LEVEL_PATTERN`
- `SENSOR_CALIBRATION_TRANSFORM1`
- `SENSOR_CALIBRATION_TRANSFORM2`
- `SENSOR_COLOR_TRANSFORM1`
- `SENSOR_COLOR_TRANSFORM2`
- `SENSOR_FORWARD_MATRIX1`
- `SENSOR_FORWARD_MATRIX2`
- `SENSOR_INFO_ACTIVE_ARRAY_SIZE`
- `SENSOR_INFO_ACTIVE_ARRAY_SIZE_MAXIMUM_RESOLUTION`
- `SENSOR_INFO_BINNING_FACTOR`
- `SENSOR_INFO_COLOR_FILTER_ARRANGEMENT`
- `SENSOR_INFO_EXPOSURE_TIME_RANGE`
- `SENSOR_INFO_LENS_SHADING_APPLIED`
- `SENSOR_INFO_MAX_FRAME_DURATION`
- `SENSOR_INFO_PHYSICAL_SIZE`
- `SENSOR_INFO_PIXEL_ARRAY_SIZE`
- `SENSOR_INFO_PIXEL_ARRAY_SIZE_MAXIMUM_RESOLUTION`
- `SENSOR_INFO_PRE_CORRECTION_ACTIVE_ARRAY_SIZE`
- `SENSOR_INFO_PRE_CORRECTION_ACTIVE_ARRAY_SIZE_MAXIMUM_RESOLUTION`
- `SENSOR_INFO_SENSITIVITY_RANGE`
- `SENSOR_INFO_TIMESTAMP_SOURCE`
- `SENSOR_INFO_WHITE_LEVEL`
- `SENSOR_MAX_ANALOG_SENSITIVITY`
- `SENSOR_OPTICAL_BLACK_REGIONS`
- `SENSOR_ORIENTATION`
- `SENSOR_READOUT_TIMESTAMP`
- `SENSOR_REFERENCE_ILLUMINANT1`
- `SENSOR_REFERENCE_ILLUMINANT2`
- `SHADING_AVAILABLE_MODES`
- `STATISTICS_INFO_AVAILABLE_FACE_DETECT_MODES`
- `STATISTICS_INFO_AVAILABLE_HOT_PIXEL_MAP_MODES`
- `STATISTICS_INFO_AVAILABLE_LENS_SHADING_MAP_MODES`
- `STATISTICS_INFO_AVAILABLE_OIS_DATA_MODES`
- `STATISTICS_INFO_MAX_FACE_COUNT`
- `SYNC_MAX_LATENCY`
- `TONEMAP_AVAILABLE_TONE_MAP_MODES`
- `TONEMAP_MAX_CURVE_POINTS`

## CaptureRequest.Keys (requests & session parameters)

- `BLACK_LEVEL_LOCK`
- `COLOR_CORRECTION_ABERRATION_MODE`
- `COLOR_CORRECTION_COLOR_TEMPERATURE`
- `COLOR_CORRECTION_COLOR_TINT`
- `COLOR_CORRECTION_GAINS`
- `COLOR_CORRECTION_MODE`
- `COLOR_CORRECTION_TRANSFORM`
- `CONTROL_AE_ANTIBANDING_MODE`
- `CONTROL_AE_EXPOSURE_COMPENSATION`
- `CONTROL_AE_LOCK`
- `CONTROL_AE_MODE`
- `CONTROL_AE_PRECAPTURE_TRIGGER`
- `CONTROL_AE_PRIORITY_MODE`
- `CONTROL_AE_REGIONS`
- `CONTROL_AE_TARGET_FPS_RANGE`
- `CONTROL_AF_MODE`
- `CONTROL_AF_REGIONS`
- `CONTROL_AF_TRIGGER`
- `CONTROL_AUTOFRAMING`
- `CONTROL_AWB_LOCK`
- `CONTROL_AWB_MODE`
- `CONTROL_AWB_REGIONS`
- `CONTROL_CAPTURE_INTENT`
- `CONTROL_EFFECT_MODE`
- `CONTROL_ENABLE_ZSL`
- `CONTROL_EXTENDED_SCENE_MODE`
- `CONTROL_MODE`
- `CONTROL_POST_RAW_SENSITIVITY_BOOST`
- `CONTROL_SCENE_MODE`
- `CONTROL_SETTINGS_OVERRIDE`
- `CONTROL_VIDEO_STABILIZATION_MODE`
- `CONTROL_ZOOM_METHOD`
- `CONTROL_ZOOM_RATIO`
- `DISTORTION_CORRECTION_MODE`
- `EDGE_MODE`
- `EXTENSION_STRENGTH`
- `FLASH_MODE`
- `FLASH_STRENGTH_LEVEL`
- `HOT_PIXEL_MODE`
- `JPEG_GPS_LOCATION`
- `JPEG_ORIENTATION`
- `JPEG_QUALITY`
- `JPEG_THUMBNAIL_QUALITY`
- `JPEG_THUMBNAIL_SIZE`
- `LENS_APERTURE`
- `LENS_FILTER_DENSITY`
- `LENS_FOCAL_LENGTH`
- `LENS_FOCUS_DISTANCE`
- `LENS_OPTICAL_STABILIZATION_MODE`
- `NOISE_REDUCTION_MODE`
- `REPROCESS_EFFECTIVE_EXPOSURE_FACTOR`
- `SCALER_CROP_REGION`
- `SCALER_ROTATE_AND_CROP`
- `SENSOR_EXPOSURE_TIME`
- `SENSOR_FRAME_DURATION`
- `SENSOR_PIXEL_MODE`
- `SENSOR_SENSITIVITY`
- `SENSOR_TEST_PATTERN_DATA`
- `SENSOR_TEST_PATTERN_MODE`
- `SHADING_MODE`
- `STATISTICS_FACE_DETECT_MODE`
- `STATISTICS_HOT_PIXEL_MAP_MODE`
- `STATISTICS_LENS_SHADING_MAP_MODE`
- `STATISTICS_OIS_DATA_MODE`
- `TONEMAP_CURVE`
- `TONEMAP_GAMMA`
- `TONEMAP_MODE`
- `TONEMAP_PRESET_CURVE`

## CaptureResult.Keys (capture results & partials)

- `BLACK_LEVEL_LOCK`
- `COLOR_CORRECTION_ABERRATION_MODE`
- `COLOR_CORRECTION_COLOR_TEMPERATURE`
- `COLOR_CORRECTION_COLOR_TINT`
- `COLOR_CORRECTION_GAINS`
- `COLOR_CORRECTION_MODE`
- `COLOR_CORRECTION_TRANSFORM`
- `CONTROL_AE_ANTIBANDING_MODE`
- `CONTROL_AE_EXPOSURE_COMPENSATION`
- `CONTROL_AE_LOCK`
- `CONTROL_AE_MODE`
- `CONTROL_AE_PRECAPTURE_TRIGGER`
- `CONTROL_AE_PRIORITY_MODE`
- `CONTROL_AE_REGIONS`
- `CONTROL_AE_STATE`
- `CONTROL_AE_TARGET_FPS_RANGE`
- `CONTROL_AF_MODE`
- `CONTROL_AF_REGIONS`
- `CONTROL_AF_SCENE_CHANGE`
- `CONTROL_AF_STATE`
- `CONTROL_AF_TRIGGER`
- `CONTROL_AUTOFRAMING`
- `CONTROL_AUTOFRAMING_STATE`
- `CONTROL_AWB_LOCK`
- `CONTROL_AWB_MODE`
- `CONTROL_AWB_REGIONS`
- `CONTROL_AWB_STATE`
- `CONTROL_CAPTURE_INTENT`
- `CONTROL_EFFECT_MODE`
- `CONTROL_ENABLE_ZSL`
- `CONTROL_EXTENDED_SCENE_MODE`
- `CONTROL_LOW_LIGHT_BOOST_STATE`
- `CONTROL_MODE`
- `CONTROL_POST_RAW_SENSITIVITY_BOOST`
- `CONTROL_SCENE_MODE`
- `CONTROL_SETTINGS_OVERRIDE`
- `CONTROL_VIDEO_STABILIZATION_MODE`
- `CONTROL_ZOOM_METHOD`
- `CONTROL_ZOOM_RATIO`
- `DISTORTION_CORRECTION_MODE`
- `EDGE_MODE`
- `EXTENSION_CURRENT_TYPE`
- `EXTENSION_NIGHT_MODE_INDICATOR`
- `EXTENSION_STRENGTH`
- `FLASH_MODE`
- `FLASH_STATE`
- `FLASH_STRENGTH_LEVEL`
- `HOT_PIXEL_MODE`
- `JPEG_GPS_LOCATION`
- `JPEG_ORIENTATION`
- `JPEG_QUALITY`
- `JPEG_THUMBNAIL_QUALITY`
- `JPEG_THUMBNAIL_SIZE`
- `LENS_APERTURE`
- `LENS_DISTORTION`
- `LENS_FILTER_DENSITY`
- `LENS_FOCAL_LENGTH`
- `LENS_FOCUS_DISTANCE`
- `LENS_FOCUS_RANGE`
- `LENS_INTRINSIC_CALIBRATION`
- `LENS_OPTICAL_STABILIZATION_MODE`
- `LENS_POSE_ROTATION`
- `LENS_POSE_TRANSLATION`
- `LENS_RADIAL_DISTORTION`
- `LENS_STATE`
- `LOGICAL_MULTI_CAMERA_ACTIVE_PHYSICAL_ID`
- `LOGICAL_MULTI_CAMERA_ACTIVE_PHYSICAL_SENSOR_CROP_REGION`
- `NOISE_REDUCTION_MODE`
- `REPROCESS_EFFECTIVE_EXPOSURE_FACTOR`
- `REQUEST_PIPELINE_DEPTH`
- `SCALER_CROP_REGION`
- `SCALER_RAW_CROP_REGION`
- `SCALER_ROTATE_AND_CROP`
- `SENSOR_DYNAMIC_BLACK_LEVEL`
- `SENSOR_DYNAMIC_WHITE_LEVEL`
- `SENSOR_EXPOSURE_TIME`
- `SENSOR_FRAME_DURATION`
- `SENSOR_GREEN_SPLIT`
- `SENSOR_NEUTRAL_COLOR_POINT`
- `SENSOR_NOISE_PROFILE`
- `SENSOR_PIXEL_MODE`
- `SENSOR_RAW_BINNING_FACTOR_USED`
- `SENSOR_ROLLING_SHUTTER_SKEW`
- `SENSOR_SENSITIVITY`
- `SENSOR_TEST_PATTERN_DATA`
- `SENSOR_TEST_PATTERN_MODE`
- `SENSOR_TIMESTAMP`
- `SHADING_MODE`
- `STATISTICS_FACE_DETECT_MODE`
- `STATISTICS_FACES`
- `STATISTICS_HOT_PIXEL_MAP`
- `STATISTICS_HOT_PIXEL_MAP_MODE`
- `STATISTICS_LENS_INTRINSICS_SAMPLES`
- `STATISTICS_LENS_SHADING_CORRECTION_MAP`
- `STATISTICS_LENS_SHADING_MAP_MODE`
- `STATISTICS_OIS_DATA_MODE`
- `STATISTICS_OIS_SAMPLES`
- `STATISTICS_SCENE_FLICKER`
- `TONEMAP_CURVE`
- `TONEMAP_GAMMA`
- `TONEMAP_MODE`
- `TONEMAP_PRESET_CURVE`

---

## Core Camera2 & capture pipeline APIs (`javap` public surface)

Public methods only - for signatures and parameters see Android Studio or the linked reference docs.

### CameraManager

```
public final class android.hardware.camera2.CameraManager {
  public android.hardware.camera2.CameraCharacteristics getCameraCharacteristics(java.lang.String) throws android.hardware.camera2.CameraAccessException;
  public android.hardware.camera2.CameraDevice$CameraDeviceSetup getCameraDeviceSetup(java.lang.String) throws android.hardware.camera2.CameraAccessException;
  public android.hardware.camera2.CameraExtensionCharacteristics getCameraExtensionCharacteristics(java.lang.String) throws android.hardware.camera2.CameraAccessException;
  public java.lang.String[] getCameraIdList() throws android.hardware.camera2.CameraAccessException;
  public java.util.Set<java.util.Set<java.lang.String>> getConcurrentCameraIds() throws android.hardware.camera2.CameraAccessException;
  public int getTorchStrengthLevel(java.lang.String) throws android.hardware.camera2.CameraAccessException;
  public boolean isCameraDeviceSetupSupported(java.lang.String) throws android.hardware.camera2.CameraAccessException;
  public boolean isConcurrentSessionConfigurationSupported(java.util.Map<java.lang.String, android.hardware.camera2.params.SessionConfiguration>) throws android.hardware.camera2.CameraAccessException;
  public void openCamera(java.lang.String, android.hardware.camera2.CameraDevice$StateCallback, android.os.Handler) throws android.hardware.camera2.CameraAccessException;
  public void openCamera(java.lang.String, java.util.concurrent.Executor, android.hardware.camera2.CameraDevice$StateCallback) throws android.hardware.camera2.CameraAccessException;
  public void registerAvailabilityCallback(android.hardware.camera2.CameraManager$AvailabilityCallback, android.os.Handler);
  public void registerAvailabilityCallback(java.util.concurrent.Executor, android.hardware.camera2.CameraManager$AvailabilityCallback);
  public void registerTorchCallback(android.hardware.camera2.CameraManager$TorchCallback, android.os.Handler);
  public void registerTorchCallback(java.util.concurrent.Executor, android.hardware.camera2.CameraManager$TorchCallback);
  public void setTorchMode(java.lang.String, boolean) throws android.hardware.camera2.CameraAccessException;
  public void turnOnTorchWithStrengthLevel(java.lang.String, int) throws android.hardware.camera2.CameraAccessException;
  public void unregisterAvailabilityCallback(android.hardware.camera2.CameraManager$AvailabilityCallback);
  public void unregisterTorchCallback(android.hardware.camera2.CameraManager$TorchCallback);
```

### CameraDevice

```
public abstract class android.hardware.camera2.CameraDevice implements java.lang.AutoCloseable {
  public abstract void close();
  public abstract android.hardware.camera2.CaptureRequest$Builder createCaptureRequest(int) throws android.hardware.camera2.CameraAccessException;
  public android.hardware.camera2.CaptureRequest$Builder createCaptureRequest(int, java.util.Set<java.lang.String>) throws android.hardware.camera2.CameraAccessException;
  public void createCaptureSession(android.hardware.camera2.params.SessionConfiguration) throws android.hardware.camera2.CameraAccessException;
  public abstract void createCaptureSession(java.util.List<android.view.Surface>, android.hardware.camera2.CameraCaptureSession$StateCallback, android.os.Handler) throws android.hardware.camera2.CameraAccessException;
  public abstract void createCaptureSessionByOutputConfigurations(java.util.List<android.hardware.camera2.params.OutputConfiguration>, android.hardware.camera2.CameraCaptureSession$StateCallback, android.os.Handler) throws android.hardware.camera2.CameraAccessException;
  public abstract void createConstrainedHighSpeedCaptureSession(java.util.List<android.view.Surface>, android.hardware.camera2.CameraCaptureSession$StateCallback, android.os.Handler) throws android.hardware.camera2.CameraAccessException;
  public void createExtensionSession(android.hardware.camera2.params.ExtensionSessionConfiguration) throws android.hardware.camera2.CameraAccessException;
  public abstract android.hardware.camera2.CaptureRequest$Builder createReprocessCaptureRequest(android.hardware.camera2.TotalCaptureResult) throws android.hardware.camera2.CameraAccessException;
  public abstract void createReprocessableCaptureSession(android.hardware.camera2.params.InputConfiguration, java.util.List<android.view.Surface>, android.hardware.camera2.CameraCaptureSession$StateCallback, android.os.Handler) throws android.hardware.camera2.CameraAccessException;
  public abstract void createReprocessableCaptureSessionByConfigurations(android.hardware.camera2.params.InputConfiguration, java.util.List<android.hardware.camera2.params.OutputConfiguration>, android.hardware.camera2.CameraCaptureSession$StateCallback, android.os.Handler) throws android.hardware.camera2.CameraAccessException;
  public int getCameraAudioRestriction() throws android.hardware.camera2.CameraAccessException;
  public abstract java.lang.String getId();
  public boolean isSessionConfigurationSupported(android.hardware.camera2.params.SessionConfiguration) throws android.hardware.camera2.CameraAccessException;
  public void setCameraAudioRestriction(int) throws android.hardware.camera2.CameraAccessException;
```

### CameraCaptureSession

```
public abstract class android.hardware.camera2.CameraCaptureSession implements java.lang.AutoCloseable {
  public android.hardware.camera2.CameraCaptureSession();
  public abstract void abortCaptures() throws android.hardware.camera2.CameraAccessException;
  public abstract int capture(android.hardware.camera2.CaptureRequest, android.hardware.camera2.CameraCaptureSession$CaptureCallback, android.os.Handler) throws android.hardware.camera2.CameraAccessException;
  public abstract int captureBurst(java.util.List<android.hardware.camera2.CaptureRequest>, android.hardware.camera2.CameraCaptureSession$CaptureCallback, android.os.Handler) throws android.hardware.camera2.CameraAccessException;
  public int captureBurstRequests(java.util.List<android.hardware.camera2.CaptureRequest>, java.util.concurrent.Executor, android.hardware.camera2.CameraCaptureSession$CaptureCallback) throws android.hardware.camera2.CameraAccessException;
  public int captureSingleRequest(android.hardware.camera2.CaptureRequest, java.util.concurrent.Executor, android.hardware.camera2.CameraCaptureSession$CaptureCallback) throws android.hardware.camera2.CameraAccessException;
  public abstract void close();
  public abstract void finalizeOutputConfigurations(java.util.List<android.hardware.camera2.params.OutputConfiguration>) throws android.hardware.camera2.CameraAccessException;
  public abstract android.hardware.camera2.CameraDevice getDevice();
  public abstract android.view.Surface getInputSurface();
  public abstract boolean isReprocessable();
  public abstract void prepare(android.view.Surface) throws android.hardware.camera2.CameraAccessException;
  public abstract int setRepeatingBurst(java.util.List<android.hardware.camera2.CaptureRequest>, android.hardware.camera2.CameraCaptureSession$CaptureCallback, android.os.Handler) throws android.hardware.camera2.CameraAccessException;
  public int setRepeatingBurstRequests(java.util.List<android.hardware.camera2.CaptureRequest>, java.util.concurrent.Executor, android.hardware.camera2.CameraCaptureSession$CaptureCallback) throws android.hardware.camera2.CameraAccessException;
  public abstract int setRepeatingRequest(android.hardware.camera2.CaptureRequest, android.hardware.camera2.CameraCaptureSession$CaptureCallback, android.os.Handler) throws android.hardware.camera2.CameraAccessException;
  public int setSingleRepeatingRequest(android.hardware.camera2.CaptureRequest, java.util.concurrent.Executor, android.hardware.camera2.CameraCaptureSession$CaptureCallback) throws android.hardware.camera2.CameraAccessException;
  public abstract void stopRepeating() throws android.hardware.camera2.CameraAccessException;
  public boolean supportsOfflineProcessing(android.view.Surface);
  public android.hardware.camera2.CameraOfflineSession switchToOffline(java.util.Collection<android.view.Surface>, java.util.concurrent.Executor, android.hardware.camera2.CameraOfflineSession$CameraOfflineSessionCallback) throws android.hardware.camera2.CameraAccessException;
  public void updateOutputConfiguration(android.hardware.camera2.params.OutputConfiguration) throws android.hardware.camera2.CameraAccessException;
```

### CameraCaptureSession.CaptureCallback

```
public abstract class android.hardware.camera2.CameraCaptureSession$CaptureCallback {
  public android.hardware.camera2.CameraCaptureSession$CaptureCallback();
  public void onCaptureBufferLost(android.hardware.camera2.CameraCaptureSession, android.hardware.camera2.CaptureRequest, android.view.Surface, long);
  public void onCaptureCompleted(android.hardware.camera2.CameraCaptureSession, android.hardware.camera2.CaptureRequest, android.hardware.camera2.TotalCaptureResult);
  public void onCaptureFailed(android.hardware.camera2.CameraCaptureSession, android.hardware.camera2.CaptureRequest, android.hardware.camera2.CaptureFailure);
  public void onCaptureProgressed(android.hardware.camera2.CameraCaptureSession, android.hardware.camera2.CaptureRequest, android.hardware.camera2.CaptureResult);
  public void onCaptureSequenceAborted(android.hardware.camera2.CameraCaptureSession, int);
  public void onCaptureSequenceCompleted(android.hardware.camera2.CameraCaptureSession, int, long);
  public void onCaptureStarted(android.hardware.camera2.CameraCaptureSession, android.hardware.camera2.CaptureRequest, long, long);
  public void onReadoutStarted(android.hardware.camera2.CameraCaptureSession, android.hardware.camera2.CaptureRequest, long, long);
```

### CameraDevice.StateCallback

```
public abstract class android.hardware.camera2.CameraDevice$StateCallback {
  public android.hardware.camera2.CameraDevice$StateCallback();
  public void onClosed(android.hardware.camera2.CameraDevice);
  public abstract void onDisconnected(android.hardware.camera2.CameraDevice);
  public abstract void onError(android.hardware.camera2.CameraDevice, int);
  public abstract void onOpened(android.hardware.camera2.CameraDevice);
```

### CameraManager.AvailabilityCallback

```
public abstract class android.hardware.camera2.CameraManager$AvailabilityCallback {
  public android.hardware.camera2.CameraManager$AvailabilityCallback();
  public void onCameraAccessPrioritiesChanged();
  public void onCameraAvailable(java.lang.String);
  public void onCameraUnavailable(java.lang.String);
  public void onPhysicalCameraAvailable(java.lang.String, java.lang.String);
  public void onPhysicalCameraUnavailable(java.lang.String, java.lang.String);
```

### CaptureRequest.Builder

```
public final class android.hardware.camera2.CaptureRequest$Builder {
  public void addTarget(android.view.Surface);
  public android.hardware.camera2.CaptureRequest build();
  public <T> T get(android.hardware.camera2.CaptureRequest$Key<T>);
  public <T> T getPhysicalCameraKey(android.hardware.camera2.CaptureRequest$Key<T>, java.lang.String);
  public void removeTarget(android.view.Surface);
  public <T> void set(android.hardware.camera2.CaptureRequest$Key<T>, T);
  public <T> android.hardware.camera2.CaptureRequest$Builder setPhysicalCameraKey(android.hardware.camera2.CaptureRequest$Key<T>, T, java.lang.String);
  public void setTag(java.lang.Object);
```

### TotalCaptureResult

```
public final class android.hardware.camera2.TotalCaptureResult extends android.hardware.camera2.CaptureResult {
  public java.util.List<android.hardware.camera2.CaptureResult> getPartialResults();
  public java.util.Map<java.lang.String, android.hardware.camera2.CaptureResult> getPhysicalCameraResults();
  public java.util.Map<java.lang.String, android.hardware.camera2.TotalCaptureResult> getPhysicalCameraTotalResults();
```

### params.SessionConfiguration

```
public final class android.hardware.camera2.params.SessionConfiguration implements android.os.Parcelable {
  public android.hardware.camera2.params.SessionConfiguration(int, java.util.List<android.hardware.camera2.params.OutputConfiguration>);
  public android.hardware.camera2.params.SessionConfiguration(int, java.util.List<android.hardware.camera2.params.OutputConfiguration>, java.util.concurrent.Executor, android.hardware.camera2.CameraCaptureSession$StateCallback);
  public void clearColorSpace();
  public int describeContents();
  public boolean equals(java.lang.Object);
  public android.graphics.ColorSpace getColorSpace();
  public java.util.concurrent.Executor getExecutor();
  public android.hardware.camera2.params.InputConfiguration getInputConfiguration();
  public java.util.List<android.hardware.camera2.params.OutputConfiguration> getOutputConfigurations();
  public android.hardware.camera2.CaptureRequest getSessionParameters();
  public int getSessionType();
  public android.hardware.camera2.CameraCaptureSession$StateCallback getStateCallback();
  public int hashCode();
  public void setColorSpace(android.graphics.ColorSpace$Named);
  public void setInputConfiguration(android.hardware.camera2.params.InputConfiguration);
  public void setSessionParameters(android.hardware.camera2.CaptureRequest);
  public void setStateCallback(java.util.concurrent.Executor, android.hardware.camera2.CameraCaptureSession$StateCallback);
  public void writeToParcel(android.os.Parcel, int);
```

### params.OutputConfiguration

```
public final class android.hardware.camera2.params.OutputConfiguration implements android.os.Parcelable {
  public <T> android.hardware.camera2.params.OutputConfiguration(android.util.Size, java.lang.Class<T>);
  public android.hardware.camera2.params.OutputConfiguration(android.view.Surface);
  public android.hardware.camera2.params.OutputConfiguration(int, android.util.Size);
  public android.hardware.camera2.params.OutputConfiguration(int, android.util.Size, long);
  public android.hardware.camera2.params.OutputConfiguration(int, android.view.Surface);
  public android.hardware.camera2.params.OutputConfiguration(int, int, android.util.Size);
  public android.hardware.camera2.params.OutputConfiguration(int, int, android.util.Size, long);
  public void addSensorPixelModeUsed(int);
  public void addSurface(android.view.Surface);
  public static java.util.Collection<android.hardware.camera2.params.OutputConfiguration> createInstancesForMultiResolutionOutput(android.hardware.camera2.MultiResolutionImageReader);
  public static java.util.List<android.hardware.camera2.params.OutputConfiguration> createInstancesForMultiResolutionOutput(java.util.Collection<android.hardware.camera2.params.MultiResolutionStreamInfo>, int);
  public int describeContents();
  public void enableSurfaceSharing();
  public boolean equals(java.lang.Object);
  public long getDynamicRangeProfile();
  public int getMaxSharedSurfaceCount();
  public int getMirrorMode();
  public int getMirrorMode(android.view.Surface);
  public long getStreamUseCase();
  public android.view.Surface getSurface();
  public int getSurfaceGroupId();
  public java.util.List<android.view.Surface> getSurfaces();
  public int getTimestampBase();
  public int hashCode();
  public boolean isReadoutTimestampEnabled();
  public void removeSensorPixelModeUsed(int);
  public void removeSurface(android.view.Surface);
  public void setDynamicRangeProfile(long);
  public void setMirrorMode(android.view.Surface, int);
  public void setMirrorMode(int);
  public void setPhysicalCameraId(java.lang.String);
  public void setReadoutTimestampEnabled(boolean);
  public void setStreamUseCase(long);
  public static void setSurfacesForMultiResolutionOutput(java.util.Collection<android.hardware.camera2.params.OutputConfiguration>, android.hardware.camera2.MultiResolutionImageReader);
  public void setTimestampBase(int);
  public void writeToParcel(android.os.Parcel, int);
```

### params.StreamConfigurationMap

```
public final class android.hardware.camera2.params.StreamConfigurationMap {
  public boolean equals(java.lang.Object);
  public android.util.Size[] getHighResolutionOutputSizes(int);
  public android.util.Range<java.lang.Integer>[] getHighSpeedVideoFpsRanges();
  public android.util.Range<java.lang.Integer>[] getHighSpeedVideoFpsRangesFor(android.util.Size);
  public android.util.Size[] getHighSpeedVideoSizes();
  public android.util.Size[] getHighSpeedVideoSizesFor(android.util.Range<java.lang.Integer>);
  public int[] getInputFormats();
  public android.util.Size[] getInputSizes(int);
  public int[] getOutputFormats();
  public long getOutputMinFrameDuration(int, android.util.Size);
  public <T> long getOutputMinFrameDuration(java.lang.Class<T>, android.util.Size);
  public android.util.Size[] getOutputSizes(int);
  public <T> android.util.Size[] getOutputSizes(java.lang.Class<T>);
  public long getOutputStallDuration(int, android.util.Size);
  public <T> long getOutputStallDuration(java.lang.Class<T>, android.util.Size);
  public int[] getValidOutputFormatsForInput(int);
  public int hashCode();
  public boolean isOutputSupportedFor(android.view.Surface);
  public boolean isOutputSupportedFor(int);
  public static <T> boolean isOutputSupportedFor(java.lang.Class<T>);
  public java.lang.String toString();
```

### params.MeteringRectangle

```
public final class android.hardware.camera2.params.MeteringRectangle {
  public android.hardware.camera2.params.MeteringRectangle(android.graphics.Point, android.util.Size, int);
  public android.hardware.camera2.params.MeteringRectangle(android.graphics.Rect, int);
  public android.hardware.camera2.params.MeteringRectangle(int, int, int, int, int);
  public boolean equals(android.hardware.camera2.params.MeteringRectangle);
  public boolean equals(java.lang.Object);
  public int getHeight();
  public int getMeteringWeight();
  public android.graphics.Rect getRect();
  public android.util.Size getSize();
  public android.graphics.Point getUpperLeftPoint();
  public int getWidth();
  public int getX();
  public int getY();
  public int hashCode();
  public java.lang.String toString();
```

### params.Face

```
public final class android.hardware.camera2.params.Face {
  public android.graphics.Rect getBounds();
  public int getId();
  public android.graphics.Point getLeftEyePosition();
  public android.graphics.Point getMouthPosition();
  public android.graphics.Point getRightEyePosition();
  public int getScore();
  public java.lang.String toString();
```

### params.InputConfiguration

```
public final class android.hardware.camera2.params.InputConfiguration {
  public android.hardware.camera2.params.InputConfiguration(int, int, int);
  public android.hardware.camera2.params.InputConfiguration(java.util.Collection<android.hardware.camera2.params.MultiResolutionStreamInfo>, int);
  public boolean equals(java.lang.Object);
  public int getFormat();
  public int getHeight();
  public int getWidth();
  public int hashCode();
  public boolean isMultiResolution();
  public java.lang.String toString();
```

### CameraExtensionCharacteristics

```
public final class android.hardware.camera2.CameraExtensionCharacteristics {
  public <T> T get(int, android.hardware.camera2.CameraCharacteristics$Key<T>);
  public java.util.Set<android.hardware.camera2.CaptureRequest$Key> getAvailableCaptureRequestKeys(int);
  public java.util.Set<android.hardware.camera2.CaptureResult$Key> getAvailableCaptureResultKeys(int);
  public android.util.Range<java.lang.Long> getEstimatedCaptureLatencyRangeMillis(int, android.util.Size, int);
  public java.util.List<android.util.Size> getExtensionSupportedSizes(int, int);
  public <T> java.util.List<android.util.Size> getExtensionSupportedSizes(int, java.lang.Class<T>);
  public java.util.Set<android.hardware.camera2.CameraCharacteristics$Key> getKeys(int);
  public java.util.List<android.util.Size> getPostviewSupportedSizes(int, android.util.Size, int);
  public java.util.List<java.lang.Integer> getSupportedExtensions();
  public boolean isCaptureProcessProgressAvailable(int);
  public boolean isPostviewAvailable(int);
```

## Related types (see Android reference)

- `android.hardware.camera2.CameraAccessException`
- `android.hardware.camera2.CameraCharacteristics` (methods: `get`, `getKeys`, `getAvailableCaptureRequestKeys`, ...)
- `android.hardware.camera2.CameraMetadata` (AE/AF/AWB mode **int** constants used with `Key` values)
- `android.hardware.camera2.DngCreator`
- `android.hardware.camera2.extensions.*` (extensions API)
- `android.hardware.camera2.params.Face` / `android.hardware.camera2.params.*` (face bounds, optional eye points, OIS, high-speed video, recommended stream configuration, ...)
- `android.media.ImageReader` / `android.media.Image` / `android.graphics.ImageFormat` (YUV/RAW preview and analysis)
- `android.view.Surface` / `android.graphics.SurfaceTexture` (outputs)

## Point & Shoot in-repo usage (quick index)

- Probe export & vendor key safety: `CameraCapabilitiesProbe.kt`, `VendorKeyGuard.kt`, `AeHighlightProbe.kt`
- Face / eye / vendor face tags: `VendorFaceEyeKeyNames.kt`, `FaceMeterProbeScreen.kt`, `scripts/pns_face_meter_probe.ps1`, `docs/face-eye-tracking-toolkit.md`
- Preview / metering: `PreviewEngineScreen.kt`, `HighlightMeter.kt`, `HighlightMeterSupport.kt`
- ADB automation: `scripts/pns_ae_highlight_probe_adb.ps1`, `AGENTS.md`

---

## Face / eye tracking reference (Point & Shoot)

**Filtered key lists** (this API level): names matching ``FACE``, ``AUTOFRAMING``, region / max-region controls, AF/AE triggers and states, ``STATISTICS_*FACE*``, ``LENS_FOCUS_*``, ``LENS_STATE``, ``SYNC_MAX_LATENCY``. For the complete key tables, see the sections above.

### CameraCharacteristics (subset)

- `CONTROL_AE_LOCK_AVAILABLE`
- `CONTROL_AUTOFRAMING_AVAILABLE`
- `CONTROL_MAX_REGIONS_AE`
- `CONTROL_MAX_REGIONS_AF`
- `CONTROL_MAX_REGIONS_AWB`
- `STATISTICS_INFO_AVAILABLE_FACE_DETECT_MODES`
- `STATISTICS_INFO_MAX_FACE_COUNT`
- `SYNC_MAX_LATENCY`

### CaptureRequest (subset)

- `CONTROL_AE_LOCK`
- `CONTROL_AE_PRECAPTURE_TRIGGER`
- `CONTROL_AE_REGIONS`
- `CONTROL_AF_REGIONS`
- `CONTROL_AF_TRIGGER`
- `CONTROL_AUTOFRAMING`
- `CONTROL_AWB_REGIONS`
- `LENS_FOCUS_DISTANCE`
- `STATISTICS_FACE_DETECT_MODE`

### CaptureResult (subset)

- `CONTROL_AE_LOCK`
- `CONTROL_AE_PRECAPTURE_TRIGGER`
- `CONTROL_AE_REGIONS`
- `CONTROL_AE_STATE`
- `CONTROL_AF_REGIONS`
- `CONTROL_AF_STATE`
- `CONTROL_AF_TRIGGER`
- `CONTROL_AUTOFRAMING`
- `CONTROL_AUTOFRAMING_STATE`
- `CONTROL_AWB_REGIONS`
- `LENS_FOCUS_DISTANCE`
- `LENS_FOCUS_RANGE`
- `LENS_STATE`
- `STATISTICS_FACE_DETECT_MODE`
- `STATISTICS_FACES`

### Semantics (public HAL contract)

| Topic | Notes |
|-------|--------|
| Face geometry | `CaptureResult.STATISTICS_FACES` → `android.hardware.camera2.params.Face` (bounds, id, score). Eye positions are **optional** (`getLeftEyePosition` / `getRightEyePosition`) when `STATISTICS_FACE_DETECT_MODE_FULL` is supported and the HAL fills them. |
| Face detect request | `CaptureRequest.STATISTICS_FACE_DETECT_MODE` — values from `CameraMetadata.STATISTICS_FACE_DETECT_MODE_*` (`OFF`, `SIMPLE`, `FULL`). |
| Capability | `CameraCharacteristics.STATISTICS_INFO_AVAILABLE_FACE_DETECT_MODES`, `STATISTICS_INFO_MAX_FACE_COUNT`. |
| Metering / AF | `CONTROL_AE_REGIONS`, `CONTROL_AF_REGIONS`, `CONTROL_AWB_REGIONS`; caps `CONTROL_MAX_REGIONS_AE` / `_AF` / `_AWB` on characteristics. Tap / face-priority paths also use `CONTROL_AF_TRIGGER`, `CONTROL_AE_PRECAPTURE_TRIGGER`, `CONTROL_AE_LOCK`, `CONTROL_AF_STATE`, `LENS_FOCUS_DISTANCE`, `LENS_STATE`, `LENS_FOCUS_RANGE` on results. |
| Auto-framing | `CONTROL_AUTOFRAMING` (request) / `CONTROL_AUTOFRAMING_STATE` (result) when the device advertises `CONTROL_AUTOFRAMING_AVAILABLE`. |
| Latency | `SYNC_MAX_LATENCY` on characteristics (face pipeline timing expectations). |

There is **no separate** public Camera2 “eye tracking” key: eyes are carried on `Face` when in FULL mode.

### Vendor metadata (OEM)

- **Discovery:** `CameraCapabilitiesProbe` / exported **PROBE_RESULTS** markdown — section **`### Named vendor keys — face / eye / tracking (by scope)`**, and **`face_meter_probe_*.json`** (`vendorNamedFaceEyeTracking_*`, `schemaVersion` ≥ 2).
- **Heuristic filter:** `VendorFaceEyeKeyNames.kt` — vendor-ish name (`com.` / `org.` / `vendor`) **and** substring list (`face`, `eye`, `iris`, `tracking`, `portrait`, …). **Not exhaustive** for every OEM spelling.
- **Production use:** `VendorKeyGuard.kt` — any vendor tag must be gated before set.

### ML Kit (YUV analysis fallback)

- `MlKitFaceTrackSupport` — `FaceDetectorOptions` **FAST** + **`LANDMARK_MODE_ALL`**; boxes + optional eye landmarks mapped to preview buffer space (`TexturePreviewFit`).
- Geometry: `MlFaceHudDetections`, `FaceTrackOverlay`, `EyeAfOverlay`, `FaceDetectAdapter`.

### Point & Shoot — where it lives

| Area | Primary files |
|------|----------------|
| Preview face + eyes + metering | `PreviewEngineScreen.kt` (`PreviewController.processFaceStatistics`, `dispatchFaceHudOverlay`, ML YUV lane) |
| Overlay | `FaceTrackOverlay.kt`, `EyeAfOverlay.kt` |
| Caps / gate | `HardwareCapsSnapshot.kt`, `CapabilityGate.kt` |
| Probes | `CameraCapabilitiesProbe.kt`, `FaceMeterProbeScreen.kt`, `scripts/pns_face_meter_probe.ps1` |
| Full toolkit narrative | `docs/face-eye-tracking-toolkit.md` |

### ADB / automation (debug)

| Extra | Purpose |
|-------|---------|
| `--es pns_screen facemeter` | Opens face / metering probe screen |
| `--ez pns_autofacemeter true` | Auto-write `face_meter_probe_*.{md,json}` and finish |
| `pns_auto_export_probe` + `pns_screen=probehub` | Full probe markdown to app files (`PROBE_EXPORT_LATEST.md`) — see `scripts/pns_ae_highlight_probe_adb.ps1` |

### Related Android types (not re-listed in `javap` blocks above)

- `android.hardware.camera2.CameraMetadata` — `STATISTICS_FACE_DETECT_MODE_*`, `CONTROL_AF_STATE`, `CONTROL_AE_STATE`, …
- `com.google.mlkit.vision.face.*` — detector, `Face`, `FaceLandmark` (ML path; not Camera2).

