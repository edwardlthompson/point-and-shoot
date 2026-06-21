# Decision log (ADR index)

One-page index of [Architecture Decision Records](docs/adr/README.md). **Append only** — edit ADR files in place only to mark *Superseded*; never rewrite accepted decision text.

| ADR | Date | Title | Status |
|-----|------|-------|--------|
| [0001](docs/adr/0001-core-architecture.md) | 2026-06-12 | Compose monolith + pure JVM helpers; probe hub Golden Path | Accepted |
| [0002](docs/adr/0002-dng-save-no-exif-rewrite.md) | 2026-06-12 | No full-file EXIF rewrite on DNG after save | Accepted |
| [0003](docs/adr/0003-dodge-tele-focal-routing.md) | 2026-06-12 | Dodge tele 73/85/150 on physical mid-tele sensor | Accepted |
| [0004](docs/adr/0004-fleet-matrix-sot.md) | 2026-06-12 | `fleet_device_matrix.json` over legacy per-SKU gates | Accepted |
| [0005](docs/adr/0005-apache-2-license.md) | 2026-06-12 | Apache-2.0 (not MIT) | Accepted |
| [0006](docs/adr/0006-mlkit-face-detection-exception.md) | 2026-06-12 | ML Kit face-detection FOSS audit exception | Accepted |
| [0007](docs/adr/0007-code-style-gate.md) | 2026-06-12 | Detekt-only style; Kover 40% scoped floor; no androidTest | Accepted |
| [0008](docs/adr/0008-mock-mode-cold-restart.md) | 2026-06-12 | Gallery-return cold restart; defer T.14 mock mode | Accepted |
| [0009](docs/adr/0009-modular-boundaries.md) | 2026-06-17 | Gradle `modules/pns-*` libraries; hub UI + capture session glue stays in `:app` | Accepted |
| [0010](docs/adr/0010-extension-handoff-wave-c.md) | 2026-06-20 | Isolated extension handoff for Wave C HDR/AUTO (no inline session merge) | Accepted |

**Related (not ADRs):** regression ledger [`docs/AGENT_REGRESSION_MEMORY.md`](docs/AGENT_REGRESSION_MEMORY.md) · curated index [`KNOWLEDGE_BASE.md`](KNOWLEDGE_BASE.md) · active plan [`BUILD_PLAN.md`](BUILD_PLAN.md) Milestone H + **H.CRI-0…7**.

*Milestone H — link gate: `scripts/pns_template_doc_link_check.ps1`*
