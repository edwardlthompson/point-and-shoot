# Bootstrap alignment (agent-project-bootstrap → Point & Shoot)

**Status:** Aligned to upstream **v0.15.0** (Reference mode) · Prior pin: **0.10.0** (Milestone T / Sprint TM)  
**Upstream:** https://github.com/edwardlthompson/agent-project-bootstrap/tree/v0.15.0  
**Map:** [`BOOTSTRAP_TEMPLATE_MAP.md`](BOOTSTRAP_TEMPLATE_MAP.md)

This file is the gap analysis + decision record for the 0.10.0 → 0.15.0 migration. Do not re-bootstrap the app; preserve camera CRITICAL locks and product code.

---

## Gap analysis (Phase 0)

### Already matched (kept)

- Core agent surface: `AGENTS.md`, `AGENT_MEMORY.md`, `BUILD_PLAN.md`, `BUILD_PLAN_COMPLETED.md`, `COMPLETED_TASKS.md`, `DECISION_LOG.md` + `docs/adr/`, `KNOWLEDGE_BASE.md`, `PROMPT_LIBRARY.md`, `SECURITY.md`, `CONTRIBUTING.md`
- 25 → 26 batch commands (added `/cleanup`), `batch-commands.mdc`, `docs/BATCH_COMMANDS.md`, `docs/help/BATCH_COMMANDS.md`
- Host gate: `scripts/pns_validate_bootstrap.ps1` (Tier 0)
- Local-first tiers: `docs/LOCAL_FIRST_DEV_LOOP.md`
- Project CRITICAL `.cursor/rules/*` (DNG, tele, fleet, chrome, etc.)
- CI: `toolchain-verify`, `security-scan`, `codeql-analysis`, Dependabot, SBOM monthly
- No root `.cursorrules` (fragmented `.mdc` rules)

### Was missing (added in this alignment)

| Area | Added |
|------|--------|
| Entrypoints | `docs/START_HERE.md`, `CURSOR_MODES.md`, `FOR_AGENTS.md`, `INITIALIZATION_PROMPT.md`, `UPGRADING_FROM_TEMPLATE.md` |
| Security process | `docs/SECURITY_TRIAGE.md` |
| Hygiene | `.cursorignore`, `.env.example`, `HUMAN_BACKLOG.md`, `CODE_OF_CONDUCT.md` |
| Metadata | `TEMPLATE_INDEX.json`, `.template-update.json`, `.template-version` → `0.15.0` |
| Session | `.cursor-session-state.example.json` (alongside `.cursor-session-state.example`) |
| Generic rules | `cursor-modes`, `destructive-ops`, `local-compute`, `read-before-write`, `repo-hygiene`, `security-triage`, `ci-gates`, `testing`, `windows-encoding`, `core-directives`, `foss-compliance` |
| Commands | `.cursor/commands/cleanup.md` |
| Scripts | `pns_check_template_updates.ps1`, `pns_check_repo_hygiene.ps1` |
| CI (additive) | `dependency-review.yml`, non-blocking `scorecard.yml` |

### Locked conflict decisions

| Conflict | Decision |
|----------|----------|
| MIT (template) vs Apache-2.0 (ADR-0005) | **Keep Apache-2.0** |
| Upstream status `🟡/🔴` vs P&S `🔲/✅/❌` | **Keep `🔲/✅/❌`** (matches live BUILD_PLAN + this migration prompt) |
| Template `AGENTS.md` vs P&S automation megadoc | **Keep CRITICAL/automation**; prepend Reference router |
| `modules/android/` vs `modules/pns-*` | **Keep pns-***; `TEMPLATE_INDEX.json` maps stack `android` → pns modules |
| Template CI (`ci.yml`, release-please, stale) | **Additive only** — do not replace P&S workflows or `pns_github_release` |
| `COMPLETED_TASKS.md` vs `BUILD_PLAN_COMPLETED.md` | Dual alias retained |

### Stack selection

- **Active:** Android (Camera2 / F-Droid Gradle) — `modules/pns-{core,fleet,capture,preview}`
- **Inactive:** web, python, node, go, rust, lightroom — do not copy `examples/`
- **Repo mode:** Reference (not fresh Bootstrap init)

### Risk areas

- Never gut `AGENTS.md` CRITICAL camera/DNG/fleet locks
- Update `pns_validate_bootstrap.ps1` in the same change set as new required paths
- Scorecard is non-blocking until a human adds it to branch protection
- `.env.example` must not contain device serials; use `scripts/pns_adb_device.env`

---

## Migration notes (for humans)

### Done by agents (this sprint)

- Reference-mode entrypoints and generic Cursor rules
- Template version pin **0.15.0** + update checker config
- `/cleanup` batch command; registry counts → 26
- Additive Dependency Review + OpenSSF Scorecard workflows
- BUILD_PLAN legend + Sprint BOOTSTRAP-0.15 lanes

### Still needs human / device attention

| Item | Owner | Notes |
|------|-------|-------|
| Confirm Scorecard / required checks on `main` | `[HUMAN]` | Scorecard must not block merges until approved |
| Dependabot backlog | `[HUMAN]` | Use `/dependabot` |
| Milestone H subjective / store / signing | `[HUMAN]` | Unchanged product backlog |
| USB capture/chrome gates | `[ADB]` | Not required for bootstrap doc alignment |

### How agents should work (session protocol)

1. Read [`START_HERE.md`](START_HERE.md) (Reference mode)
2. Pick Cursor mode via [`CURSOR_MODES.md`](CURSOR_MODES.md)
3. Read [`FOR_AGENTS.md`](FOR_AGENTS.md) → `AGENTS.md` CRITICAL + automation
4. Execute `BUILD_PLAN.md` **Sequential** first, then Parallel with isolated scopes
5. Local-first: Tier 0 → 1 → 2 → USB sequential → ship

Conventional Commits remain the expected style (`CONTRIBUTING.md`).
