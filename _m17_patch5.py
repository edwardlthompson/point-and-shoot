from pathlib import Path
p = Path("app/src/main/java/dev/pointandshoot/fleet/FleetDeviceMatrixStructured.kt")
t = p.read_text(encoding="utf-8")
if 'merged.put("faceDetectModes"' not in t:
    t = t.replace(
        "        if (deepCam?.has(LensInfoSummaryJson.KEY_LENS_INFO) == true) {",
        "        deepCam?.optJSONArray(\"faceDetectModes\")?.let { merged.put(\"faceDetectModes\", it) }\n        if (deepCam?.has(LensInfoSummaryJson.KEY_LENS_INFO) == true) {",
    )
    p.write_text(t, encoding="utf-8")
    print("structured updated")
