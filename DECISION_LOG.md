# Decision log (ADR index)

One-page index of [Architecture Decision Records](docs/adr/README.md). **Append only** — edit ADR files in place only to mark *Superseded*; never rewrite accepted decision text.

| ADR | Date | Title | Status |
|-----|------|-------|--------|
| [0001](docs/adr/0001-core-architecture.md) | 2026-06-12 | Compose monolith + pure JVM helpers; probe hub Golden Path | Accepted |
| [0002](docs/adr/0002-dng-save-no-exif-rewrite.md) | 2026-06-12 | No full-file EXIF rewrite on DNG after save | Accepted |
| [0003](docs/adr/0003-dodge-tele-focal-routing.md) | 2026-06-12 | Dodge tele 73/85/150 on physical mid-tele sensor | Accepted |
| [0004](docs/adr/0004-fleet-matrix-sot.md) | 2026-06-12 | `fleet_device_matrix.json` over legacy per-SKU gates | Accepted |
| [0005](docs/adr/0005-apache-2-license.md) | 2026-06-12 | Apache-2.0 (not MIT) | Accepted |
| [0006](docs/adr/0006-mlkit-face-detection-exception.md) | 2026-06-12 | ML Kit face-detection FOSS audit exception | Superseded by 0011 |
| [0007](docs/adr/0007-code-style-gate.md) | 2026-06-12 | Detekt-only style; Kover 40% scoped floor; no androidTest | Accepted |
| [0008](docs/adr/0008-mock-mode-cold-restart.md) | 2026-06-12 | Gallery-return cold restart; defer T.14 mock mode | Accepted |
| [0009](docs/adr/0009-modular-boundaries.md) | 2026-06-17 | Gradle `modules/pns-*` libraries; hub UI + capture session glue stays in `:app` | Accepted |
| [0010](docs/adr/0010-extension-handoff-wave-c.md) | 2026-06-20 | Isolated extension handoff for Wave C HDR/AUTO (no inline session merge) | Accepted |
| [0011](docs/adr/0011-foss-play-distribution-flavors.md) | 2026-11-16 | foss / play flavors; F-Droid has no ML Kit | Accepted |

### 2026-07-22 — Bootstrap template v0.15.0 Reference alignment

- **Decision:** Align agent process/tooling with [agent-project-bootstrap v0.15.0](https://github.com/edwardlthompson/agent-project-bootstrap/tree/v0.15.0) in **Reference** mode; keep Apache-2.0, P&S CRITICAL locks, and existing CI (`toolchain-verify` / `security-scan` / CodeQL).
- **Status markers:** Keep `🔲`/`✅`/`❌` (document deviation from upstream `🟡`/`🔴`).
- **CI:** Additive Dependency Review + non-blocking Scorecard only; no release-please / stale import.
- **Record:** [`docs/BOOTSTRAP_ALIGNMENT.md`](docs/BOOTSTRAP_ALIGNMENT.md).

**Related (not ADRs):** regression ledger [`docs/AGENT_REGRESSION_MEMORY.md`](docs/AGENT_REGRESSION_MEMORY.md) · curated index [`KNOWLEDGE_BASE.md`](KNOWLEDGE_BASE.md) · active plan [`BUILD_PLAN.md`](BUILD_PLAN.md) Milestone H + **H.CRI-0…7**.

### 2026-11-16 — Multiplatform migration Phases 0–3 (not an ADR yet)

- **Product:** iOS = full camera (adapt chrome, ProRAW OK); desktop = companion (tether / MJPEG / UVC host / library / presets). [`docs/migration/INVENTORY.md`](docs/migration/INVENTORY.md) Decisions.
- **Stack (file when Phase 4 starts as ADR-0011+):** KMP core + Compose Multiplatform companion/library UI + native shooting pipelines. [`docs/migration/TECH_DECISION.md`](docs/migration/TECH_DECISION.md).
- **Brief:** [`docs/migration/DESIGN_BRIEF.md`](docs/migration/DESIGN_BRIEF.md) binding; deviations need Edward.

*Milestone H — link gate: `scripts/pns_template_doc_link_check.ps1`*
