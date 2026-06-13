## Summary

<!-- What changed and why (1–3 sentences). -->

## Checklist

- [ ] Read [`CONTRIBUTING.md`](CONTRIBUTING.md)
- [ ] Host: `scripts/pns_verify_toolchain.ps1 -RunTests` (or CI green)
- [ ] Security: `security-scan` + `codeql-analysis` workflows green (when applicable)
- [ ] Agent PRs: used [`PROMPT_LIBRARY.md`](PROMPT_LIBRARY.md) workflow if applicable
- [ ] Capture/DNG/fleet/chrome: [`docs/AGENT_REGRESSION_MEMORY.md`](docs/AGENT_REGRESSION_MEMORY.md) consulted; USB evidence attached when required
- [ ] User-visible: [`CHANGELOG.md`](CHANGELOG.md) + [`scripts/changelog_coverage.v1.json`](scripts/changelog_coverage.v1.json) updated
- [ ] If **`app/src/release/generated/baselineProfiles/*.txt`** changed: note **device class + Android API** used for regeneration in the PR body (large diffs stay reviewable).
