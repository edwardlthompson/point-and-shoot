# Multi-agent parallel orchestration (Point & Shoot)

**When:** Complex milestones touching many files — use Cursor **parallel agents** (Git worktrees and/or Cloud VMs in the Agents window), up to **8** concurrent workers.

**Companion:** [Local-first dev loop](LOCAL_FIRST_DEV_LOOP.md) · [`PROMPT_LIBRARY.md`](../PROMPT_LIBRARY.md) §9 · [`scripts/pns_agent_worktree_bootstrap.ps1`](../scripts/pns_agent_worktree_bootstrap.ps1)

---

## Guardrails (mandatory)

### 1. Strict feature branching

Every parallel agent runs in an **isolated short-lived branch** on a **dedicated git worktree** — no two agents write the same working tree.

| Convention | Example |
|------------|---------|
| Branch | `feature/agent-<task-slug>` |
| Worktree path | `../point-and-shoot-wt-<task-slug>` (sibling of main clone) |
| Lifetime | Delete branch + worktree after merge or abandon |

Bootstrap:

```powershell
.\scripts\pns_agent_worktree_bootstrap.ps1 -TaskSlug fleet-matrix-docs -Create
# Agent works in printed path; opens that folder in Cursor / Cloud VM
.\scripts\pns_agent_worktree_bootstrap.ps1 -TaskSlug fleet-matrix-docs -Remove   # after merge
```

**Never** run two agents on `main` in the same directory.

### 2. Asymmetric scoping (no overlapping file boundaries)

Before launching parallel agents, split work so **file sets do not intersect**. If two tasks need the same file, **serialize** them.

#### Example decoupling (Milestone-sized)

| Agent | Scope | Typical paths | Gate |
|-------|--------|---------------|------|
| **A — Core / types** | Fleet schema, catalog evaluators, pure JVM | `fleet/*.kt`, `app/src/test/.../fleet/*` | `:app:testDebugUnitTest` subset |
| **B — Scripts / host** | PowerShell gates only | `scripts/pns_*.ps1`, no `app/` | `pns_verify_toolchain.ps1 -SkipGradle` |
| **C — Docs / metadata** | Markdown, F-Droid, ADR | `docs/**`, `metadata/**`, `KNOWLEDGE_BASE.md` | `pns_template_doc_link_check.ps1` |
| **D — Preview behavior** | Locked chrome **behavior only** | `PreviewEngineScreen.kt` (minimal), `RawCaptureSupport.kt` | USB capture **alone** |
| **E — DNG pipeline** | Save path only | `Dng12Saver.kt`, `StillCaptureMetadata.kt`, `DngMetadataResolver.kt` | `pns_fixture_dng_gates.ps1` + USB DNG |
| **F — Settings / About** | Non-preview settings sheets | `HudSettingsScreen.kt`, `AboutScreen.kt` | Host a11y tests |

#### Hard overlaps — always serialize

| Area | Why |
|------|-----|
| `PreviewEngineScreen.kt` + `RawCaptureSupport.kt` | Same session graph |
| `gradle/libs.versions.toml` + `LICENSES.md` + `app/build.gradle.kts` | Version + license chain |
| `files/fleet_device_matrix.json` + `FleetDeviceMatrix.kt` | Schema + runtime |
| `CHANGELOG.md` + `changelog_coverage.v1.json` + release constants | Single ship artifact |
| `.cursor/rules/*.mdc` + code they lock | Policy drift |

Use [`.cursor/rules/preview-chrome-ui-lock.mdc`](../.cursor/rules/preview-chrome-ui-lock.mdc) — parallel agents must **not** “improve” chrome layout concurrently.

### 3. Shared schema lock (sequential first)

If any **shared schema / contract** changes, **one agent** lands it **first** (merge to integration branch). Parallel agents **consume** the new types — they do not edit the same contract concurrently.

#### Sequential-only files (schema lock list)

