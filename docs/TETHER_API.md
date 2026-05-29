# Tether API (HTTP control)

Point & Shoot exposes a minimal JSON-over-HTTP API for desktop / companion control while the preview engine is running.

## Endpoints

| Method | Path | Description |
|--------|------|-------------|
| `GET` | `/status` | Session snapshot (capture readiness, camera id, fps, flash mode) |
| `POST` | `/capture` | Fire one still capture (same path as in-app shutter when allowed) |
| `POST` | `/flash?mode=auto\|on\|torch\|off` | Set preview flash mode (rear camera) |

All responses are `application/json`. Unknown paths return **404** with `{"ok":false,"error":"not_found"}`.

### `GET /status` example

```json
{
  "ok": true,
  "canCaptureStill": true,
  "primaryPhoto": true,
  "cameraId": "2",
  "fps": 60,
  "flashMode": "Auto"
}
```

### `POST /capture` example

```json
{"ok": true, "action": "capture"}
```

## Transports

| Mode | Setting | Bind address | Discovery |
|------|---------|--------------|-----------|
| **Loopback (USB)** | `tetheredCaptureEnabled` | `127.0.0.1:28765` | `adb reverse tcp:28765 tcp:28765` |
| **LAN / Wi‑Fi Direct** | `wifiDirectTetherEnabled` | `0.0.0.0:28765` | mDNS `_pns-tether._tcp` (service name `PNS-Tether`) |

Port **28765** is fixed (`TetheredCaptureServer.DEFAULT_PORT`). LAN mode also keeps the loopback listener for `adb reverse`.

## Permissions (LAN mode)

- `ACCESS_FINE_LOCATION` (all API levels — required for NSD on many OEM stacks)
- `NEARBY_WIFI_DEVICES` (Android 13+)

Requested when enabling **Wi‑Fi Direct tether (LAN)** in Settings → Pro capture.

## Log tags

| Tag | Needle |
|-----|--------|
| `PNS.Tether` | `listening host=`, `wifiDirectBound=true`, `POST /capture` |
| `PNS.TetherNsd` | `nsdRegistered` |
| `PNS.AdbValidation` | automation mirrors of the above |

## ADB automation

```text
# Loopback only
adb shell am start -n dev.pointandshoot/.MainActivity --es pns_screen preview --ez pns_preview_tether true

# LAN bind + NSD (gate: logcat wifiDirectBound=true)
adb shell am start -n dev.pointandshoot/.MainActivity --es pns_screen preview --ez pns_preview_wifi_direct_tether true
```

Grant permissions first on a cold install:

```text
adb shell pm grant dev.pointandshoot android.permission.ACCESS_FINE_LOCATION
adb shell pm grant dev.pointandshoot android.permission.NEARBY_WIFI_DEVICES
```

## Related

- Loopback gate: `scripts/pns_pro_features_test.ps1`
- LAN media pull (read-only gallery): port **28766** — `LanMediaTransferServer` (separate from tether control)
- Code: `TetheredCaptureServer.kt`, `TetherNsdRegistrar.kt`, `WifiDirectTetherSupport.kt`
