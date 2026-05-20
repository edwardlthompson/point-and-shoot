# Sprint 13V.18 — CameraX OEM extension probe

## Build requirement

`ProcessCameraProvider` needs **`androidx.camera:camera-camera2`** on the classpath (declared in `app/build.gradle.kts`). Without it the probe logs `IllegalStateException: CameraX is not configured properly` and the USB gate sees **FAIL** (no `extensionProbeComplete`).

## Behavior

- **`CameraXExtensionProbe`** runs at app start (`PnsApplication`); logs **`PNS.CamXExtProbe`** with `extensionProbeComplete` and per-camera `extensionAvail=…`.
- **Command dial:** **Night** / **Bokeh** modes hidden when the active camera has no matching CameraX extension.

## Gate

```powershell
# Host (no device)
.\scripts\pns_camerax_extension_probe.ps1 -HostOnly

# USB
.\scripts\pns_camerax_extension_probe.ps1
```

| `gateResult` | Meaning |
|--------------|---------|
| `PASS` | At least one extension mode advertised |
| `PROBE_OK_NO_EXTENSIONS` | Probe ran; none on this ROM (expected LineageOS) |
| `FAIL` | No `extensionProbeComplete` log (crash / probe not run) |
| `HOST_PASS` | `-HostOnly` JVM + build only |

Artifact: `hfr-runs/camerax_ext_probe_*/probe.json`.
