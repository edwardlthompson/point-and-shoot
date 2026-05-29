#!/usr/bin/env python3
"""Reorganize BUILD_PLAN_COMPLETED.md by app feature (not milestones)."""
from __future__ import annotations

import re
from collections import defaultdict
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SRC = ROOT / "BUILD_PLAN_COMPLETED.md"
OUT = ROOT / "BUILD_PLAN_COMPLETED.md"

# (category title, category blurb, keywords)
CATEGORIES: list[tuple[str, str, list[str]]] = [
    (
        "Engineering, CI & automation",
        "Host build gates, FOSS compliance, CI/CD, release packaging, and repo automation scripts.",
        [
            "toolchain", "gradle", "detekt", "lint", "sbom", "foss", "license", "ci baseline",
            "github actions", "gitlab", "release packaging", "release_automation", "keystore",
            "zipalign", "assembleRelease", "compileDebugKotlin", "automation infrastructure",
            "sprint guardrail", "github_secrets", "bracket_regroup", "desktop_file_validate",
        ],
    ),
    (
        "Diagnostics, probes & engineering hub",
        "Camera capability probes, deep caps, shallow cache, debug hub, failure matrix, root read-only diagnostics.",
        [
            "probe", "deep_caps", "enc_probe", "exhaustive_probe", "capabilitiesprobe",
            "probe export", "shallow", "probe phase", "failure matrix", "probehub",
            "sessionmatrix", "ae_highlight_probe", "face_meter_probe", "orientation probe",
            "reprocess", "hardwarecaps", "capabilitygate", "shallowcapability",
            "root_privileged", "root_capability", "gfxinfo", "mediastore_probe",
        ],
    ),
    (
        "Fleet capability matrix & device policy",
        "Per-SKU matrix JSON, fleet profiles, ProShot DNG parity, verify matrix, encoder fleet slice.",
        [
            "fleetdevice", "fleet_matrix", "fleetmatrix", "fleetcamera", "proshot parity",
            "proshot pipeline", "proshot reference", "aux dng", "dng openability",
            "leaf dng", "wide-cal", "lock bisect", "still mode compare", "m13_3",
            "focal map", "fleet catalog", "policy plugin", "encoder fleet",
            "verify matrix", "fleet_device_matrix", "fleetpolicy", "oneplus13fleet",
            "generic fleet", "fleethal", "onboarded sku",
        ],
    ),
    (
        "Camera mapping & lens routing",
        "Dodge profile, focal slots 14–150 mm, tele 73/85/150, sensor crop, back-camera roles.",
        [
            "dodge", "sensorcrop", "cropplan", "backcamerarole", "focal equivalent",
            "focal slot", "tele routing", "focal mm", "physical lens", "lens strip",
            "hardware ↔ software", "dngdefaultusercrop", "pickfocal", "milestone 3",
            "35mm equiv", "≥12 mp",
        ],
    ),
    (
        "Preview chrome & operator UI",
        "Locked portrait finder, 7×3 grid, tray, safe area, DND, theme, navigation, chrome UX gate.",
        [
            "chrome", "finder", "7×3", "7x3", "quick grid", "bottom tray", "dual shutter",
            "self-timer", "flash mode", "safe area", "cutout", "dnd", "immersive",
            "gesture", "navigation_mode", "theme", "material design", "ux.1", "ux.2",
            "chrome ux", "screencap", "settings expand", "grid quick", "preview chrome",
            "status bar", "edge-to-edge", "interruptionfilter",
        ],
    ),
    (
        "Readout strip & shooting modes",
        "ISO/SS/EV chips, command dial, QR, macro mode, WB menu, stabilisation chip, mode menus.",
        [
            "readout", "command dial", "shooting-mode", "white balance menu", "raw badge",
            "stabchip", "stabilisation readout", "mode selector", "photo programs",
            "video programs", "qr scan", "barcode", "commanddialmode", "macro shooting",
            "video format chip", "inappvideoformat",
        ],
    ),
    (
        "Still capture — JPEG, RAW & DNG",
        "RAW12 pipeline, DNG save/loadability, metadata pairing, JPEG/AVIF, hardware JPEG, capture verify.",
        [
            "dng", "raw12", "raw still", "capture pipeline", "captureRawStill", "jpeg-only",
            "jpeg icc", "avif", "hardware jpeg", "stillcapture", "dngcreator", "tiff",
            "applyToDngUri", "leaf reconcile", "composed still", "haptic delay",
            "independent tonal", "nightscape", "exif focal", "tiffexif",
        ],
    ),
    (
        "Advanced still modes",
        "Bracketing BKT, ZSL, HDR still, burst, intervalometer, pre-capture buffer, smile still.",
        [
            "bracket", "bkt", "zsl", "hdr still", "burst mode", "intervalometer",
            "pre-capture buffer", "smile still", "still mode", "composed_still_burst",
            "stacking",
        ],
    ),
    (
        "Video recording & encoding",
        "MediaCodec, HFR 120/240, 4K/8K, HEVC/H.264/AV1, DCG/HDR10, timelapse, dual video, macro video.",
        [
            "video record", "mediacodec", "hfr", "4k", "8k", "hevc", "h.265", "h.264",
            "av1", "in-app video", "video format", "video matrix", "dual video",
            "timelapse", "macro video", "dcg", "hdr10", "raw video", "mcraw",
            "yuv+10", "slow-motion", "bitrate", "video stabilization", "letterbox video",
            "video repair", "resolution selector", "dual-iso", "dualiso",
            "mergeFrameRateForDisplay", "spatial audio metadata", "action_video_capture",
        ],
    ),
    (
        "Recording HUD & preview overlays",
        "Timecode, PPM meters, pillar HUD, histogram while recording, thermal, storage, peaking, false color.",
        [
            "timecode", "audio meter", "ppm", "pillar", "recording overlay",
            "histogram during", "showhistogramduringvideo", "power thermal",
            "storage remaining", "previewstatusbar", "false color", "zebra",
            "focus peaking", "rgb histogram", "luma histogram", "composition guide",
            "highlight clip",
        ],
    ),
    (
        "Audio capture & shutter feedback",
        "Mic paths, hi-fi AAC, wind filter, gain, source picker, shutter sounds, spatial audio.",
        [
            "audio capture", "pnsaudio", "shutter sound", "soundpool", "wind noise",
            "audiogain", "audio source", "audio focus", "noise suppressor", "spatial audio",
            "audio quality", "shutter haptic", "voiceover ducking", "video audio",
        ],
    ),
    (
        "Metering, exposure & ISO/shutter",
        "Highlight H-mode metering, AE lock, ISO band, shutter angle, manual readout chase.",
        [
            "highlight meter", "highlight-weighted", "ae lock", "ae highlight", "iso band",
            "shutter angle", "exposure compensation", "exposure chase", "spot/matrix",
            "metering", "ev comp", "locked iso", "locked ss", "previewaelock",
        ],
    ),
    (
        "Focus, AF & rack pulls",
        "Manual focus, peaking, rack focus waypoints, breathing compensation, focus mode picker.",
        [
            "focus peaking", "manual focus", "rack focus", "focus breathing", "focus mode",
            "macro focus", "focus assist", "previewcontroller",
        ],
    ),
    (
        "Face, eye & subject tracking",
        "Eye AF overlay, face priority AE, 3D tracking, smile capture, alignment probes, selfie ring.",
        [
            "eye af", "eye-af", "face detect", "face priority", "face hud", "face overlay",
            "ml kit", "facedetect", "tracker", "3d tracking", "selfie ring",
            "eye_af_alignment", "facemeter", "facealign",
        ],
    ),
    (
        "Color grading, LUT & picture profiles",
        "Preview/video LUT, HLG/Flat/Cine, picture profiles, BT.709/HLG VUI, independent tonal.",
        [
            "lut", "picture profile", "hlg", "flat/cine", "cinematic", "propictureprofile",
            "color profile", "video color", "bt709", "bt2020", "colorvui",
            "independent tonal", "video_hlg",
        ],
    ),
    (
        "Calibration & ICC",
        "ColorChecker workflow, chart detector, passport CE, Display P3 ICC in JPEG/AVIF.",
        [
            "calibrat", "colorchecker", "chart quad", "icc", "display p3", "jpegicc",
            "passport_ce", "de2000", "macbeth", "calibrationworkflow", "iccprofilebuilder",
        ],
    ),
    (
        "Gallery & saved media",
        "Bespoke in-app gallery, thumbnails, EXIF panel, share/delete, orientation, batch share.",
        [
            "gallery", "bespoke", "mediastore", "thumbnail", "pinch-zoom", "batch share",
            "dnggalleryorientation", "gallery letterbox", "media aspect", "pnsmediastore",
        ],
    ),
    (
        "Settings, HUD & workflow",
        "Settings grouping, HUD toggles, workflow presets, cloud backup, About/heritage screen.",
        [
            "hudsettings", "settings grouping", "engineering item", "workflow preset",
            "cloud backup", "about screen", "about heritage", "venmo", "pro hud",
            "feature gating", "hud ", "ux.3",
        ],
    ),
    (
        "Connectivity, tether & platform",
        "HTTP tether, Wi-Fi Direct LAN, WebDAV, LAN transfer, BLE shutter, deep links, widget.",
        [
            "tether", "wifi direct", "wifi-direct", "nsd", "28765", "28766", "lan media",
            "webdav", "deep link", "widget", "share receive", "platform integration",
            "connectivity", "collaborative", "social stream", "bluetooth", "avrcp",
            "remote shutter", "procapture", "tetheredcapture",
        ],
    ),
    (
        "Performance, memory & battery",
        "Memory profiler, adaptive FPS, thermal monitor, long-running pause, PO optimization gate.",
        [
            "memory profiler", "adaptive fps", "battery", "thermal", "longrunning",
            "bitmap guard", "performance", "profiling", "po.1", "po.2", "baseline profile",
            "reader backpressure", "ndk", "libavif", "libjxl", "native encoder",
        ],
    ),
    (
        "AI & scene-assisted capture",
        "Smile detection, scene vendor hints, CameraX extension filtering, bitrate scale.",
        [
            "smile", "scene hint", "ai feature", "camerax extension", "smilestill",
            "scenevendorhint", "videobitratescale",
        ],
    ),
]

