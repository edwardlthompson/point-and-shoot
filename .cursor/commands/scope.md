# Parallel agent scope map

Read `docs/MULTI_AGENT_PARALLEL_ORCHESTRATION.md` and `BUILD_PLAN.md` parallel notes.
Assign one worktree per agent — no overlapping file paths:

```powershell
.\scripts\pns_agent_worktree_bootstrap.ps1 -TaskSlug <kebab-name> -Create
```

Shared schema / version / changelog merges stay **sequential** before parallel feature agents.

Begin now.
