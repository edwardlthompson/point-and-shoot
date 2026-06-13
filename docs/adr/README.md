# Architecture Decision Records (ADR)

Chronological register of major technical trade-offs for Point & Shoot. **Append only** — past ADRs are immutable history; supersede with a new numbered ADR rather than rewriting.

## Template

Each ADR file uses:

```markdown
# ADR-NNNN — Title

- **Status:** Accepted | Superseded by ADR-XXXX
- **Date:** YYYY-MM-DD

## Context
…

## Decision
…

## Consequences
…

## References
…
```

## Index

See root [`DECISION_LOG.md`](../../DECISION_LOG.md) for the full list with one-line summaries.

## When to add an ADR

- Architectural pivot (capture pipeline, fleet policy, distribution)
- Irreversible or high-cost constraint (FOSS boundary, license, reproducible builds)
- Replacing a scattered spike doc as the formal decision record

Do **not** add ADRs for routine bug fixes — use [`docs/AGENT_REGRESSION_MEMORY.md`](../AGENT_REGRESSION_MEMORY.md) instead.
