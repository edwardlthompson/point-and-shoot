# Ideas (backlog, do not implement)

Propose the next in-scope features. This is not `/coach` (next action now) and not `/plan` (implement a chosen feature).

Read @AGENT_MEMORY.md (Persistent Context + latest retrospective only), @BUILD_PLAN.md Sequential lane, @CHANGELOG.md `## Unreleased`, the latest entries in @DECISION_LOG.md, @docs/START_HERE.md, and @docs/help/BATCH_COMMANDS.md.

1. Run `.\scripts\pns_validate_bootstrap.ps1` (or `.\scripts\pns_changelog_gate.ps1` if bootstrap is already green). Summarize the next BUILD_PLAN 🔲 `[AGENT]` row in one line.
2. This repo is always **Child / Reference** mode (camera product, not the bootstrap template). Suggest product slices, not template internals. Use `BUILD_PLAN.md` 🔲/✅ — there is no `scripts/agent-run.py`.
3. Print **5–8** ideas that are not already shipped and not already 🔲 on BUILD_PLAN. Each idea: title, **Why** (one sentence + industry reason), **Effort** (S or M), **Priority** (P0 / P1 / P2).
4. Cap at 8. No option dump. Name the **single best next** idea in one sentence.
5. **Do not implement.** **Do not edit BUILD_PLAN** unless the user names a number (or says **do all**). Then offer: “Say the number to add a 🔲 `[AGENT]` row.”
6. Refuse out of scope: proprietary SDKs on the FOSS path, Play-only defaults, bulk restore of `docs/REVERTED_FEATURES_RESTORE_LIST.md` §4a / §2 without USB proof.

Other IDEs: the same recipe is `docs/help/IDEAS.md`.

Begin now.
