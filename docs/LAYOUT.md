# Layout law

One location per asset class. Do not invent parallel trees.

| Class | Location |
|-------|----------|
| App / product source | `examples/{stack}/` |
| Stack module guide | `modules/{stack}/MODULE.md` |
| Design tokens (SoT) | `design-tokens/design-tokens.json` |
| Brand kit | `branding/` (`brand.json`, `assets/`, `product.json`) |
| Agent law | `AGENTS.md` (adapters generated) |
| Living memory | `.agent/AGENT_MEMORY.md`, `.agent/DECISION_LOG.md` |
| Feature specs | `docs/features/` |
| Argus rules (SoT) | `argus/rules/` |
| UX rule YAML | `argus/ux/rules/` |
| Briefs (fleet) | `briefs/` |
| Gates | `scripts/check-*.py` (+ `.sh` / `.ps1` twins) |

See also [`DIRECTORY_RULES.md`](DIRECTORY_RULES.md) for asset-class detail and [`WEB_PROJECT_LAYOUT.md`](WEB_PROJECT_LAYOUT.md) for web publish paths.
