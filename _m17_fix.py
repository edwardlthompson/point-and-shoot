from pathlib import Path
p = Path("app/src/main/java/dev/pointandshoot/fleet/CameraCapabilityCatalogBuilder.kt")
t = p.read_text(encoding="utf-8")
t = t.replace("import dev.pointandshoot.CapabilityGate", "import dev.pointandshoot.Feature")
t = t.replace("CapabilityGate.Feature", "Feature")
p.write_text(t, encoding="utf-8")
print("fixed Feature import")

# test file too
p2 = Path("app/src/test/java/dev/pointandshoot/fleet/CameraCapabilityCatalogBuilderTest.kt")
t2 = p2.read_text(encoding="utf-8")
t2 = t2.replace("import dev.pointandshoot.CapabilityGate", "import dev.pointandshoot.Feature")
t2 = t2.replace("CapabilityGate.Feature", "Feature")
p2.write_text(t2, encoding="utf-8")
