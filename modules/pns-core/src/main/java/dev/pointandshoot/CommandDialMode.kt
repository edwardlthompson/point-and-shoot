package dev.pointandshoot

/**
 * Command-dial modes — shared with `:pns-preview` session diagnostics.
 * UI: [CommandDial] in `:app`.
 */
enum class CommandDialMode(val label: String, val description: String) {
    Auto("A", "Auto: continuous AE/AF — standard point-and-shoot behavior"),
    M("M", "Manual focus distance on preview (drag); ISO/shutter use readout chips, not dial M"),
    H("H", "Highlight: underexpose for bright peaks (sky / sun disk) — save-the-highlights"),
    S("S", "Snap: street preset — AF at infinity (tap preview to refocus)"),
    Monochrome("MONO", "Dedicated monochrome sensor mode (hardware B&W camera, not LUT)"),
    BKT("BKT", "Bracket: 3 / 5 / 7 RAW12 sequence with GroupingID"),
    Macro("MACRO", "Macro: close-up focus for subjects <10cm"),
    Night("NIGHT", "Night: OEM multi-frame stacking for low light (requires OEM extension support)"),
    Bokeh("BOKEH", "Bokeh: OEM portrait with hardware background blur (requires OEM extension support)"),
    Qr("QR", "QR: scan codes on the live preview (ZXing)"),
    Dual("DUAL", "Dual: rear + front side-by-side into one MP4 (video only)"),
}
