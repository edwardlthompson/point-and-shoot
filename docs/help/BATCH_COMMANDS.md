# Agent shortcuts (cheat sheet)

Shortcut commands for Cursor Agent — type **`/`** in Agent chat to pick a recipe.

## 30-second start

1. Open **Agent** chat in Cursor.
2. Type **`/`** to open the command menu.
3. Pick a command (e.g. `/verify`, `/build`, `/ship`).
4. The agent runs the workflow step by step.

Bookmark this page when you return after a break.

## Try these first (super commands)

| Command | When to use |
|---------|-------------|
| `/bootstrap` | Fresh agent session — context + local gates |
| `/verify` | After your changes, before opening a pull request |
| `/build` | Start a new feature (plans first, then implements) |
| `/ship` | Publish **Point & Shoot x.y.z** to GitHub with a **signed** APK |
| `/maintain` | Weekly health pass — security, dependencies, full review |

**Worked example — before a PR:** make changes → `/verify` → fix any red gates → open PR.

## When you need one step

**Getting started:** `/init` · `/setup` · `/prune` · `/gates`

**Building:** `/plan` · `/feature` · `/fix` (gates failed after `/build`) · `/scope` (parallel agents)

**Docs & checks:** `/docs` · `/ci` (CI poll only) · `/gates` (full local validation)

**Publishing:** `/prerelease` · `/push` · `/regress` (after release)

**Maintenance:** `/triage` · `/dependabot` · `/audit`

**Long sessions:** `/compact` · `/restore` · `/cleanup` (archive finished BUILD_PLAN rows)

**Next work:** `/coach` (what to do now) · `/ideas` (ranked backlog, no implementation)

**Device testing:** use `scripts/pns_capture_pipeline_verify.ps1` then `pns_chrome_ux_gate.ps1` **sequentially** on one phone (not part of `/gates` by default).

## Before you publish

`/push` and `/ship` **push code to GitHub**. Only run them when you intend to publish.

## Bare words (optional)

You can type a single word like `verify` instead of `/verify`. Slash commands are more reliable.

---

Advanced registry (maintainers): [docs/BATCH_COMMANDS.md](../BATCH_COMMANDS.md)
