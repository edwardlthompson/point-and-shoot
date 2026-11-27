#!/usr/bin/env bash
# Fail when BUILD_PLAN remaining tallies are stale.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
exec bash "$ROOT/scripts/refresh-build-plan-tally.sh" --check
