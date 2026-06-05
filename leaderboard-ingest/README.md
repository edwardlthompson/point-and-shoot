# Leaderboard ingest (Render)

FastAPI service that accepts signed parity submissions from the app (`FleetLeaderboardSubmit`).

## Deploy

1. Create a Render **Web Service** from this directory (`Dockerfile` included).
2. Set env `LEADERBOARD_INGEST_URL` in the app / hub (or `scripts/pns_adb_device.env` for local testing).
3. Add release APK signing cert SHA-256 fingerprints to `config/signing_pins.json`:

```json
{
  "allowedCertSha256": ["AB:CD:...:EF"],
  "note": "Debug builds use a different cert; pin release only for community lane."
}
```

Obtain cert hash: `keytool -printcert -jarfile app-release.apk | findstr SHA256`

4. Empty `allowedCertSha256` allows all certs (maintainer-only preview); populate before opening community submit.

## Endpoints

- `GET /health` — liveness
- `POST /v1/submit` — JSON body `pns.leaderboard_submission.v1`

Approved payloads land under `submissions/pending/` on the service volume; merge into site via `pns_leaderboard_site_publish.ps1 -MergeSubmissions`.
