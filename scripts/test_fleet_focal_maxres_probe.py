#!/usr/bin/env python3
"""Unit tests for fleet_focal_maxres_probe.py"""

from __future__ import annotations

import json
import tempfile
import unittest
from pathlib import Path

import fleet_focal_maxres_probe as probe


class FleetFocalMaxresProbeTest(unittest.TestCase):
    def test_parse_dumpsys_mp_by_camera(self) -> None:
        text = """
Camera 2 (BACK) ...
pixelArraySizeMaximumResolution = 8192 x 6144
activeArraySizeMaximumResolution = [0, 0, 8192, 6144]
availableStreamConfigurationsMaximumResolution ...
"""
        by_cam = probe._parse_dumpsys_mp_by_camera(text)
        self.assertIn("2", by_cam)
        self.assertGreater(by_cam["2"], 48.0)

    def test_manifest_fallback_when_dumpsys_ambiguous(self) -> None:
        dumpsys = "availableStreamConfigurationsMaximumResolution\nCamera 0 BACK"
        focal = {"slots": [{"cameraId": "0", "focalMm35": 23, "megapixels": 12.5}]}
        manifest = {
            "devices": {
                "CPH2583": {
                    "bands": [{"focal35Min": 19, "focal35Max": 34, "megapixels": 50.0}],
                },
            },
        }
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            dumpsys_path = root / "d.txt"
            focal_path = root / "f.json"
            manifest_path = root / "m.json"
            out_path = root / "o.json"
            dumpsys_path.write_text(dumpsys, encoding="utf-8")
            focal_path.write_text(json.dumps(focal), encoding="utf-8")
            manifest_path.write_text(json.dumps(manifest), encoding="utf-8")
            import sys

            old = sys.argv
            try:
                sys.argv = [
                    "fleet_focal_maxres_probe.py",
                    "--dumpsys",
                    str(dumpsys_path),
                    "--focal-map",
                    str(focal_path),
                    "--manifest",
                    str(manifest_path),
                    "--model",
                    "CPH2583",
                    "--out",
                    str(out_path),
                ]
                self.assertEqual(probe.main(), 0)
            finally:
                sys.argv = old
            result = json.loads(out_path.read_text(encoding="utf-8"))
            self.assertTrue(result["maxResolutionBlocksPresent"])
            self.assertEqual(len(result["overrides"]), 1)
            self.assertEqual(result["overrides"][0]["megapixels"], 50.0)


if __name__ == "__main__":
    unittest.main()
