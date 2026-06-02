from pathlib import Path
p = Path("CHANGELOG.md")
t = p.read_text(encoding="utf-8")
if "Sprint 17.1" not in t:
    t = t.replace(
        "## Unreleased\n\n_(Nothing yet — see [0.14.0-beta.4].)_",
        "## Unreleased\n\n### Added\n\n- **Sprint 17.1 — Fleet capability catalog slice** — `capabilityCatalog` + `catalogVersion` on `fleet_device_matrix.json`; `files/fleet_device_capability_summary.md` (ADB-pullable human SoT); `CameraCapabilityCatalog` / `CameraCapabilityCatalogBuilder` / `FleetCapabilitySummaryMarkdown`; extended deep caps stream map (JPEG/RAW/MR sizes, aspect ratios, face detect modes on structured cameras); `pns_fleet_matrix_scan.ps1` pulls summary markdown.\n\n_(See [0.14.0-beta.4] for prior release.)_",
    )
    p.write_text(t, encoding="utf-8")
    print("CHANGELOG updated")
