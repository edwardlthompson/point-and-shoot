"""Capture `dumpsys gfxinfo … framestats` after a cold-start preview window.

Reads **PNS_ADB_SERIAL** from **scripts/pns_adb_device.env** when **--serial** is
omitted (same key as PowerShell fleet scripts). Prefer **--serial** when more than
one device is connected or to override the env file.

Example:
  python scripts/pns_capture_gfxinfo_baseline.py --serial <serial>
"""
from __future__ import annotations

import argparse
import os
import subprocess
import sys
import time
from pathlib import Path

DEFAULT_PKG = "dev.pointandshoot"
DEFAULT_SETTLE_S = 12


def _read_pns_adb_serial(env_path: Path) -> str | None:
    if not env_path.is_file():
        return os.environ.get("PNS_ADB_SERIAL")
    for raw in env_path.read_text(encoding="utf-8", errors="replace").splitlines():
        line = raw.strip()
        if not line or line.startswith("#"):
            continue
        if line.startswith("export "):
            line = line[7:].strip()
        if "=" not in line:
            continue
        key, _, val = line.partition("=")
        if key.strip() != "PNS_ADB_SERIAL":
            continue
        v = val.strip().strip('"').strip("'")
        return v or None
    return os.environ.get("PNS_ADB_SERIAL")


def adb(serial: str, *args: str) -> str:
    r = subprocess.run(
        ["adb", "-s", serial, *args],
        capture_output=True,
        text=True,
        encoding="utf-8",
        errors="replace",
    )
    if r.returncode != 0:
        raise RuntimeError(
            f"adb failed rc={r.returncode}: adb -s {serial} {' '.join(args)}\n{r.stderr}"
        )
    return r.stdout


def main() -> int:
    script_dir = Path(__file__).resolve().parent
    proj = script_dir.parent
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--serial",
        default=None,
        help="adb serial (default: PNS_ADB_SERIAL from scripts/pns_adb_device.env or env)",
    )
    parser.add_argument(
        "--out-dir",
        type=Path,
        default=proj / "perf-runs",
        help="output directory (default: repo perf-runs/)",
    )
    parser.add_argument(
        "--settle-seconds",
        type=float,
        default=DEFAULT_SETTLE_S,
        help=f"sleep after cold start before gfxinfo (default: {DEFAULT_SETTLE_S})",
    )
    parser.add_argument("--pkg", default=DEFAULT_PKG, help="applicationId / package")
    args = parser.parse_args()
    serial = args.serial or _read_pns_adb_serial(script_dir / "pns_adb_device.env")
    if not serial:
        print(
            "error: no adb serial; pass --serial or set PNS_ADB_SERIAL in scripts/pns_adb_device.env",
            file=sys.stderr,
        )
        return 2

    out_dir: Path = args.out_dir
    out_dir.mkdir(parents=True, exist_ok=True)
    stamp = time.strftime("%Y%m%d_%H%M%S")
    out = out_dir / f"gfxinfo_{stamp}_serial-{serial}.txt"

    adb(serial, "shell", "am", "force-stop", args.pkg)
    adb(serial, "shell", "dumpsys", "gfxinfo", args.pkg, "reset")
    adb(
        serial,
        "shell",
        "am",
        "start",
        "-W",
        "-n",
        f"{args.pkg}/.MainActivity",
        "--es",
        "pns_screen",
        "preview",
    )
    time.sleep(max(0.0, float(args.settle_seconds)))
    body = adb(serial, "shell", "dumpsys", "gfxinfo", args.pkg, "framestats")
    header = (
        "# Point & Shoot gfxinfo capture\n"
        f"# serial={serial}\n"
        "# after: am force-stop; gfxinfo reset; cold start preview "
        f"{args.settle_seconds}s settle\n"
        f"# path={out.as_posix()}\n\n"
    )
    out.write_text(header + body, encoding="utf-8")
    print(str(out))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