DEFAULT = (
    "Other shipped work",
    "Completed items not matched above — skim for misc coverage.",
)


def clean_task(line: str) -> str:
    line = re.sub(r"^- \[[xX]\]\s*", "", line)
    line = re.sub(r"\*\*\[(AGENT|ADB|HOST|HUMAN|MIXED|DEVICE|ROOT)\]\*\*\s*", "", line)
    line = re.sub(r"\[(AGENT|ADB|HOST|HUMAN|MIXED|DEVICE|ROOT)\]\s*", "", line)
    line = re.sub(r"\*\*Sprint check:\*\*\s*", "", line)
    line = re.sub(r"\s+", " ", line).strip()
    line = re.sub(r"\*\*\s*\*\*", "", line)
    line = re.sub(r"^\*\*\s+", "", line)
    if re.match(r"^Sprint \d", line):
        return ""
    if len(line) > 200:
        line = line[:200].rsplit(" ", 1)[0] + "…"
    return line


def categorize(blob: str) -> str:
    b = blob.lower()
    best_name = DEFAULT[0]
    best_score = 0
    for name, _, keys in CATEGORIES:
        score = 0
        for k in keys:
            if k.lower() in b:
                score += max(3, len(k.split()))
        if score > best_score:
            best_score = score
            best_name = name
    return best_name


