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
        sweepSkip: String? = null,
        proofScript: String? = null,
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
            sweepSkipReason = sweepSkip,
            parityProofScript = proofScript,
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
                        codecId == "vp9" -> CameraCapabilityCatalog.AppStatus.Partial
                        codecId == "av1" && tierId == "8k" -> CameraCapabilityCatalog.AppStatus.Partial
                        codecId == "h264" || codecId == "hevc" -> CameraCapabilityCatalog.AppStatus.Shipped
                        else -> CameraCapabilityCatalog.AppStatus.Partial
                    }
                val proofScript =
                    when (codecId) {
                        "av1" -> "pns_av1_parity_verify.ps1"
                        "vp9" -> "pns_video_format_test.ps1"
                        else -> null
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
                        proofScript = proofScript,
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
                    CameraCapabilityCatalog.AppStatus.Partial,
                    surfacing = listOf("fps_rail"),
                    proofScript = "pns_hfr_fps_parity_verify.ps1",
                ),
            )
        }

        val stillFormats =
            listOf(
                Triple("jpeg", CameraCapabilityCatalog.AppStatus.Shipped, null),
                Triple("avif", CameraCapabilityCatalog.AppStatus.Shipped, null),
                Triple("jxl", CameraCapabilityCatalog.AppStatus.Partial, "pns_still_export_verify.ps1"),
                Triple("dng", CameraCapabilityCatalog.AppStatus.Shipped, null),
                Triple("heic", CameraCapabilityCatalog.AppStatus.Partial, "pns_still_export_verify.ps1"),
                Triple("tiff16", CameraCapabilityCatalog.AppStatus.Partial, "pns_still_export_verify.ps1"),
                Triple("motion_photo", CameraCapabilityCatalog.AppStatus.Partial, "pns_still_export_verify.ps1"),
                Triple("monochrome_sensor", CameraCapabilityCatalog.AppStatus.ProbeOnly, null),
            )
        for ((fmt, st, script) in stillFormats) {
            val sweepSkip = if (fmt == "monochrome_sensor") "probe_only_inventory" else null
            add(row("still.$fmt", "Still $fmt export", "Still capture", keywords = fmt, status = st, proofScript = script, sweepSkip = sweepSkip))
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
            val st = if (id == "macro_dedicated") CameraCapabilityCatalog.AppStatus.ProbeOnly else CameraCapabilityCatalog.AppStatus.Shipped
            val sweepSkip = if (id == "macro_dedicated") "probe_only_inventory" else null
            add(row("af.$id", name, "Face & AF", status = st, sweepSkip = sweepSkip))
        }

        val audioFeatures =
            listOf(
                Triple("aac_48k", CameraCapabilityCatalog.AppStatus.Shipped, null),
                Triple("aac_96k", CameraCapabilityCatalog.AppStatus.Shipped, null),
                Triple("wind_filter", CameraCapabilityCatalog.AppStatus.Shipped, null),
                Triple("spatial", CameraCapabilityCatalog.AppStatus.Partial, "pns_spatial_audio_verify.ps1"),
                Triple("voiceover_duck", CameraCapabilityCatalog.AppStatus.Shipped, null),
                Triple("light_compression", CameraCapabilityCatalog.AppStatus.Shipped, null),
                Triple("unprocessed", CameraCapabilityCatalog.AppStatus.Partial, "pns_audio_unprocessed_verify.ps1"),
            )
        for ((id, st, script) in audioFeatures) {
            add(row("audio.$id", "Audio $id", "Audio", status = st, surfacing = listOf("format_picker"), proofScript = script))
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
                "web_ui" to CameraCapabilityCatalog.AppStatus.ProbeOnly,
                "push_notify" to CameraCapabilityCatalog.AppStatus.ProbeOnly,
                "nsd_mdns" to CameraCapabilityCatalog.AppStatus.Partial,
            )
        for ((id, st) in tetherFeatures) {
            val sweepSkip = if (id == "web_ui" || id == "push_notify") "probe_only_inventory" else null
            add(row("tether.$id", "Tether $id", "Fleet", status = st, sweepSkip = sweepSkip))
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
                    status = CameraCapabilityCatalog.AppStatus.ProbeOnly,
                    visibility = CameraCapabilityCatalog.VisibilityPolicy.ShowDisabledEngineering,
                    sweepSkip = "probe_only_inventory",
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
            add(
                row(
                    id,
                    name,
                    "Legacy",
                    status = CameraCapabilityCatalog.AppStatus.ProbeOnly,
                    visibility = CameraCapabilityCatalog.VisibilityPolicy.ShowDisabledEngineering,
                    sweepSkip = "probe_only_inventory",
                ),
            )
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
            add(
                row(
                    "encoder.$slug",
                    "Encoder $enc",
                    "Video",
                    CameraCapabilityCatalog.SourceLayer.HalEncoder,
                    enc,
                    CameraCapabilityCatalog.AppStatus.ProbeOnly,
                    visibility = CameraCapabilityCatalog.VisibilityPolicy.ShowDisabledEngineering,
                    sweepSkip = "probe_only_inventory",
                ),
            )
        }

        for (profile in listOf("hdr10", "hlg10", "bt709", "pq", "flat")) {
            add(
                row(
                    "video.color.$profile",
                    "Video color $profile",
                    "Video",
                    keywords = profile,
                    status = CameraCapabilityCatalog.AppStatus.Partial,
                    proofScript = "pns_video_color_profile_verify.ps1",
                ),
            )
        }

        for (dial in listOf("h", "bkt", "m", "macro", "qr", "dual", "night", "bokeh")) {
            add(row("dial.$dial", "Command dial $dial", "Still capture", keywords = "dial $dial"))
        }

        add(
            CameraCapabilityCatalog.CatalogRow(
                id = "video.delivery_honesty",
                displayName = "Delivery vs picker honesty",
                category = "Video",
                sourceLayer = CameraCapabilityCatalog.SourceLayer.Product,
                appStatus = CameraCapabilityCatalog.AppStatus.Partial,
                parityProofScript = "pns_parity_proof_pack.ps1",
            ),
        )
        add(row("preview.measured_fps", "Preview measured FPS", "Preview HUD", status = CameraCapabilityCatalog.AppStatus.Shipped))
        add(row("lens.fleet_focal_row", "Fleet adaptive focal row", "Lens", surfacing = listOf("focal_row")))
        add(row("policy.os_flavor_plugin", "OS flavor fleet policy", "Fleet", status = CameraCapabilityCatalog.AppStatus.Partial))
        add(row("product.obtainium_updates", "Obtainium update channel", "Fleet", status = CameraCapabilityCatalog.AppStatus.Shipped))
        add(row("fleet.macro_benchmark_export", "Fleet macro benchmark CSV", "Fleet", status = CameraCapabilityCatalog.AppStatus.Partial, surfacing = listOf("engineering_hub")))
        add(row("workflow.presets_from_matrix", "Workflow presets from matrix", "Fleet", status = CameraCapabilityCatalog.AppStatus.Partial))
        add(row("preview.ae_lock", "Preview AE lock", "Preview HUD", surfacing = listOf("qs_grid")))
        add(row("still.independent_tonal", "Independent tonal still", "Still capture", status = CameraCapabilityCatalog.AppStatus.Partial, proofScript = "pns_independent_tonal_verify.ps1"))
        add(row("video.side_panels", "Video side panels", "Video", status = CameraCapabilityCatalog.AppStatus.Shipped))
        add(row("fleet.regression_pack", "Fleet regression pack", "Fleet", status = CameraCapabilityCatalog.AppStatus.Shipped, surfacing = listOf("engineering_hub")))
        add(row("tether.nsd", "NSD tether registrar", "Fleet", status = CameraCapabilityCatalog.AppStatus.Partial))
        add(row("hud.readout_strip", "Preview readout strip", "Preview HUD", status = CameraCapabilityCatalog.AppStatus.Shipped))
        add(row("video.time_lapse", "Time lapse encoder", "Video", status = CameraCapabilityCatalog.AppStatus.Shipped))
        add(
            CameraCapabilityCatalog.CatalogRow(
                id = "still.proshot_leaf",
                displayName = "ReferenceCam leaf DNG path",
                category = "Still capture",
                sourceLayer = CameraCapabilityCatalog.SourceLayer.Product,
                appStatus = CameraCapabilityCatalog.AppStatus.ProbeOnly,
                parityProofScript = "pns_aux_dng_capture_analyze.ps1",
                buildPlanSprint = "21.11",
                humanOnly = true,
                sweepSkipReason = "probe_only_inventory",
            ),
        )
        for ((preset, label) in listOf("street" to "street", "portrait" to "portrait", "video_log" to "video_log")) {
            add(
                CameraCapabilityCatalog.CatalogRow(
                    id = "workflow.preset.$preset",
                    displayName = "Workflow preset $label",
                    category = "Fleet",
                    sourceLayer = CameraCapabilityCatalog.SourceLayer.Product,
                    keywords = "workflow $label",
                    parityProofScript = "pns_workflow_test.ps1",
                    buildPlanSprint = "21.13",
                ),
            )
        }
    }
}
