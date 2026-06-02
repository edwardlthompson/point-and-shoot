from pathlib import Path
p = Path("app/src/main/java/dev/pointandshoot/fleet/DeepCapsProbeCore.kt")
t = p.read_text(encoding="utf-8")

if "outputSizesByFormat" not in t:
    t = t.replace(
        "import android.view.SurfaceHolder\nimport android.graphics.SurfaceTexture",
        "import android.graphics.ImageFormat\nimport android.media.MediaRecorder\nimport android.view.SurfaceHolder\nimport android.graphics.SurfaceTexture",
    )
    t = t.replace(
        '            camObj.put("streamConfigurationMap", streamConfigToJson(map))',
        '''            val streamJson = streamConfigToJson(map)
            streamJson.put(
                "aeTargetFpsRanges",
                rangeArrayToJson(cc.get(CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES)),
            )
            camObj.put("streamConfigurationMap", streamJson)
            val faceModes = cc.get(CameraCharacteristics.STATISTICS_INFO_AVAILABLE_FACE_DETECT_MODES) ?: intArrayOf()
            camObj.put("faceDetectModes", JSONArray().apply { faceModes.forEach { put(it) } })''',
    )

    old_fn = '''    internal fun streamConfigToJson(map: StreamConfigurationMap?): JSONObject {
        if (map == null) return JSONObject().put("present", false)

        fun hsConfigs(): JSONArray {
            val out = JSONArray()
            val hsSizes = runCatching { map.highSpeedVideoSizes?.toList() }.getOrNull().orEmpty()
            for (s in hsSizes) {
                val ranges = runCatching { map.getHighSpeedVideoFpsRangesFor(s) }.getOrNull()
                out.put(
                    JSONObject().apply {
                        put("w", s.width)
                        put("h", s.height)
                        put("fpsRanges", rangeArrayToJson(ranges))
                    },
                )
            }
            return out
        }

        return JSONObject().apply {
            put("present", true)
            put(
                "outputSizes",
                JSONObject().apply {
                    put(
                        "surfaceTexture",
                        sizeArrayToJson(runCatching { map.getOutputSizes(SurfaceTexture::class.java) }.getOrNull()),
                    )
                    put(
                        "surfaceHolder",
                        sizeArrayToJson(runCatching { map.getOutputSizes(SurfaceHolder::class.java) }.getOrNull()),
                    )
                },
            )
            put("highSpeedVideo", hsConfigs())
        }
    }
}'''

    new_fn = '''    internal fun streamConfigToJson(map: StreamConfigurationMap?): JSONObject {
        if (map == null) return JSONObject().put("present", false)

        fun hsConfigs(): JSONArray {
            val out = JSONArray()
            val hsSizes = runCatching { map.highSpeedVideoSizes?.toList() }.getOrNull().orEmpty()
            for (s in hsSizes) {
                val ranges = runCatching { map.getHighSpeedVideoFpsRangesFor(s) }.getOrNull()
                out.put(
                    JSONObject().apply {
                        put("w", s.width)
                        put("h", s.height)
                        put("fpsRanges", rangeArrayToJson(ranges))
                    },
                )
            }
            return out
        }

        val stSizes = runCatching { map.getOutputSizes(SurfaceTexture::class.java) }.getOrNull()
        val allSizeLists = listOfNotNull(
            stSizes?.toList(),
            runCatching { map.getOutputSizes(SurfaceHolder::class.java) }.getOrNull()?.toList(),
            runCatching { map.getOutputSizes(ImageFormat.JPEG) }.getOrNull()?.toList(),
            runCatching { map.getOutputSizes(ImageFormat.RAW_SENSOR) }.getOrNull()?.toList(),
            runCatching { map.getOutputSizes(ImageFormat.RAW10) }.getOrNull()?.toList(),
            runCatching { map.getOutputSizes(ImageFormat.RAW12) }.getOrNull()?.toList(),
            runCatching { map.getOutputSizes(MediaRecorder::class.java) }.getOrNull()?.toList(),
        )
        return JSONObject().apply {
            put("present", true)
            put(
                "outputSizes",
                JSONObject().apply {
                    put("surfaceTexture", sizeArrayToJson(stSizes))
                    put(
                        "surfaceHolder",
                        sizeArrayToJson(runCatching { map.getOutputSizes(SurfaceHolder::class.java) }.getOrNull()),
                    )
                },
            )
            put(
                "outputSizesByFormat",
                JSONObject().apply {
                    put("jpeg", sizeArrayToJson(runCatching { map.getOutputSizes(ImageFormat.JPEG) }.getOrNull()))
                    put("rawSensor", sizeArrayToJson(runCatching { map.getOutputSizes(ImageFormat.RAW_SENSOR) }.getOrNull()))
                    put("raw10", sizeArrayToJson(runCatching { map.getOutputSizes(ImageFormat.RAW10) }.getOrNull()))
                    put("raw12", sizeArrayToJson(runCatching { map.getOutputSizes(ImageFormat.RAW12) }.getOrNull()))
                    put("yuv420888", sizeArrayToJson(runCatching { map.getOutputSizes(ImageFormat.YUV_420_888) }.getOrNull()))
                    put(
                        "mediaRecorder",
                        sizeArrayToJson(runCatching { map.getOutputSizes(MediaRecorder::class.java) }.getOrNull()),
                    )
                },
            )
            put("highSpeedVideo", hsConfigs())
            put("aspectRatios", aspectRatiosJson(allSizeLists))
        }
    }

    internal fun aspectRatiosJson(sizeLists: List<List<Size>>): JSONArray {
        val ratios = linkedSetOf<String>()
        for (list in sizeLists) {
            for (s in list) {
                val g = gcdInt(s.width, s.height)
                if (g <= 0) continue
                ratios.add("${s.width / g}:${s.height / g}")
            }
        }
        return JSONArray().apply { ratios.sorted().forEach { put(it) } }
    }

    private fun gcdInt(a: Int, b: Int): Int {
        var x = kotlin.math.abs(a)
        var y = kotlin.math.abs(b)
        while (y != 0) {
            val t = y
            y = x % y
            x = t
        }
        return x
    }
}'''
    t = t.replace(old_fn, new_fn)
    p.write_text(t, encoding="utf-8")
    print("DeepCapsProbeCore.kt updated")
