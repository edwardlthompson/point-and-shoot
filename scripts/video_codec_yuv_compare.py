#!/usr/bin/env python3
"""Sprint 15.2 — mean Cb/Cr delta between H.264 and H.265 clips (host ffmpeg)."""

from __future__ import annotations

import argparse
import shutil
import struct
import subprocess
import sys
from pathlib import Path


def _mean_uv_yuv420p(mp4: Path, frames: int, scale_w: int, scale_h: int) -> tuple[float, float]:
    if not shutil.which("ffmpeg"):
        raise RuntimeError("ffmpeg not on PATH")
    if not mp4.is_file():
        raise FileNotFoundError(mp4)

    vf = f"scale={scale_w}:{scale_h},format=yuv420p"
    cmd = [
        "ffmpeg",
        "-v",
        "error",
        "-i",
        str(mp4),
        "-an",
        "-vf",
        vf,
        "-frames:v",
        str(frames),
        "-f",
        "rawvideo",
        "-pix_fmt",
        "yuv420p",
        "pipe:1",
    ]
    proc = subprocess.run(cmd, capture_output=True, check=False)
    if proc.returncode != 0:
        err = proc.stderr.decode("utf-8", errors="replace").strip()
        raise RuntimeError(f"ffmpeg failed for {mp4}: {err}")

    w, h = scale_w, scale_h
    y_size = w * h
    uv_w, uv_h = w // 2, h // 2
    uv_size = uv_w * uv_h
    frame_bytes = y_size + 2 * uv_size
    data = proc.stdout
    if len(data) < frame_bytes:
        raise RuntimeError(f"short read {len(data)} < {frame_bytes} for {mp4}")

    u_sum = 0.0
    v_sum = 0.0
    count = 0
    offset = 0
    while offset + frame_bytes <= len(data) and count < frames:
        u_plane = data[offset + y_size : offset + y_size + uv_size]
        v_plane = data[offset + y_size + uv_size : offset + frame_bytes]
        u_sum += sum(u_plane) / uv_size
        v_sum += sum(v_plane) / uv_size
        count += 1
        offset += frame_bytes

    if count == 0:
        raise RuntimeError(f"no frames decoded from {mp4}")
    return u_sum / count, v_sum / count


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("h264_mp4", type=Path)
    parser.add_argument("hevc_mp4", type=Path)
    parser.add_argument("--frames", type=int, default=10)
    parser.add_argument("--max-cb-cr-delta", type=float, default=8.0)
    parser.add_argument("--scale", default="320x180", help="WxH for fast decode")
    args = parser.parse_args()

    try:
        sw, sh = (int(x) for x in args.scale.split("x", 1))
    except ValueError as exc:
        print(f"invalid --scale: {args.scale}", file=sys.stderr)
        return 2

    u_h264, v_h264 = _mean_uv_yuv420p(args.h264_mp4, args.frames, sw, sh)
    u_hevc, v_hevc = _mean_uv_yuv420p(args.hevc_mp4, args.frames, sw, sh)
    du = abs(u_h264 - u_hevc)
    dv = abs(v_h264 - v_hevc)
    ok = du < args.max_cb_cr_delta and dv < args.max_cb_cr_delta

    print(
        f"h264 U={u_h264:.2f} V={v_h264:.2f} | hevc U={u_hevc:.2f} V={v_hevc:.2f} | "
        f"dU={du:.2f} dV={dv:.2f} max={args.max_cb_cr_delta}"
    )
    if ok:
        print("YUV_COMPARE: PASS")
        return 0
    print("YUV_COMPARE: FAIL", file=sys.stderr)
    return 1


if __name__ == "__main__":
    sys.exit(main())
