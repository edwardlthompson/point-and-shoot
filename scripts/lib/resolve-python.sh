# Resolve a working Python into PY (single executable path).
# Skip the Windows Store python3 stub (WindowsApps\python3.exe hangs).
# Prefer `py -3` on Windows, then python3/python outside WindowsApps.
# shellcheck shell=bash
# shellcheck source=resolve-tools.sh
. "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/resolve-tools.sh"
PY=""
if command -v py >/dev/null 2>&1; then
  _py_exe="$(py -3 -c "import sys; print(sys.executable)" 2>/dev/null || true)"
  case "$_py_exe" in
    *WindowsApps*) _py_exe="" ;;
  esac
  if [ -n "$_py_exe" ]; then
    PY="$_py_exe"
  fi
fi
if [ -z "$PY" ]; then
  for _cand in python3 python; do
    if command -v "$_cand" >/dev/null 2>&1; then
      _p="$(command -v "$_cand")"
      case "$_p" in
        *WindowsApps*) continue ;;
      esac
      PY="$_p"
      break
    fi
  done
fi
PY="${PY:-python3}"
unset _cand _p _py_exe
