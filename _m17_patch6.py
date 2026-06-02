from pathlib import Path
p = Path("app/src/main/java/dev/pointandshoot/fleet/CameraCapabilityCatalogBuilder.kt")
t = p.read_text(encoding="utf-8")
t = t.replace('"lens.ois" -> anyCameraLensFlag(root, "hasOis")', '"lens.ois" -> lensHasOis(root)')
old = '''    private fun anyCameraLensFlag(root: JSONObject, flag: String): Triple<Boolean, Boolean?, String> {
        val cams = root.optJSONArray(FleetDeviceMatrix.KEY_CAMERAS) ?: return Triple(false, null, "")
        for (i in 0 until cams.length()) {
            val lens = cams.optJSONObject(i)?.optJSONObject("lensInfo") ?: continue
            if (lens.optBoolean(flag, false)) return Triple(true, null, flag)
        }
        return Triple(false, null, flag)
    }'''
new = '''    private fun lensHasOis(root: JSONObject): Triple<Boolean, Boolean?, String> {
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
    }'''
if old in t:
    t = t.replace(old, new)
p.write_text(t, encoding="utf-8")
print("builder ois fix")
