from pathlib import Path
p = Path("BUILD_PLAN.md")
t = p.read_text(encoding="utf-8")
replacements = [
    ("- [ ] **[AGENT]** Schema **v1.1**", "- [x] **[AGENT]** Schema **v1.1**"),
    ("- [ ] **[AGENT]** `CameraCapabilityCatalog.kt`", "- [x] **[AGENT]** `CameraCapabilityCatalog.kt`"),
    ("- [ ] **[AGENT]** `FleetCapabilitySummaryMarkdown.kt`", "- [x] **[AGENT]** `FleetCapabilitySummaryMarkdown.kt`"),
    ("- [ ] **[AGENT]** Extend `DeepCapsProbeCore.streamConfigToJson`", "- [x] **[AGENT]** Extend `DeepCapsProbeCore.streamConfigToJson`"),
    ("- [ ] **[AGENT]** Extend `scripts/pns_fleet_matrix_scan.ps1`", "- [x] **[AGENT]** Extend `scripts/pns_fleet_matrix_scan.ps1`"),
    ("- [ ] **[AGENT]** `docs/CAMERA_CAPABILITY_CATALOG.md`", "- [x] **[AGENT]** `docs/CAMERA_CAPABILITY_CATALOG.md`"),
]
for a,b in replacements:
    t = t.replace(a,b,1)
p.write_text(t, encoding="utf-8")
print("BUILD_PLAN 17.1 ticks updated")
