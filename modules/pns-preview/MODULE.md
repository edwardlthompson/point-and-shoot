# :pns-preview

**Gradle:** `modules/pns-preview` · **Package:** `dev.pointandshoot.preview`

## Role

Preview session create seams: regular/HFR orchestrators, surface policy, context diag, `Camera2SessionCompat`, automation extras.

## Locks

- Preview chrome layout unchanged ([`preview-chrome-ui-lock.mdc`](../../.cursor/rules/preview-chrome-ui-lock.mdc))
- `setGeometry` only from `PreviewMainViewport` ([`AGENTS.md`](../../AGENTS.md))

## Dependencies

- `:pns-core`, `:pns-fleet`, `:pns-capture`

## Remains in `:app`

`PreviewSessionJpegCompanion`, vendor/macro session parameters, HFR output list (`HudSettings` / video effects coupling), GLES mock screens.

## Gates

USB `pns_capture_pipeline_verify.ps1` then `pns_chrome_ux_gate.ps1` (sequential)