def main() -> None:
    # Read original milestone file if .bak exists, else current (may already be reorganized)
    src = ROOT / "BUILD_PLAN_COMPLETED.md.bak"
    if not src.exists():
        src = ROOT / "BUILD_PLAN_COMPLETED.md"
        # backup original once
        backup = ROOT / "BUILD_PLAN_COMPLETED.md.bak"
        if not backup.exists() and "by feature" not in src.read_text(encoding="utf-8")[:200]:
            backup.write_text(src.read_text(encoding="utf-8"), encoding="utf-8")

    text = src.read_text(encoding="utf-8") if src.name.endswith(".bak") else (
        (ROOT / "BUILD_PLAN_COMPLETED.md.bak").read_text(encoding="utf-8")
        if (ROOT / "BUILD_PLAN_COMPLETED.md.bak").exists()
        else src.read_text(encoding="utf-8")
    )

    # Always parse from .bak (original milestone structure)
    bak = ROOT / "BUILD_PLAN_COMPLETED.md.bak"
    if bak.exists():
        text = bak.read_text(encoding="utf-8")

    lines = text.splitlines()
    section = sprint = ""
    buckets: dict[str, list[str]] = defaultdict(list)
    seen: set[str] = set()

    for line in lines:
        if line.startswith("## "):
            section = line[3:].strip()
            sprint = ""
        elif line.startswith("### "):
            sprint = line[4:].strip()
        elif re.match(r"^- \[[xX]\]", line):
            task = clean_task(line)
            if len(task) < 8:
                continue
            cat = categorize(f"{section} {sprint} {task}")
            key = task.lower()
            if key in seen:
                continue
            seen.add(key)
            buckets[cat].append(task)

    out: list[str] = [
        "# Point & Shoot — completed work (by feature)",
        "",
        "Shipped tasks grouped by **app area** for manual review. "
        "Open human gates: **[BUILD_PLAN.md](BUILD_PLAN.md)** (*Milestone H*). "
        "USB artifacts: `hfr-runs/`. Technical settings: [`docs/PNS_TECHNICAL_SETTINGS.md`](docs/PNS_TECHNICAL_SETTINGS.md).",
        "",
        "## Contents",
        "",
    ]

    cat_order = [c[0] for c in CATEGORIES] + [DEFAULT[0]]
    cat_desc = {c[0]: c[1] for c in CATEGORIES}
    cat_desc[DEFAULT[0]] = DEFAULT[1]

    for i, cat in enumerate(cat_order, 1):
        if buckets.get(cat):
            anchor = cat.lower().replace(",", "").replace(" & ", "-").replace(" ", "-")
            anchor = re.sub(r"[^a-z0-9-]", "", anchor)
            out.append(f"{i}. [{cat}](#{anchor})")

    out.extend(["", "---", ""])
    total = 0
    for cat in cat_order:
        tasks = buckets.get(cat, [])
        if not tasks:
            continue
        total += len(tasks)
        anchor = cat.lower().replace(",", "").replace(" & ", "-").replace(" ", "-")
        anchor = re.sub(r"[^a-z0-9-]", "", anchor)
        out.append(f"## {cat}")
        out.append("")
        out.append(f"*{cat_desc.get(cat, '')}*")
        out.append("")
        for t in sorted(tasks, key=str.lower):
            out.append(f"- {t}")
        out.append("")
        out.append("---")
        out.append("")

    out.append(f"* **{total}** completed tasks indexed across **{len([c for c in cat_order if buckets.get(c)])}** categories.")
    out.append("")
    out.append("## Deferred or human-only (not counted above)")
    out.append("")
    out.append("- In-app RAW/JPEG development editor (CC.3 — deferred)")
    out.append("- Live colour temperature readout chip (15.33 — cancelled)")
    out.append("- Full dual-ISO HDR video merge (15.38 stub only; merge deferred)")
    out.append("- Wi-Fi Direct companion browser UI + push notifications (future backlog)")
    out.append("- Subjective sign-off rows (**H.7**, **H.8.1–H.8.6**) — active in BUILD_PLAN Milestone H")
    out.append("- 8K tier sometimes missing from in-app picker despite automation PASS (pinned investigation)")
    out.append("- H.265 DCG @4K color — owner visual fail 2026-05-26; re-open with H.8.3")

    OUT.write_text("\n".join(out), encoding="utf-8")
    print(f"Wrote {OUT} — {total} tasks")


if __name__ == "__main__":
    main()
