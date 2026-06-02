from pathlib import Path
p = Path("app/src/main/java/dev/pointandshoot/fleet/FleetDeviceMatrix.kt")
t = p.read_text(encoding="utf-8")
if "KEY_CAPABILITY_CATALOG" not in t:
    t = t.replace(
        'const val KEY_APPENDIX = "appendix"',
        'const val KEY_CAPABILITY_CATALOG = "capabilityCatalog"\n    const val KEY_CATALOG_VERSION = "catalogVersion"\n    const val KEY_APPENDIX = "appendix"',
    )
    p.write_text(t, encoding="utf-8")
    print("FleetDeviceMatrix.kt updated")
