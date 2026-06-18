# Full repo review and BUILD_PLAN execution

Framework: [AGENT]/[HUMAN] labels; gates after AGENT steps; update memory files at milestones.

## Step 1 — Review

Explore via targeted reads (`KNOWLEDGE_BASE.md` index). Run when available:

```powershell
.\scripts\pns_validate_bootstrap.ps1
.\scripts\pns_local_dev_parallel.ps1
.\scripts\pns_verify_toolchain.ps1 -RunTests
```

Check Dependabot/CodeQL via `gh` if authenticated. Write `CODE_REVIEW.md` from `CODE_REVIEW.md.example`.

## Step 2 — BUILD_PLAN

Add a review sprint at the top of `BUILD_PLAN.md`. Link findings to CODE_REVIEW sections.

## Step 3 — Execute

Work Sequential [AGENT] items top-to-bottom. After each step run Tier 0 + Tier 2 gates.

## Step 4 — Cleanup

Archive completed sprint to `BUILD_PLAN_COMPLETED.md`; update `AGENT_MEMORY.md` at milestone boundary.

Begin now.