| Lock | Files | Owner action |
|------|-------|--------------|
| **Gradle catalog + deps** | `gradle/libs.versions.toml`, `app/gradle.lockfile`, `LICENSES.md`, `scripts/pns_license_inventory.ps1` | One agent; refresh lockfile + license inventory same commit |
| **App version ship** | `app/build.gradle.kts` (`versionCode`/`versionName`), `PnsExternalUrl.kt`, `scripts/changelog_coverage.v1.json`, `CHANGELOG.md` | Release agent only (`github-release` skill) |
| **Fleet matrix schema** | `fleet/FleetDeviceMatrix.kt`, `files/fleet_device_matrix.json`, `FleetDeviceMatrixBuilder.kt`, `docs/FLEET_DEVICE_CAPABILITY_MATRIX.md` | One agent; run `pns_fleet_matrix_scan.ps1` after |
| **Capability catalog** | `fleet/CameraCapabilityCatalog.kt`, `files/fleet_device_matrix.json` (`capabilityCatalog`), `scripts/pns_capability_catalog_gate.ps1` | One agent before parity/UI agents |
| **DNG / RAW locks** | `DngMetadataResolver.kt`, `RawCaptureSupport.kt` session pickers, `.cursor/rules/dng-*-lock.mdc` | One agent; USB proof before parallel UI work |
| **Dodge tele routing** | `BackCameraRoleResolver.kt`, `SensorCropGeometry.kt`, `.cursor/rules/dodge-tele-focal-routing.mdc` | One agent; `pns_chrome_ux_gate.ps1 -FocalMmSlot 150` |
| **Prefs / backup schema** | `app/src/main/res/xml/pns_backup_rules.xml`, new `SharedPreferences` file names | One agent; document in `PRIVACY.md` if user-visible |
| **ADR / architectural decision** | New `docs/adr/NNNN-*.md` | One agent per ADR number |

**Workflow:**

```text
Phase 0 (sequential): Schema-lock agent merges contract changes
Phase 1 (parallel):   Feature agents rebase on integration branch, scoped paths only
Phase 2 (sequential): Integrator runs pns_local_dev_parallel.ps1 → pns_verify_toolchain -RunTests → USB gates
```

---

## Cursor / Cloud VM usage

| Mode | Use |
|------|-----|
| **Git worktree per agent** | Default for 2–8 local parallel agents — lowest collision risk |
| **Cloud VM agent** | Heavy Gradle `-RunTests` or long USB sessions off laptop; still use **feature branch** per VM |
| **Parent integrator** | One session owns merge order, runs Tier 0–2 gates, resolves conflicts |

**Max 8 agents:** Only allocate slots after Phase 0 schema lock is merged. Prefer **4–6** with clear path boundaries over 8 overlapping tasks.

---

## Merge integration checklist

1. Merge **schema-lock** PR first; refresh `AGENT_MEMORY.md`.
2. Rebase each `feature/agent-*` branch; resolve only within that agent’s declared paths.
3. `.\scripts\pns_local_dev_parallel.ps1` on integration branch.
4. `.\scripts\pns_verify_toolchain.ps1 -RunTests`.
5. USB gates **sequentially** on **CPH2583** (capture, then chrome if both touched).
6. Append `REG-*` to `docs/AGENT_REGRESSION_MEMORY.md` for USB-proven fixes.
7. Remove worktrees: `pns_agent_worktree_bootstrap.ps1 -TaskSlug … -Remove`.

---

## Anti-patterns

| Do not | Do |
|--------|-----|
| Two agents edit `PreviewEngineScreen.kt` | Split: session wiring vs readout chip vs chrome tap map — **or** serialize |
| Parallel USB capture + chrome on one serial | Sequential; see `AGENTS.md` CRITICAL |
| Parallel Gradle `-RunTests` in same worktree | One Gradle lane per worktree; Tier 0 parallel is host-only |
| Schema change mid-flight in parallel agents | Phase 0 sequential lock, then parallel consume |

---

## Implementation status

| Item | Status |
|------|--------|
| Guardrails doc (this file) | **Shipped** |
| `pns_agent_worktree_bootstrap.ps1` | **Shipped** |
| PROMPT_LIBRARY §9 | **Shipped** |
| Cursor rule `.cursor/rules/multi-agent-parallel.mdc` | **Shipped** |
| Automated file-boundary enforcement in CI | **Not planned** — human/agent checklist |
