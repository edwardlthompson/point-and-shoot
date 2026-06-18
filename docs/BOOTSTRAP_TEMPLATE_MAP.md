# Bootstrap template map (agent-project-bootstrap → Point & Shoot)

Maps the **agent-project-bootstrap** template to this repository. **Do not** maintain parallel copies of the same rules — update the canonical path in place.

## File aliases

| Template | P&S path | Notes |
|----------|----------|-------|
| `.cursorrules` | [`.cursor/rules/*.mdc`](../.cursor/rules/) | Fragmented subsystem locks |
| `COMPLETED_TASKS.md` | [`BUILD_PLAN_COMPLETED.md`](../BUILD_PLAN_COMPLETED.md) + root [`COMPLETED_TASKS.md`](../COMPLETED_TASKS.md) stub |
| `DECISION_LOG.md` | [`DECISION_LOG.md`](../DECISION_LOG.md) + [`docs/adr/`](../docs/adr/) |
| `validate-bootstrap.sh` | [`scripts/pns_validate_bootstrap.ps1`](../scripts/pns_validate_bootstrap.ps1) | Host file + label checks |
| `watch-agent-gates.sh` | [`scripts/pns_watch_agent_gates.ps1`](../scripts/pns_watch_agent_gates.ps1) | Tier 0/1/2/USB step router |
| `.cursor/commands/*.md` | [`.cursor/commands/`](../.cursor/commands/) | **25** batch slash commands (v0.10.0 template) |
| `batch-commands.mdc` | [`.cursor/rules/batch-commands.mdc`](../.cursor/rules/batch-commands.mdc) | Bare-word triggers |
| `check-batch-commands.sh` | [`scripts/pns_check_batch_commands.ps1`](../scripts/pns_check_batch_commands.ps1) | Registry ↔ filesystem |
| `check-github-ci.ps1` | [`scripts/pns_check_github_ci.ps1`](../scripts/pns_check_github_ci.ps1) | Toolchain verify + Security scan + CodeQL |
| `docs/BATCH_COMMANDS.md` | [`docs/BATCH_COMMANDS.md`](BATCH_COMMANDS.md) + [`docs/help/BATCH_COMMANDS.md`](help/BATCH_COMMANDS.md) | Agent + human cheat sheets |
| `modules/*/MODULE.md` | [`modules/`](../modules/) | Gradle library modules (Sprint TM) |
| `examples/` | [`examples/golden-path/`](../examples/golden-path/) | Golden Path docs (no duplicate app code) |

## Gate tiers

| Tier | Script | Role |
|------|--------|------|
| 0 | `pns_local_dev_parallel.ps1` | Parallel host (changelog, docs, F-Droid, fixtures, **bootstrap**) |
| 1 | `pns_prerelease_gate.ps1 -SkipGradle` | Full prerelease host lane |
| 2 | `pns_verify_toolchain.ps1 -RunTests` | Gradle assemble + detekt + lint + unit tests + Kover |
| 3 | USB scripts (one serial) | Capture **then** chrome sequentially |
| 4 | `pns_prerelease_gate.ps1` (+ `-IncludeUsb`) | Ship |

## Sprint labels

- `⬜ [AGENT]` — agent-executable
- `⬜ [HUMAN]` — owner judgment / sign-off
- `✅` / `[x]` — closed with Appendix A evidence

Full automation index: [`AGENTS.md`](../AGENTS.md).
