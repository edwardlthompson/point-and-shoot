# Batch Commands — Agent Registry

> Technical catalog for agents and maintainers. **Humans:** start with [docs/help/BATCH_COMMANDS.md](help/BATCH_COMMANDS.md).

28 slash commands: **23 atomic** workflows + **5 super** orchestrators. Bare-word triggers: `.cursor/rules/batch-commands.mdc`.

Upstream template: [agent-project-bootstrap](https://github.com/edwardlthompson/agent-project-bootstrap) v0.15.0 (`/.template-version`).

## Super commands

| Command | Chain | Cursor mode | Push? |
|---------|-------|-------------|-------|
| `/bootstrap` | init → prune → setup → gates | Agent | No |
| `/verify` | docs → gates → ci | Agent | No |
| `/build` | plan → approval → feature → gates | Plan then Agent | No |
| `/ship` | prerelease → push → regress (stable **Point & Shoot x.y.z**, signed APK) | Agent | **Yes** |
| `/maintain` | triage → dependabot → audit | Agent | No |

## Atomic commands

| Command | P&S scripts / docs | Super parent |
|---------|-------------------|--------------|
| `/audit` | Tier 0 + Tier 2 + `CODE_REVIEW.md.example` | maintain |
| `/debug` | `AGENT_REGRESSION_MEMORY.md`, Debug Mode | — |
| `/gates` | `pns_validate_bootstrap`, `pns_local_dev_parallel`, `pns_prerelease_gate -SkipGradle` | bootstrap, verify, build |
| `/triage` | `pns_check_github_ci`, Dependabot | maintain |
| `/dependabot` | `gh` + Tier 2 after merges | maintain |
| `/push` | `pns_changelog_gate`, `pns_github_release.ps1` | ship |
| `/prerelease` | `pns_prerelease_gate.ps1` | ship |
| `/regress` | `pns_repro_build_verify`, `pns_sbom -Verify`, fixtures | ship |
| `/feature` | Tier 0/2 + USB capture→chrome sequential | build |
| `/fix` | `pns_watch_agent_gates -Autofix` | build |
| `/init` | `AGENT_MEMORY.md`, bootstrap validate | bootstrap |
| `/prune` | ADR-0009 module check + `assembleDebug` | bootstrap |
| `/ci` | `pns_check_github_ci.ps1` | verify |
| `/docs` | `pns_template_doc_link_check`, `pns_changelog_gate` | verify |
| `/upgrade` | Diff upstream bootstrap; update `.template-version` | maintain |
| `/setup` | `pns_adb_device.env`, `gh` settings | bootstrap |
| `/plan` | BUILD_PLAN + KB + ADR | build |
| `/restore` | `.cursor-session-state` | — |
| `/compact` | `.cursor-session-state` | — |
| `/scope` | `MULTI_AGENT_PARALLEL_ORCHESTRATION.md`, worktree bootstrap | — |
| `/cleanup` | Archive ✅ BUILD_PLAN rows → `BUILD_PLAN_COMPLETED.md` | — |
| `/ideas` | Ranked backlog; do not implement unless the user names a number | — |
| `/coach` | Next recommended action now (BUILD_PLAN + industry reason) | — |

## Decision tree

```
New agent session?  → /bootstrap  (or /init if context known)
Changed code?       → /verify (or /docs if docs-only)
New feature?        → /build  (or /fix if gates fail)
Ready to publish?   → /ship   (or /prerelease then /push)
Weekly maintenance? → /maintain (heavy) or /triage + /verify (light)
Bug with evidence?  → /debug  (not /audit)
Long chat session?  → /compact before clear · /restore after
Parallel agents?    → /scope before dispatch
```

## `/verify` vs `/gates` vs `/push` vs `/ship`

| Command | Scope |
|---------|-------|
| `/gates` | Local scripts only — no CI poll |
| `/verify` | docs + gates + CI (pre-merge) |
| `/push` | Release commit workflow with explicit push approval |
| `/ship` | prerelease + push + regress (preferred publish path) |

## File layout

| Path | Role |
|------|------|
| `.cursor/commands/*.md` | Slash command bodies |
| `.cursor/rules/batch-commands.mdc` | Bare-word → same files |
| `docs/help/BATCH_COMMANDS.md` | Human cheat sheet |
| `CODE_REVIEW.md.example` | Audit output template |
| `RELEASE_NOTES.md.example` | Release draft template |
| `scripts/pns_check_batch_commands.ps1` | Registry ↔ filesystem sync |

Validation: `.\scripts\pns_check_batch_commands.ps1` (wired in `pns_validate_bootstrap.ps1`).

Gate router: `.\scripts\pns_watch_agent_gates.ps1` · Tier matrix: `docs/LOCAL_FIRST_DEV_LOOP.md`.
