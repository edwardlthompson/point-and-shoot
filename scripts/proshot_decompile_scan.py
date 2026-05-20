"""Scan jadx decompile tree for Camera2 / RAW / DNG needles; emit JSON + markdown."""
from __future__ import annotations

import json
import re
import sys
from pathlib import Path

NEEDLES_BASE = [
    "DngCreator",
    "ImageFormat.RAW",
    "RAW_SENSOR",
    "RAW10",
    "RAW12",
    "ImageReader",
    "CameraManager",
    "CameraDevice",
    "CameraCaptureSession",
    "CaptureRequest",
    "CaptureResult",
    "TotalCaptureResult",
    "createCaptureSession",
    "openCamera",
    "setPhysicalCameraId",
    "LOGICAL_MULTI_CAMERA",
    "LENS_SHADING",
    "STATISTICS_LENS_SHADING",
    "physicalCamera",
    "getPhysicalCameraIds",
    "OutputConfiguration",
    "SessionConfiguration",
    "CameraCharacteristics",
    "SCALER_CROP_REGION",
    "CONTROL_AE_MODE",
    "CONTROL_SCENE_MODE",
]

# MotionCam / RAW video / DCG (Milestone 13.1)
NEEDLES_MOTIONCAM = [
    "EnableHDRDCGMode",
    "DynamicRangeProfiles",
    "setSessionParameters",
    "SessionConfiguration",
    "MediaRecorder",
    "MediaCodec",
    "mcraw",
    "MCRAW",
    "MotionCam",
    "createConstrainedHighSpeedCaptureSession",
    "YUV_420_888",
    "YUVP010",
    "HEVC",
    "HDR10",
    "codeaurora",
    "qcamera3",
    "sessionParameters",
    "onImageAvailable",
    "acquireNextImage",
    "writeImage",
]

PROFILES = {
    "proshot": NEEDLES_BASE,
    "motioncam": NEEDLES_BASE + [n for n in NEEDLES_MOTIONCAM if n not in NEEDLES_BASE],
    "all": list(dict.fromkeys(NEEDLES_BASE + NEEDLES_MOTIONCAM)),
}

CLASS_HINT = re.compile(
    r"(?i)(camera|raw|dng|capture|sensor|lens|hal|photo|still|proshot|motioncam|mcraw)"
)


def scan_tree(root: Path, needles: list[str]) -> dict:
    hits: dict[str, list[dict]] = {n: [] for n in needles}
    interesting_files: list[str] = []
    for path in root.rglob("*"):
        if not path.is_file():
            continue
        if path.suffix not in {".java", ".kt", ".smali"}:
            continue
        rel = str(path.relative_to(root)).replace("\\", "/")
        if CLASS_HINT.search(path.stem) or CLASS_HINT.search(rel):
            interesting_files.append(rel)
        try:
            text = path.read_text(encoding="utf-8", errors="ignore")
        except OSError:
            continue
        for needle in needles:
            if needle in text:
                count = text.count(needle)
                hits[needle].append({"file": rel, "count": count})
    for n in needles:
        hits[n].sort(key=lambda x: (-x["count"], x["file"]))
    interesting_files = sorted(set(interesting_files))
    return {"hits": hits, "interesting_files": interesting_files}


def parse_args(argv: list[str]) -> tuple[Path, Path, list[str]]:
    profile = "proshot"
    skip_next = False
    args: list[str] = []
    for a in argv[1:]:
        if skip_next:
            skip_next = False
            continue
        if a == "--profile":
            skip_next = True
            continue
        if a.startswith("--profile="):
            profile = a.split("=", 1)[1].lower()
            continue
        args.append(a)
    if "--profile" in argv:
        i = argv.index("--profile")
        if i + 1 < len(argv) and not argv[i + 1].startswith("-"):
            profile = argv[i + 1].lower()
    if len(args) < 1:
        print("usage: proshot_decompile_scan.py <jadx_sources_dir> [out.json] [--profile proshot|motioncam|all]")
        sys.exit(2)
    root = Path(args[0])
    out = Path(args[1]) if len(args) > 1 else root.parent / "scan.json"
    needles = PROFILES.get(profile, PROFILES["proshot"])
    return root, out, needles


def main() -> None:
    root, out, needles = parse_args(sys.argv)
    data = scan_tree(root, needles)
    data["profile"] = sys.argv[sys.argv.index("--profile") + 1] if "--profile" in sys.argv else "proshot"
    out.write_text(json.dumps(data, indent=2), encoding="utf-8")
    print(f"wrote {out}")
    print("\nTop files per needle:")
    for needle in needles:
        top = data["hits"][needle][:3]
        if top:
            print(f"  {needle}:")
            for t in top:
                print(f"    {t['count']:4d}  {t['file']}")


if __name__ == "__main__":
    main()
