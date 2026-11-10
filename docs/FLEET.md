# Fleet fanout

`fleet.json` is the **opt-in** roster. Repos not listed are never touched.

## Briefs

Markdown under `briefs/` with YAML frontmatter. `verify:` is an **executable command** (not prose). No `verify:` → brief stays in plan pass.

```bash
python3 scripts/argus-fanout-plan.py briefs/_TEMPLATE.md
```

## Waves

1. Canary of 1  
2. Then 3  
3. Then batches of 5 (halved when `auto: true`)  

Halt on first failure in a wave; immediately on a canary revert. Skips are not failures. Caps: 25 repos/brief, 3 waves/hour, 60 minutes/repo, 2 reverts halt the brief.

One branch, one PR, per repo — never one cross-repo commit.
