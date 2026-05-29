package dev.pointandshoot.fleet

import org.json.JSONObject

/** Master capability taxonomy (Milestone **17.1**). */
object CameraCapabilityCatalog {
    const val CATALOG_VERSION: Int = 1

    enum class SourceLayer(val label: String) {
        Camera2("Camera2"),
        CameraX("CameraX"),
        HalEncoder("HAL/encoder"),
        Product("Product"),
        Root("Root"),
    }

    enum class VisibilityPolicy {
        HideWhenUnavailable,
        RootOnly,
        ShowDisabledEngineering,
        AlwaysShow,
    }

    enum class AppStatus {
        Shipped,
        Partial,
        ProbeOnly,
        Planned,
        NotApplicable,
    }

    data class CatalogRow(
        val id: String,
        val displayName: String,
        val category: String,
        val sourceLayer: SourceLayer,
        val keywords: String = "",
        val visibilityPolicy: VisibilityPolicy = VisibilityPolicy.HideWhenUnavailable,
        val appStatus: AppStatus = AppStatus.Shipped,
        val surfacing: List<String> = emptyList(),
        val rootOnly: Boolean = false,
    )

    data class EvaluatedRow(
        val row: CatalogRow,
        val deviceSupported: Boolean,
        val sessionOk: Boolean? = null,
        val detail: String = "",
    ) {
        fun toJson(): JSONObject =
            JSONObject().apply {
                put("id", row.id)
                put("displayName", row.displayName)
                put("category", row.category)
                put("sourceLayer", row.sourceLayer.label)
                put("deviceSupported", deviceSupported)
                put("appStatus", row.appStatus.name)
                put("appShipped", row.appStatus == AppStatus.Shipped || row.appStatus == AppStatus.Partial)
                put("rootOnly", row.rootOnly)
                put("visibilityPolicy", row.visibilityPolicy.name)
                if (sessionOk != null) put("sessionOk", sessionOk)
                if (row.surfacing.isNotEmpty()) put("surfacing", row.surfacing)
                if (detail.isNotEmpty()) put("detail", detail)
            }
    }

