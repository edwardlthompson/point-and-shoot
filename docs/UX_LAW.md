# UX law (fifteen checks)

Machine copy: `.cursor/rules/ux-law.mdc`. Rule YAML: `argus/ux/rules/*.yaml`.

## Web

| Id | Meaning |
|----|---------|
| `UX-RAW-HEX` | No raw hex in UI sources (tokens only) |
| `UX-EMOJI-ICON` | No emoji as icons |
| `UX-VAGUE-BUTTON` | Buttons need clear labels |
| `UX-OUTLINE-NONE` | Do not remove focus outlines without replacement |
| `UX-Z-INDEX-MAGIC` | No unexplained z-index magic numbers |
| `UX-MISSING-FOCUS` | Interactive elements need visible focus |
| `UX-LABEL-NONE` | Form controls need labels |
| `UX-TOUCH-TARGET` | Touch targets meet minimum size |
| `UX-COLOR-ONLY-STATE` | State not conveyed by color alone |

## Android Compose

| Id | Meaning |
|----|---------|
| `UX-COMPOSE-CONTENT-DESC` | Icons/images need contentDescription |
| `UX-COMPOSE-TARGET-DP` | Touch targets in dp meet minimum |
| `UX-COMPOSE-HARDCODED-COLOR` | No hardcoded Color(0x…) in UI |
| `UX-COMPOSE-LITERAL-STRING` | No user-facing string literals |
| `UX-COMPOSE-TOUCH-TARGET` | Clickable meet touch minimum |
| `UX-COMPOSE-UNSTABLE-KEY` | Lazy keys must be stable |

## Exclusions

`BASE_EXCLUDES`: `**/*.md`, `*.mdx`, `*.txt`, `LICENSE*`, `docs/**`, `.cursor/rules/**`. Locale paths are per-rule.

## Baseline

Empty `ux-baseline.json` (`{}`) means no baseline yet. First real scan may open a baseline PR. Baselined findings exit 0; new drift fails.
