# YOLO mode (off by default)

Presence of root file `yolo.on` arms YOLO **for this repo only**. The template ships **without** `yolo.on`.

## May fix

Colors off-palette, unlabeled buttons, missing alt text, leftover `console.log`, dead imports/deps, off-scale spacing, orphaned files, stub content in a shipped path.

## Leaves alone

Workflows, CODEOWNERS, config, docs, branding, lockfiles; layout/copy rewrites, dependency majors, release cuts, fanout, file deletions, rule edits.

## Every run

1. Pull `rules.manifest.json` / rules SoT first  
2. One family of fixes  
3. Fix twice and compare  
4. Read own diff  
5. Gates  
6. PR with `touches:`  
7. Merge and watch 20 minutes  

Over 20 files or 600 lines → split. Caps: 20 files, 600 lines, 5 merges/day, 30 minutes.

## Give up (sticky)

Delete `yolo.on`, write `YOLO-OFF.md` with one line, stop. Triggers: two undos in a row, three in a day, protected file touched, undo failed twice.

Arming requires a non-stale Argus contract (`check-contract.py`).
