package dev.pointandshoot

import android.content.Context
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject

/**
 * Sprint **UX.3** — named capture workflows (dial + imaging folder + photo/video tray).
 * Persisted in app-private JSON; ADB `pns_preview_workflow_preset` applies on cold preview.
 */
data class WorkflowPreset(
    val id: String,
    val label: String,
    val commandDialMode: CommandDialMode,
    val imagingProfileId: String,
    val primaryPhoto: Boolean = true,
    val fps: Int? = null,
) {
    fun toJson(): JSONObject =
        JSONObject()
            .put("id", id)
            .put("label", label)
            .put("commandDialMode", commandDialMode.name)
            .put("imagingProfileId", imagingProfileId)
            .put("primaryPhoto", primaryPhoto)
            .put("fps", fps ?: JSONObject.NULL)

    companion object {
        fun fromJson(o: JSONObject): WorkflowPreset? =
            runCatching {
                WorkflowPreset(
                    id = o.getString("id"),
                    label = o.getString("label"),
                    commandDialMode =
                        CommandDialMode.entries.firstOrNull {
                            it.name == o.getString("commandDialMode")
                        } ?: CommandDialMode.Auto,
                    imagingProfileId = o.getString("imagingProfileId"),
                    primaryPhoto = o.optBoolean("primaryPhoto", true),
                    fps = if (o.has("fps") && !o.isNull("fps")) o.getInt("fps") else null,
                )
            }.getOrNull()
    }
}

object WorkflowPresets {
    private const val TAG = "PNS.Workflow"
    private const val PREFS = "pns_workflow_presets"
    private const val KEY_JSON = "presets_json"

    val builtIn: List<WorkflowPreset> =
        listOf(
            WorkflowPreset(
                id = "street",
                label = "Street",
                commandDialMode = CommandDialMode.S,
                imagingProfileId = ImagingProfile.StandardPro.id,
                primaryPhoto = true,
                fps = 60,
            ),
            WorkflowPreset(
                id = "portrait",
                label = "Portrait",
                commandDialMode = CommandDialMode.M,
                imagingProfileId = ImagingProfile.StandardPro.id,
                primaryPhoto = true,
                fps = 60,
            ),
            WorkflowPreset(
                id = "video_log",
                label = "Video log",
                commandDialMode = CommandDialMode.Auto,
                imagingProfileId = ImagingProfile.JpegOnly.id,
                primaryPhoto = false,
                fps = 60,
            ),
            WorkflowPreset(
                id = "macro_video",
                label = "Macro video",
                commandDialMode = CommandDialMode.Macro,
                imagingProfileId = ImagingProfile.StandardPro.id,
                primaryPhoto = false,
                fps = CaptureMediaFamily.MACRO_VIDEO_MAX_FPS,
            ),
        )

    fun all(context: Context): List<WorkflowPreset> {
        val custom = loadCustom(context)
        val byId = linkedMapOf<String, WorkflowPreset>()
        builtIn.forEach { byId[it.id] = it }
        custom.forEach { byId[it.id] = it }
        return byId.values.toList()
    }

    fun byId(context: Context, id: String?): WorkflowPreset? {
        val want = id?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        return all(context).firstOrNull { it.id.equals(want, ignoreCase = true) }
    }

    fun saveCustom(context: Context, presets: List<WorkflowPreset>) {
        val arr = JSONArray()
        presets.forEach { arr.put(it.toJson()) }
        context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_JSON, arr.toString())
            .commit()
    }

    fun loadCustom(context: Context): List<WorkflowPreset> {
        val raw =
            context.applicationContext
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(KEY_JSON, null)
                ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) {
                    val item = arr.optJSONObject(i) ?: continue
                    WorkflowPreset.fromJson(item)?.let { add(it) }
                }
            }
        }.getOrElse {
            Log.w(TAG, "loadCustom parse failed: ${it.message}")
            emptyList()
        }
    }

    fun captureCurrent(
        id: String,
        label: String,
        commandDialMode: CommandDialMode,
        imagingProfile: ImagingProfile,
        primaryPhoto: Boolean,
        fps: Int,
    ): WorkflowPreset =
        WorkflowPreset(
            id = id,
            label = label,
            commandDialMode = commandDialMode,
            imagingProfileId = imagingProfile.id,
            primaryPhoto = primaryPhoto,
            fps = fps,
        )

    fun logApplied(context: Context, preset: WorkflowPreset) {
        Log.i(
            TAG,
            "workflowPreset applied id=${preset.id} dial=${preset.commandDialMode.name} " +
                "profile=${preset.imagingProfileId} photo=${preset.primaryPhoto} fps=${preset.fps}",
        )
        PnsAdbLog.i(
            context,
            "workflowPreset applied id=${preset.id} dial=${preset.commandDialMode.name} " +
                "profile=${preset.imagingProfileId}",
        )
    }
}
