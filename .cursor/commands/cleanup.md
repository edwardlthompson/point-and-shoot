# BUILD_PLAN archive cleanup

Run after BUILD_PLAN execution when local gates pass. Moves finished work off the active board into @BUILD_PLAN_COMPLETED.md (and keep @COMPLETED_TASKS.md stub in sync when used).

**Do not archive** while any `[AGENT]` or `[AUTO]` row in the active sprint/feature block is still 🔲 or ❌. Items in `HUMAN_BACKLOG.md` stay 🔲 on the board until a human clears them.

## Step 1 — Confirm completion

- All executed `[AGENT]` and `[AUTO]` rows in the active block are ✅
- Gates passed for this session (`pns_validate_bootstrap.ps1`, Tier 0/2, or USB gates as applicable)
- Replace 🔲 → ✅ only for rows verified done **this session**; never mark complete while gates are red
- Device-facing rows need USB proof per `AGENTS.md`

## Step 2 — Archive to BUILD_PLAN_COMPLETED.md

Prepend a new dated section (or append under the matching milestone) in @BUILD_PLAN_COMPLETED.md:

```markdown
## {Sprint or feature name} ({YYYY-MM-DD})

- ✅ [OWNER] Original description
```

Copy every ✅ row from the finished block verbatim (keep owner labels and descriptions).

## Step 3 — Slim BUILD_PLAN.md

Remove the archived ✅ rows from the active board.

**Finished sprint:**

- Delete or collapse the sprint section on the active board
- Add a summary line pointing at `BUILD_PLAN_COMPLETED.md`
- Leave open `[HUMAN]` / `[ADB]` Milestone H items in place

## Step 4 — Verify

```powershell
.\scripts\pns_validate_bootstrap.ps1
.\scripts\pns_check_batch_commands.ps1
```

Begin now.
