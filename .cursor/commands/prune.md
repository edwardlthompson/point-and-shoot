# Stack / module verification

Confirm Android modular layout matches `docs/adr/0009-modular-boundaries.md`:

- `modules/pns-{core,fleet,capture,preview}/MODULE.md` present
- `examples/golden-path/` docs (no duplicate app tree)
- Hub Compose + session glue remain in `:app` per ADR

```powershell
.\scripts\pns_validate_bootstrap.ps1
.\scripts\pns_gradlew.ps1 :app:assembleDebug
```

Begin now.
