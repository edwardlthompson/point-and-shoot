#!/usr/bin/env bash
# Refresh or check remaining-item tallies on BUILD_PLAN.md and BUILD_PLAN_TEMPLATE.md.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
# shellcheck source=lib/resolve-python.sh
. "$(cd "$(dirname "$0")" && pwd)/lib/resolve-python.sh"
exec "$PY" "$ROOT/scripts/lib/build_plan_tally.py" --root "$ROOT" "$@"