    val registry: List<CatalogRow> = listOf(
        CatalogRow("raw.dng", "RAW DNG capture", "Still capture", SourceLayer.Product, "raw dng still", surfacing = listOf("dial_H", "settings")),
        CatalogRow("raw.ultra_max", "Ultra-Max imaging profile", "Still capture", SourceLayer.Product, "ultra max 12bit"),
        CatalogRow("still.bracket", "Exposure bracketing (BKT dial)", "Still capture", SourceLayer.Product, "bracket bkt", surfacing = listOf("mode_dial")),
        CatalogRow("still.zsl", "ZSL still capture", "Still capture", SourceLayer.Camera2, "zsl zero shutter lag"),
        CatalogRow("still.avif", "10-bit AVIF still", "Still capture", SourceLayer.Product, "avif hdr 10bit"),
        CatalogRow("still.nightscape", "Nightscape stacking", "Still capture", SourceLayer.Product, "nightscape"),
        CatalogRow("still.intervalometer", "Intervalometer", "Still capture", SourceLayer.Product, "intervalometer timelapse"),
        CatalogRow("video.h264", "H.264 video", "Video", SourceLayer.HalEncoder, "h264 avc", surfacing = listOf("format_picker")),
        CatalogRow("video.hevc", "HEVC video", "Video", SourceLayer.HalEncoder, "hevc h265", surfacing = listOf("format_picker")),
        CatalogRow("video.av1", "AV1 video", "Video", SourceLayer.HalEncoder, "av1", appStatus = AppStatus.Partial),
        CatalogRow("video.hfr", "High-speed video (120+ fps)", "Video", SourceLayer.Camera2, "hfr 120 240 480", surfacing = listOf("fps_rail", "format_picker")),
        CatalogRow("video.regular.1080p30", "1080p @ 30 fps (regular)", "Video", SourceLayer.HalEncoder, "1080p 30fps", surfacing = listOf("format_picker")),
        CatalogRow("video.dcg_hdr", "HDR / DCG video", "Video", SourceLayer.Camera2, "dcg hdr10"),
        CatalogRow("video.raw", "RAW video", "Video", SourceLayer.Product, "raw video", appStatus = AppStatus.Partial),
        CatalogRow("video.dual", "Dual video composite", "Video", SourceLayer.Product, "dual video", surfacing = listOf("mode_dial")),
        CatalogRow("video.timelapse", "Time lapse video", "Video", SourceLayer.Product, "timelapse"),
        CatalogRow("face.detect", "Face detection", "Face & AF", SourceLayer.Camera2, "face detect"),
        CatalogRow("face.eye_af", "Eye-AF overlay", "Face & AF", SourceLayer.Product, "eye af", surfacing = listOf("qs_grid", "settings")),
        CatalogRow("face.priority_ae", "Face-priority AE", "Face & AF", SourceLayer.Product, "face priority ae"),
        CatalogRow("af.manual", "Manual focus (M dial)", "Face & AF", SourceLayer.Camera2, "manual focus", surfacing = listOf("mode_dial")),
        CatalogRow("af.rack", "Rack focus pull", "Face & AF", SourceLayer.Product, "rack focus"),
        CatalogRow("af.macro", "Super Macro", "Face & AF", SourceLayer.Product, "macro", surfacing = listOf("mode_dial")),
        CatalogRow("lens.multi", "Focal slots / multi-cam", "Lens", SourceLayer.Product, "focal uw tele", surfacing = listOf("focal_row")),
        CatalogRow("lens.ois", "Optical stabilization", "Lens", SourceLayer.Camera2, "ois", surfacing = listOf("settings")),
        CatalogRow("lens.eis", "Electronic stabilization", "Lens", SourceLayer.Camera2, "eis", surfacing = listOf("settings")),
        CatalogRow("hud.zebra", "Zebra / false color", "Preview HUD", SourceLayer.Product, "zebra false color", surfacing = listOf("qs_grid")),
        CatalogRow("hud.histogram", "Histogram", "Preview HUD", SourceLayer.Product, "histogram", surfacing = listOf("settings")),
        CatalogRow("hud.highlight_meter", "Highlight metering (H)", "Preview HUD", SourceLayer.Product, "highlight h dial", surfacing = listOf("mode_dial")),
        CatalogRow("hud.focus_peaking", "Focus peaking", "Preview HUD", SourceLayer.Product, "peaking"),
        CatalogRow("preview.qr", "QR scan mode", "Preview HUD", SourceLayer.Product, "qr scan", surfacing = listOf("mode_dial")),
        CatalogRow("camerax.night", "CameraX NIGHT", "CameraX", SourceLayer.CameraX, "night", appStatus = AppStatus.ProbeOnly, visibilityPolicy = VisibilityPolicy.ShowDisabledEngineering),
        CatalogRow("camerax.bokeh", "CameraX BOKEH", "CameraX", SourceLayer.CameraX, "bokeh", appStatus = AppStatus.ProbeOnly, visibilityPolicy = VisibilityPolicy.ShowDisabledEngineering),
        CatalogRow("camerax.hdr", "CameraX HDR", "CameraX", SourceLayer.CameraX, "hdr extension", appStatus = AppStatus.ProbeOnly, visibilityPolicy = VisibilityPolicy.ShowDisabledEngineering),
        CatalogRow("root.vendor_keys", "Vendor key probe", "Root", SourceLayer.Root, "root vendor", rootOnly = true, visibilityPolicy = VisibilityPolicy.RootOnly, appStatus = AppStatus.ProbeOnly),
        CatalogRow("root.cpu_governor", "CPU governor pin", "Root", SourceLayer.Root, "root cpu", rootOnly = true, visibilityPolicy = VisibilityPolicy.RootOnly, appStatus = AppStatus.ProbeOnly),
        CatalogRow("root.hfr_unlock", "HFR above matrix ceiling", "Root", SourceLayer.Root, "root hfr fps", rootOnly = true, visibilityPolicy = VisibilityPolicy.RootOnly, surfacing = listOf("fps_rail")),
        CatalogRow("fleet.matrix", "Device capability matrix", "Fleet", SourceLayer.Product, "fleet matrix", visibilityPolicy = VisibilityPolicy.AlwaysShow, surfacing = listOf("engineering_hub")),
        CatalogRow("fleet.deep_caps", "Deep caps probe", "Fleet", SourceLayer.Product, "deep caps", appStatus = AppStatus.ProbeOnly, visibilityPolicy = VisibilityPolicy.ShowDisabledEngineering),
    )
}
