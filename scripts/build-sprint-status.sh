#!/usr/bin/env bash
# Report active BUILD_PLAN sprint and next autonomous /build row.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
LANE="auto"
JSON=false
while [ $# -gt 0 ]; do
  case "$1" in
    --json) JSON=true ;;
    --lane) LANE="${2:-auto}"; shift 2; continue ;;
    --lane=*) LANE="${1#*=}" ;;
    -h|--help)
      echo "Usage: build-sprint-status.sh [--json] [--lane auto|child|maintainer]"
      exit 0
      ;;
  esac
  shift
done
ARGS=(--root "$ROOT" --lane "$LANE")
[ "$JSON" = true ] && ARGS+=(--json)
python3 "$ROOT/scripts/lib/build_sprint.py" "${ARGS[@]}"
