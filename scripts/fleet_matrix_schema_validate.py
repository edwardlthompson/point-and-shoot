#!/usr/bin/env python3
"""Validate fleet_device_matrix.json structure (Milestone 16.12). Exit 0 on PASS."""

from __future__ import annotations

import json
import sys
from pathlib import Path
from typing import Any


REQUIRED_ROOT = (
    "schemaVersion",
    "scanMeta",
    "device",
    "cameras",
    "product",
    "appendix",
)
REQUIRED_SCAN_META = (
    "scanTier",
    "appVersionCode",
    "sdkInt",
    "fingerprintSha256Prefix",
)
REQUIRED_DEVICE = ("manufacturer", "model", "device")
VALID_SCAN_TIERS = frozenset({"quick", "full"})


def _fail(msg: str) -> None:
    print(f"FLEET MATRIX SCHEMA: FAIL — {msg}", file=sys.stderr)
    sys.exit(1)


def _pass(msg: str = "") -> None:
    suffix = f" ({msg})" if msg else ""
    print(f"FLEET MATRIX SCHEMA: PASS{suffix}")
    sys.exit(0)


def validate_root(root: dict[str, Any]) -> None:
    if root.get("schemaVersion") != 1:
        _fail(f"schemaVersion must be 1, got {root.get('schemaVersion')!r}")
    for key in REQUIRED_ROOT:
        if key not in root:
            _fail(f"missing root key {key!r}")
    meta = root.get("scanMeta")
    if not isinstance(meta, dict):
        _fail("scanMeta must be object")
    for key in REQUIRED_SCAN_META:
        if key not in meta:
            _fail(f"scanMeta missing {key!r}")
    tier = meta.get("scanTier")
    if tier not in VALID_SCAN_TIERS:
        _fail(f"scanMeta.scanTier invalid: {tier!r}")
    device = root.get("device")
    if not isinstance(device, dict):
        _fail("device must be object")
    for key in REQUIRED_DEVICE:
        if key not in device:
            _fail(f"device missing {key!r}")
    cameras = root.get("cameras")
    if not isinstance(cameras, list):
        _fail("cameras must be array")
    if len(cameras) == 0:
        _fail("cameras must be non-empty")
    ids: list[str] = []
    for i, cam in enumerate(cameras):
        if not isinstance(cam, dict):
            _fail(f"cameras[{i}] must be object")
        cid = cam.get("cameraId")
        if not cid:
            _fail(f"cameras[{i}] missing cameraId")
        ids.append(str(cid))
    if tier == "full" and ids != sorted(ids):
        _fail(f"full tier cameras must be sorted by cameraId (got {ids}, want {sorted(ids)})")
    for i, cam in enumerate(cameras):
        if "hfrMaxFpsAt1080" not in cam and "featureGates" not in cam:
            _fail(f"cameras[{i}] missing shallow or structured fields")


def main() -> None:
    if len(sys.argv) < 2:
        _fail("usage: fleet_matrix_schema_validate.py <fleet_device_matrix.json>")
    path = Path(sys.argv[1])
    if not path.is_file():
        _fail(f"not a file: {path}")
    try:
        root = json.loads(path.read_text(encoding="utf-8"))
    except json.JSONDecodeError as e:
        _fail(f"JSON parse: {e}")
    if not isinstance(root, dict):
        _fail("root must be object")
    validate_root(root)
    n = len(root["cameras"])
    tier = root["scanMeta"]["scanTier"]
    _pass(f"tier={tier} cameras={n}")


if __name__ == "__main__":
    main()
