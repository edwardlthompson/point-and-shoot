# Milestone 28.2 — Extension handoff spike

Isolated HDR `CameraExtensionSession` configure + cold return to Camera2 preview (no inline session merge).

## ADB route

```text
am start -n dev.pointandshoot/.MainActivity \
  --es pns_screen extensionhandoff \
  --ez pns_extension_handoff_return_preview true
```

Optional: `--es pns_preview_camera_id 2`

## Log needles (`PNS.AdbValidation`)

| Needle | Meaning |
|--------|---------|
| `extensionHandoff ok=true … label=HDR … returnToPreview=true` | Extension session configured |
| `extensionHandoff launching preview return route=extensionhandoff` | Cold handoff started |
| `previewReturnAfterExtensionHandoff ok=true route=extensionhandoff` | Preview engine resumed |

## Gate

```powershell
.\scripts\pns_extension_handoff_spike.ps1
```

Artifacts: `hfr-runs/extension_handoff_spike_*/gate.json`

- **PASS** — handoff + preview return needles
- **PROBE_OK_NO_EXTENSIONS** — no advertised extensions but preview return proof succeeded (exit 0)
- **FAIL** — missing preview return or configure error when extensions were expected

## ADR

[ADR-0010](adr/0010-extension-handoff-wave-c.md) — go/no-go for Wave C consumer HDR/AUTO extensions.
