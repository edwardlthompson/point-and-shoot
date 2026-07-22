# For Agents

Point & Shoot — Reference-mode agent protocol (bootstrap v0.15.0 alignment).

## Phased loading

SessionStart → [`START_HERE.md`](START_HERE.md) → [`CURSOR_MODES.md`](CURSOR_MODES.md) (pick mode) → this file → [`AGENTS.md`](../AGENTS.md) → `BUILD_PLAN.md` Sequential → active `modules/pns-*/MODULE.md` only.

Batch slash commands: [`BATCH_COMMANDS.md`](BATCH_COMMANDS.md) (humans: [`help/BATCH_COMMANDS.md`](help/BATCH_COMMANDS.md)).

## Cursor mode transitions

See [`CURSOR_MODES.md`](CURSOR_MODES.md). Key triggers:

- **Agent → Plan** — shared schema/session change, scope expanded, CRITICAL lock area
- **Agent → Debug** — gate fail after autofix, CI red, or flaky USB repro
- **Debug → Agent** — root cause confirmed and fix approach agreed
- **Plan → Agent** — plan approved ("execute the plan")

Do not debug in Plan Mode. Do not edit in Ask Mode.

## Token economy

1. Never load inactive template stacks or wholesale `examples/`
2. Grep [`KNOWLEDGE_BASE.md`](../KNOWLEDGE_BASE.md) then open linked SoT — do not paste generic framework docs into KB
3. Update memory files only at session start, milestone end, or architectural pivot
4. Read-before-write: inspect `@filename` before edits
5. Sequential before Parallel in BUILD_PLAN
6. Respect `.cursorignore` — do not index `.gradle/`, `build/`, `hfr-runs/` bulk, etc.
7. Before capture/DNG/GLES/fleet edits: read [`AGENT_REGRESSION_MEMORY.md`](AGENT_REGRESSION_MEMORY.md) + relevant lock rules

## BUILD_PLAN status markers

| Marker | State |
|--------|-------|
| 🔲 | Open |
| ✅ | Done — archive sprint to `BUILD_PLAN_COMPLETED.md` / `COMPLETED_TASKS.md` |
| ❌ | Blocked — append reason |

**Format:** `🔲 [OWNER] Description` · do not use `- [ ]` GitHub checkboxes.

Owners: `[AGENT]` · `[HUMAN]` · `[ADB]` · `[AUTO]`.

## Repo hygiene

- Track source and lockfiles only; never commit build output or caches
- Before push: `.\scripts\pns_check_repo_hygiene.ps1`
- Stage explicit paths; avoid blind `git add -A`
- Device serials stay in gitignored `scripts/pns_adb_device.env`

## Parallel-first planning and dispatch

See [`MULTI_AGENT_PARALLEL_ORCHESTRATION.md`](MULTI_AGENT_PARALLEL_ORCHESTRATION.md) and `.cursor/rules/multi-agent-parallel.mdc`.

1. Finish all `[AGENT]` **Sequential** items (shared schema locked)
2. Dispatch Parallel agents with **non-overlapping** paths via worktrees (`pns_agent_worktree_bootstrap.ps1`)
3. Parallel agents **never** edit `BUILD_PLAN.md`, `AGENTS.md` CRITICAL sections, or shared contracts
4. USB: never run capture pipeline and chrome UX gates in parallel on one serial

## Autonomous `/build`

`/build` runs `[AGENT]`/`[AUTO]` Sequential then Parallel, then surfaces **Human & device** items. Failed human automation goes to `HUMAN_BACKLOG.md` while BUILD_PLAN rows stay open.

## 3-strike rule

After 3 failed fix attempts: halt, summarize conflict, request human direction. Switch to **Debug Mode** when root cause is unclear.

Escalate with: failing command output (last attempt), files touched, proposed options.

## Session checkpoint

1. Copy `.cursor-session-state.example.json` (or `.cursor-session-state.example`) to `.cursor-session-state` / `.cursor-session-state.json` (gitignored)
2. Fill milestone, device, last gates, blockers, next steps
3. Clear chat; on restart read state, pick Cursor mode, resume BUILD_PLAN
4. Delete session state after successful restore

## Autonomous feature gates

After each `[AGENT]` step that changes code:

```powershell
.\scripts\pns_watch_agent_gates.ps1 -Step tier0
# Before merge:
.\scripts\pns_verify_toolchain.ps1 -RunTests
```

Capture/session changes: USB `pns_capture_pipeline_verify` **then** chrome gate (sequential). Always `force-stop` the app after ADB.

## Failure playbook

Use **Debug Mode** when CI or local gates fail and root cause is unclear.

### CI poll after push

```powershell
.\scripts\pns_check_github_ci.ps1 -WaitSeconds 300
```

Required green workflows (P&S names): **Toolchain verify**, **Security scan**, **CodeQL**. Additive: **Dependency Review** on PRs; **OpenSSF Scorecard** weekly (non-blocking until human enables as required).

### Device / USB

- Empty `adb devices` → ask human to connect; do not claim delivery
- `ERROR_CAMERA_DEVICE` during overlapping gates → serialize and retry
- Never leave camera app running after tests

### Template updates

```powershell
.\scripts\pns_check_template_updates.ps1 -Verbose
```

See [`UPGRADING_FROM_TEMPLATE.md`](UPGRADING_FROM_TEMPLATE.md).
