# Start Here

> **Read this file first** — whether you are a human or a Cursor agent.

## What is this?

**Point & Shoot** is an Android Camera2 FOSS camera app. Agent process and Cursor conventions are aligned with [agent-project-bootstrap](https://github.com/edwardlthompson/agent-project-bootstrap) **v0.15.0** in **Reference** mode.

**License:** Apache-2.0 (not the template’s MIT). See `LICENSE` and ADR-0005.

**Alignment record:** [`BOOTSTRAP_ALIGNMENT.md`](BOOTSTRAP_ALIGNMENT.md) · file map: [`BOOTSTRAP_TEMPLATE_MAP.md`](BOOTSTRAP_TEMPLATE_MAP.md)

## Which repo mode are you in?

This repository is **Reference** (existing product). Do **not** run a fresh Bootstrap init or copy inactive template `examples/`.

- **Reference:** read [`CURSOR_MODES.md`](CURSOR_MODES.md), then [`FOR_AGENTS.md`](FOR_AGENTS.md)
- **Bootstrap init:** only if creating a *new* project from the upstream template — not applicable here. See [`INITIALIZATION_PROMPT.md`](INITIALIZATION_PROMPT.md) (already initialized).

## Cursor modes (Plan / Agent / Debug / Ask)

See [`CURSOR_MODES.md`](CURSOR_MODES.md) — pick the Cursor mode before editing code.

## Agent shortcuts

Type **`/`** in Cursor Agent chat. Start with **[docs/help/BATCH_COMMANDS.md](help/BATCH_COMMANDS.md)** — try `/verify` before merge or `/gates` for local Tier 0.

## Reference read order

1. `docs/START_HERE.md` (this file)
2. `docs/CURSOR_MODES.md`
3. `docs/FOR_AGENTS.md`
4. `TEMPLATE_INDEX.json`
5. `AGENTS.md` (automation + CRITICAL locks)
6. Active modules only: `modules/pns-*/MODULE.md`
7. `BUILD_PLAN.md` Sequential lane
8. When debugging capture/DNG/fleet: `docs/AGENT_REGRESSION_MEMORY.md` + relevant `.cursor/rules/*-lock.mdc`

## Do not read yet

- Inactive template stacks (web/python/node/go/rust/lightroom examples — not in this repo)
- Full `KNOWLEDGE_BASE.md` unless debugging a mapped area
- Upstream `docs/MAINTAINING_THE_TEMPLATE.md` (template maintainers only)

## BUILD_PLAN labels

`AGENT` | `HUMAN` | `ADB` | `AUTO` — filter with `grep '\[AGENT\]' BUILD_PLAN.md`

**Status markers:** 🔲 open · ✅ done · ❌ blocked — emoji only (not `- [ ]` checkboxes). See legend in `BUILD_PLAN.md`.

## Security

Dependabot + private vulnerability reporting: `SECURITY.md`. Weekly CVE triage: [`SECURITY_TRIAGE.md`](SECURITY_TRIAGE.md).

## Device truth

Camera/DNG/chrome fixes are **not done** until USB-verified on the primary fleet device (see `AGENTS.md`). Always `force-stop` the app after ADB sessions.

## Agent prompt (Reference)

Read @docs/START_HERE.md, @docs/CURSOR_MODES.md, @docs/FOR_AGENTS.md, and @TEMPLATE_INDEX.json. Pick Cursor mode per CURSOR_MODES. Apply matching rules. Preserve CRITICAL locks. Do not re-scaffold or copy inactive examples.
