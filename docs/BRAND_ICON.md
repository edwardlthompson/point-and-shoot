# Brand icon contract

Canonical app icon for Golden Path children: `branding/assets/app-icon-1024.png` (512×512 PNG).

## Metadata

[`branding/brand.json`](../branding/brand.json) is the machine contract:

| Field | Meaning |
|-------|---------|
| `icon` | Repo-relative path to the PNG |
| `icon_sha256` | SHA-256 of that file — inheritance detector |
| `source` | `owned` \| `inherited` \| `missing` \| `placeholder` |
| `brand_name` | Product display name |
| `tokens` | Pointer to design tokens (`config/tokens.json`) |

There is **no mutable flag** for “rebranded.” If `icon_sha256` still matches the template’s hash, the child has not rebranded (`inherited`).

## Resolution order (first hit wins)

1. `branding/brand.json` → `icon`
2. `branding/assets/app-icon-1024.png`
3. Ecosystem-forced paths (Android mipmaps, iOS AppIcon, Tauri/Electron icons, webmanifest, freedesktop hicolor)

## Gate states

| State | Result |
|-------|--------|
| **owned** | Pass — hash matches file and `source` is `owned` (or child replaced icon + hash) |
| **inherited** | Fail — SHA still equals template stock hash |
| **missing** | Fail — no resolvable PNG |
| **placeholder** | Fail — under 400 bytes or stub SVG |

`BRAND-ICON-MISSING` is agent-class: write a design brief into the PR body; agents design a mark. **No generated placeholders.**

## Commands

```bash
python3 scripts/check-brand-icon.py
python3 scripts/check-brand-icon.py --ps1   # via scripts/check-brand-icon.ps1
```
