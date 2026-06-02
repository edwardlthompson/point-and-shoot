from pathlib import Path
p = Path("app/src/main/java/dev/pointandshoot/fleet/FleetDeviceMatrixBuilder.kt")
t = p.read_text(encoding="utf-8")
t = t.replace(
    "FleetDeviceMatrixStore.save(context, built.root, rotatePreviousToHistory = forceRescan)",
    "FleetDeviceMatrixStore.saveWithArtifacts(context, CameraCapabilityCatalogBuilder.attachTo(built.root), rotatePreviousToHistory = forceRescan)",
)
t = t.replace(
    "FleetDeviceMatrixStore.save(context, built.root, rotatePreviousToHistory = true)",
    "FleetDeviceMatrixStore.saveWithArtifacts(context, CameraCapabilityCatalogBuilder.attachTo(built.root), rotatePreviousToHistory = true)",
)
p.write_text(t, encoding="utf-8")
print("FleetDeviceMatrixBuilder.kt updated")
