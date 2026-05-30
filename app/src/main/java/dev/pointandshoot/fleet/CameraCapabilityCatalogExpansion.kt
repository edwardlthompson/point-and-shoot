package dev.pointandshoot.fleet

/**
 * Programmatic catalog rows for Milestone **18.1** (~200 total with [CameraCapabilityCatalog.baseRegistry]).
 */
object CameraCapabilityCatalogExpansion {

    private fun row(
        id: String,
        name: String,
        category: String,
        layer: CameraCapabilityCatalog.SourceLayer = CameraCapabilityCatalog.SourceLayer.Product,
        keywords: String = "",
        status: CameraCapabilityCatalog.AppStatus = CameraCapabilityCatalog.AppStatus.Shipped,
        surfacing: List<String> = emptyList(),
        visibility: CameraCapabilityCatalog.VisibilityPolicy = CameraCapabilityCatalog.VisibilityPolicy.HideWhenUnavailable,
    ): CameraCapabilityCatalog.CatalogRow =
        CameraCapabilityCatalog.CatalogRow(
            id = id,
            displayName = name,
            category = category,
            sourceLayer = layer,
            keywords = keywords,
            appStatus = status,
            surfacing = surfacing,
            visibilityPolicy = visibility,
        )

    fun expandedRows(): List<CameraCapabilityCatalog.CatalogRow> = buildList {
        val codecs =
            listOf(
                "h264" to "H.264",
                "hevc" to "HEVC",
                "av1" to "AV1",
                "vp9" to "VP9",
            )
        val tiers =
            listOf(
                "720p" to "720p",
                "1080p" to "1080p",
                "4k" to "4K UHD",
                "8k" to "8K",
            )
        val fpsTags = listOf(24, 30, 60, 120, 240)
        for ((codecId, codecLabel) in codecs) {
            for ((tierId, tierLabel) in tiers) {
                val status =
                    when {
                        codecId == "vp9" -> CameraCapabilityCatalog.AppStatus.Planned
                        codecId == "av1" && tierId == "8k" -> CameraCapabilityCatalog.AppStatus.Partial
                        codecId == "h264" || codecId == "hevc" -> CameraCapabilityCatalog.AppStatus.Shipped
                        else -> CameraCapabilityCatalog.AppStatus.Partial
                    }
                add(
                    row(
                        "video.$codecId.$tierId",
                        "$codecLabel $tierLabel",
                        "Video",
                        CameraCapabilityCatalog.SourceLayer.HalEncoder,
                        "$codecId $tierId video",
                        status,
                        surfacing = listOf("format_picker"),
                    ),
                )
            }
        }
        for (fps in fpsTags) {
            add(
                row(
                    "video.hfr.$fps",
                    "HFR ${fps}fps",
                    "Video",
                    CameraCapabilityCatalog.SourceLayer.Camera2,
                    "hfr $fps",
                    if (fps <= 60) CameraCapabilityCatalog.AppStatus.NotApplicable else CameraCapabilityCatalog.AppStatus.Partial,
                    surfacing = listOf("fps_rail"),
                ),
            )
        }

        val stillFormats =
            listOf(
                "jpeg" to CameraCapabilityCatalog.AppStatus.Shipped,
                "avif" to CameraCapabilityCatalog.AppStatus.Shipped,
                "jxl" to CameraCapabilityCatalog.AppStatus.Partial,
                "dng" to CameraCapabilityCatalog.AppStatus.Shipped,
                "heic" to CameraCapabilityCatalog.AppStatus.Planned,
                "tiff16" to CameraCapabilityCatalog.AppStatus.Planned,
                "motion_photo" to CameraCapabilityCatalog.AppStatus.Planned,
                "monochrome_sensor" to CameraCapabilityCatalog.AppStatus.Planned,
            )
        for ((fmt, st) in stillFormats) {
            add(row("still.$fmt", "Still $fmt export", "Still capture", keywords = fmt, status = st))
        }

        val hudFeatures =
            listOf(
                "zebra" to "Zebra overlay",
                "false_color" to "False color",
                "histogram" to "Luma histogram",
                "focus_peaking" to "Focus peaking",
                "power_thermal" to "Power / thermal chip",
                "storage_remaining" to "Storage remaining",
                "audio_meters" to "Audio meters",
                "composition_grid" to "Composition grid",
                "level" to "Horizon level",
                "pillar_bars" to "Pillar bars",
            )
        for ((id, name) in hudFeatures) {
            add(row("hud.$id", name, "Preview HUD", surfacing = listOf("qs_grid", "settings")))
        }

        val afFeatures =
            listOf(
                "caf" to "Continuous AF",
                "manual_dial" to "Manual focus dial",
                "rack_pull" to "Rack focus pull",
                "macro_dedicated" to "Dedicated macro camera",
                "focus_breathing" to "Focus breathing compensation",
            )
        for ((id, name) in afFeatures) {
            val st = if (id == "macro_dedicated") CameraCapabilityCatalog.AppStatus.Planned else CameraCapabilityCatalog.AppStatus.Shipped
            add(row("af.$id", name, "Face & AF", status = st))
        }

        val audioFeatures =
            listOf(
                "aac_48k" to CameraCapabilityCatalog.AppStatus.Shipped,
                "aac_96k" to CameraCapabilityCatalog.AppStatus.Shipped,
                "wind_filter" to CameraCapabilityCatalog.AppStatus.Shipped,
                "spatial" to CameraCapabilityCatalog.AppStatus.Partial,
                "voiceover_duck" to CameraCapabilityCatalog.AppStatus.Shipped,
                "light_compression" to CameraCapabilityCatalog.AppStatus.Shipped,
                "unprocessed" to CameraCapabilityCatalog.AppStatus.Partial,
            )
        for ((id, st) in audioFeatures) {
            add(row("audio.$id", "Audio $id", "Audio", status = st, surfacing = listOf("format_picker")))
        }

        val perfFeatures =
            listOf(
                "thermal_adaptive" to CameraCapabilityCatalog.AppStatus.Partial,
                "capture_latency" to CameraCapabilityCatalog.AppStatus.Partial,
                "cold_preview_ms" to CameraCapabilityCatalog.AppStatus.Partial,
                "first_frame_ms" to CameraCapabilityCatalog.AppStatus.Partial,
                "battery_adaptive_fps" to CameraCapabilityCatalog.AppStatus.Shipped,
            )
        for ((id, st) in perfFeatures) {
            add(row("perf.$id", "Performance $id", "Performance", status = st))
        }

        val tetherFeatures =
            listOf(
                "http_status" to CameraCapabilityCatalog.AppStatus.Shipped,
                "wifi_direct" to CameraCapabilityCatalog.AppStatus.Partial,
                "web_ui" to CameraCapabilityCatalog.AppStatus.Planned,
                "push_notify" to CameraCapabilityCatalog.AppStatus.Planned,
                "nsd_mdns" to CameraCapabilityCatalog.AppStatus.Partial,
            )
        for ((id, st) in tetherFeatures) {
            add(row("tether.$id", "Tether $id", "Fleet", status = st))
        }

        val cameraxModes =
            listOf("AUTO", "PORTRAIT", "BEAUTY", "HDR", "NIGHT", "BOKEH", "FACE_RETOUCH")
        for (mode in cameraxModes) {
            add(
                row(
                    "camerax.${mode.lowercase()}",
                    "CameraX $mode",
                    "CameraX",
                    CameraCapabilityCatalog.SourceLayer.CameraX,
                    status = if (mode in listOf("NIGHT", "BOKEH", "HDR")) CameraCapabilityCatalog.AppStatus.ProbeOnly else CameraCapabilityCatalog.AppStatus.Planned,
                    visibility = CameraCapabilityCatalog.VisibilityPolicy.ShowDisabledEngineering,
                ),
            )
        }

        val legacyRows =
            listOf(
                "legacy.camera1" to "Camera1 API probe",
                "legacy.mediarecorder_hfr_cap" to "MediaRecorder HFR cap probe",
                "product.toolbox" to "OpenCamera-style toolbox",
                "gallery.lut_preview" to "Gallery LUT preview",
                "video.anamorphic" to "Anamorphic desqueeze",
            )
        for ((id, name) in legacyRows) {
            add(row(id, name, "Legacy", status = CameraCapabilityCatalog.AppStatus.Planned, visibility = CameraCapabilityCatalog.VisibilityPolicy.ShowDisabledEngineering))
        }

        val sessionRows =
            listOf(
                "stream_hints" to CameraCapabilityCatalog.AppStatus.ProbeOnly,
                "physical_pin" to CameraCapabilityCatalog.AppStatus.ProbeOnly,
                "raw10_tier" to CameraCapabilityCatalog.AppStatus.ProbeOnly,
                "regular_hs" to CameraCapabilityCatalog.AppStatus.Shipped,
                "logical_multicam" to CameraCapabilityCatalog.AppStatus.Partial,
            )
        for ((suffix, st) in sessionRows) {
            add(row("session.$suffix", "Session $suffix", "Fleet", status = st))
        }

        for (mm in listOf(14, 23, 35, 50, 73, 85, 150)) {
            add(row("focal.slot.$mm", "Focal slot ${mm}mm eq.", "Lens", keywords = "focal $mm"))
        }

        val encoderProbes =
            listOf(
                "c2.qti.avc.encoder",
                "c2.qti.hevc.encoder",
                "c2.android.av1.encoder",
                "c2.qti.av1.encoder",
                "c2.android.vp9.encoder",
                "OMX.google.h264.encoder",
            )
        for (enc in encoderProbes) {
            val slug = enc.replace('.', '_')
            add(row("encoder.$slug", "Encoder $enc", "Video", CameraCapabilityCatalog.SourceLayer.HalEncoder, enc, CameraCapabilityCatalog.AppStatus.ProbeOnly, visibility = CameraCapabilityCatalog.VisibilityPolicy.ShowDisabledEngineering))
        }

        for (profile in listOf("hdr10", "hlg10", "bt709", "pq", "flat")) {
            add(row("video.color.$profile", "Video color $profile", "Video", keywords = profile, status = CameraCapabilityCatalog.AppStatus.Partial))
        }

        for (dial in listOf("h", "bkt", "m", "macro", "qr", "dual", "night", "bokeh")) {
            add(row("dial.$dial", "Command dial $dial", "Still capture", keywords = "dial $dial"))
        }

        add(row("video.delivery_honesty", "Delivery vs picker honesty", "Video", status = CameraCapabilityCatalog.AppStatus.Partial))
        add(row("preview.measured_fps", "Preview measured FPS", "Preview HUD", status = CameraCapabilityCatalog.AppStatus.Shipped))
        add(row("lens.fleet_focal_row", "Fleet adaptive focal row", "Lens", surfacing = listOf("focal_row")))
        add(row("policy.os_flavor_plugin", "OS flavor fleet policy", "Fleet", status = CameraCapabilityCatalog.AppStatus.Partial))
        add(row("product.obtainium_updates", "Obtainium update channel", "Fleet", status = CameraCapabilityCatalog.AppStatus.Shipped))
        add(row("fleet.macro_benchmark_export", "Fleet macro benchmark CSV", "Fleet", status = CameraCapabilityCatalog.AppStatus.Partial, surfacing = listOf("engineering_hub")))
        add(row("workflow.presets_from_matrix", "Workflow presets from matrix", "Fleet", status = CameraCapabilityCatalog.AppStatus.Partial))
        add(row("preview.ae_lock", "Preview AE lock", "Preview HUD", surfacing = listOf("qs_grid")))
        add(row("still.independent_tonal", "Independent tonal still", "Still capture", status = CameraCapabilityCatalog.AppStatus.Partial))
        add(row("video.side_panels", "Video side panels", "Video", status = CameraCapabilityCatalog.AppStatus.Shipped))
        add(row("fleet.regression_pack", "Fleet regression pack", "Fleet", status = CameraCapabilityCatalog.AppStatus.Shipped, surfacing = listOf("engineering_hub")))
        add(row("tether.nsd", "NSD tether registrar", "Fleet", status = CameraCapabilityCatalog.AppStatus.Partial))
        add(row("hud.readout_strip", "Preview readout strip", "Preview HUD", status = CameraCapabilityCatalog.AppStatus.Shipped))
        add(row("video.time_lapse", "Time lapse encoder", "Video", status = CameraCapabilityCatalog.AppStatus.Shipped))
        add(row("still.proshot_leaf", "ProShot leaf DNG path", "Still capture", status = CameraCapabilityCatalog.AppStatus.Partial))
    }
}
