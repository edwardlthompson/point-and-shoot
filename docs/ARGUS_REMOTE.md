# Argus remote console (disabled by default)

[`argus.remote.json`](../argus.remote.json) ships with `enabled: false` and `readonly: true`.

## Identity

`allow` is an exact-match list of user IDs. Commands from anyone else are **ignored** (no error reply — no probing). Bot identity is separate from `RELEASE_TOKEN`.

## Cards

Argus sends a card: one line of intent, diff stat, verify result, single-use nonce. Reply with the verb + nonce, or just the nonce for the suggested action. Cards expire at `card_ttl_s`. Destructive verbs (`release`, `fleet execute`, `revert`) need the verb twice.

Every accepted command appends to `state/remote.log` and is committed.

## Quiet

Only the six `notify` keys ping. `quiet_hours` suppresses all but `main_red`.

## Runner

The bot must run in CI or a cloud runner — never only on a laptop. State lives in the repo.

```bash
python3 scripts/argus-remote.py status
```
