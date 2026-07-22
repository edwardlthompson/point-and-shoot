# Bootstrap template map (agent-project-bootstrap → Point & Shoot)

Maps the **agent-project-bootstrap** template (**v0.15.0**) to this repository. **Do not** maintain parallel copies of the same rules — update the canonical path in place.

**Alignment record:** [`BOOTSTRAP_ALIGNMENT.md`](BOOTSTRAP_ALIGNMENT.md) · pin: [`.template-version`](../.template-version)

## File aliases

| Template | P&S path | Notes |
|----------|----------|-------|
| `.cursorrules` | [`.cursor/rules/*.mdc`](../.cursor/rules/) | Fragmented subsystem locks + generic bootstrap rules |
| `docs/START_HERE.md` | [`docs/START_HERE.md`](START_HERE.md) | Reference mode |
| `docs/CURSOR_MODES.md` | [`docs/CURSOR_MODES.md`](CURSOR_MODES.md) | Ask/Plan/Agent/Debug |
| `docs/FOR_AGENTS.md` | [`docs/FOR_AGENTS.md`](FOR_AGENTS.md) | Agent protocol |
| `COMPLETED_TASKS.md` | [`BUILD_PLAN_COMPLETED.md`](../BUILD_PLAN_COMPLETED.md) + root [`COMPLETED_TASKS.md`](../COMPLETED_TASKS.md) stub |
| `DECISION_LOG.md` | [`DECISION_LOG.md`](../DECISION_LOG.md) + [`docs/adr/`](adr/) |
| `HUMAN_BACKLOG.md` | [`HUMAN_BACKLOG.md`](../HUMAN_BACKLOG.md) | Deferred HUMAN/ADB |
| `validate-bootstrap.sh` | [`scripts/pns_validate_bootstrap.ps1`](../scripts/pns_validate_bootstrap.ps1) | Host file + label checks |
| `watch-agent-gates.sh` | [`scripts/pns_watch_agent_gates.ps1`](../scripts/pns_watch_agent_gates.ps1) | Tier 0/1/2/USB step router |
| `check-template-updates.ps1` | [`scripts/pns_check_template_updates.ps1`](../scripts/pns_check_template_updates.ps1) | Upstream release poll |
| `check-repo-hygiene.ps1` | [`scripts/pns_check_repo_hygiene.ps1`](../scripts/pns_check_repo_hygiene.ps1) | Hygiene smoke |
| `.cursor/commands/*.md` | [`.cursor/commands/`](../.cursor/commands/) | **26** batch slash commands |
| `batch-commands.mdc` | [`.cursor/rules/batch-commands.mdc`](../.cursor/rules/batch-commands.mdc) | Bare-word triggers |
| `check-batch-commands.sh` | [`scripts/pns_check_batch_commands.ps1`](../scripts/pns_check_batch_commands.ps1) | Registry ↔ filesystem |
| `check-github-ci.ps1` | [`scripts/pns_check_github_ci.ps1`](../scripts/pns_check_github_ci.ps1) | Toolchain verify + Security scan + CodeQL |
| `docs/BATCH_COMMANDS.md` | [`docs/BATCH_COMMANDS.md`](BATCH_COMMANDS.md) + [`docs/help/BATCH_COMMANDS.md`](help/BATCH_COMMANDS.md) | Agent + human cheat sheets |
| `docs/SECURITY_TRIAGE.md` | [`docs/SECURITY_TRIAGE.md`](SECURITY_TRIAGE.md) | Weekly CVE playbook |
| `modules/android/` | [`modules/pns-*`](../modules/) | Gradle library modules (not template examples) |
| `examples/` | [`examples/golden-path/`](../examples/golden-path/) | Golden Path docs (no duplicate app code) |
| `.cursor-session-state.example.json` | [`.cursor-session-state.example.json`](../.cursor-session-state.example.json) + [`.cursor-session-state.example`](../.cursor-session-state.example) | Dual session stubs |

## Gate tiers

| Tier | Script | Role |
|------|--------|------|
| 0 | `pns_local_dev_parallel.ps1` | Parallel host (changelog, docs, F-Droid, fixtures, **bootstrap**) |
| 1 | `pns_prerelease_gate.ps1 -SkipGradle` | Full prerelease host lane |
| 2 | `pns_verify_toolchain.ps1 -RunTests` | Gradle assemble + detekt + lint + unit tests + Kover |
| 3 | USB scripts (one serial) | Capture **then** chrome sequentially |
| 4 | `pns_prerelease_gate.ps1` (+ `-IncludeUsb`) | Ship |

## Sprint labels

- `🔲 [AGENT]` / `🔲 [HUMAN]` / `🔲 [ADB]` / `🔲 [AUTO]` — owner labels
- `✅` done · `❌` blocked · never GitHub `- [ ]` checkboxes
- Sequential → Parallel → Human & device (after automation)

Full automation index: [`AGENTS.md`](../AGENTS.md).
