import json
import math
import sys
from dataclasses import dataclass
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw


@dataclass
class Point:
    x: float
    y: float


def _connected_components(mask: np.ndarray) -> list[tuple[int, int, int, int, int, float, float]]:
    """
    Returns components as:
      (minx, miny, maxx, maxy, area, cx, cy)
    """
    h, w = mask.shape
    seen = np.zeros_like(mask, dtype=np.uint8)
    comps = []
    for y in range(h):
        row = mask[y]
        for x in np.where(row & (seen[y] == 0))[0]:
            # BFS
            q = [(x, y)]
            seen[y, x] = 1
            minx = maxx = x
            miny = maxy = y
            area = 0
            sx = 0
            sy = 0
            while q:
                cx, cy = q.pop()
                area += 1
                sx += cx
                sy += cy
                if cx < minx:
                    minx = cx
                if cx > maxx:
                    maxx = cx
                if cy < miny:
                    miny = cy
                if cy > maxy:
                    maxy = cy
                # 4-neighborhood
                if cx > 0 and mask[cy, cx - 1] and not seen[cy, cx - 1]:
                    seen[cy, cx - 1] = 1
                    q.append((cx - 1, cy))
                if cx + 1 < w and mask[cy, cx + 1] and not seen[cy, cx + 1]:
                    seen[cy, cx + 1] = 1
                    q.append((cx + 1, cy))
                if cy > 0 and mask[cy - 1, cx] and not seen[cy - 1, cx]:
                    seen[cy - 1, cx] = 1
                    q.append((cx, cy - 1))
                if cy + 1 < h and mask[cy + 1, cx] and not seen[cy + 1, cx]:
                    seen[cy + 1, cx] = 1
                    q.append((cx, cy + 1))
            comps.append((minx, miny, maxx, maxy, area, sx / area, sy / area))
    return comps


def detect_green_markers(rgb: np.ndarray) -> list[Point]:
    r = rgb[:, :, 0].astype(np.int16)
    g = rgb[:, :, 1].astype(np.int16)
    b = rgb[:, :, 2].astype(np.int16)
    # "neon green" threshold: strong G, weak R/B
    mask = (g >= 170) & (r <= 120) & (b <= 120) & ((g - np.maximum(r, b)) >= 60)
    comps = _connected_components(mask.astype(np.uint8))
    # Keep medium-sized components; ignore noise
    pts = []
    for minx, miny, maxx, maxy, area, cx, cy in comps:
        w = (maxx - minx) + 1
        h = (maxy - miny) + 1
        if area < 25:
            continue
        if w > 250 or h > 250:
            continue
        pts.append(Point(cx, cy))
    # Prefer two markers near the preview finder region (avoid bottom tray / QS tiles).
    h = rgb.shape[0]
    pts = [p for p in pts if 220 <= p.y <= min(h - 1, 2050)]
    pts.sort(key=lambda p: (p.y, p.x))
    # Ensure two distinct points (avoid double-counting the same blob).
    distinct: list[Point] = []
    for p in pts:
        if all(dist(p, q) >= 35 for q in distinct):
            distinct.append(p)
        if len(distinct) == 2:
            break
    return distinct


def detect_face_box_skin(rgb: np.ndarray) -> tuple[int, int, int, int] | None:
    # crude skin mask in YCbCr; tuned to avoid green overlay
    img = rgb.astype(np.float32)
    r, g, b = img[:, :, 0], img[:, :, 1], img[:, :, 2]
    y = 0.299 * r + 0.587 * g + 0.114 * b
    cb = 128 - 0.168736 * r - 0.331264 * g + 0.5 * b
    cr = 128 + 0.5 * r - 0.418688 * g - 0.081312 * b
    mask = (y > 35) & (cb > 75) & (cb < 140) & (cr > 130) & (cr < 185)
    comps = _connected_components(mask.astype(np.uint8))
    if not comps:
        return None
    # Prefer components in the finder region
    comps.sort(key=lambda c: c[4], reverse=True)
    minx, miny, maxx, maxy, area, _, _ = comps[0]
    if area < 8000:
        return None
    return (minx, miny, maxx, maxy)


def detect_eyes_dark_blobs(rgb: np.ndarray, face_box: tuple[int, int, int, int]) -> list[Point]:
    x0, y0, x1, y1 = face_box
    w = x1 - x0 + 1
    h = y1 - y0 + 1
    # Search upper half of the face for dark blobs (eyes)
    ux0 = x0 + int(0.08 * w)
    ux1 = x1 - int(0.08 * w)
    uy0 = y0 + int(0.12 * h)
    uy1 = y0 + int(0.58 * h)
    roi = rgb[uy0:uy1, ux0:ux1].astype(np.float32)
    lum = 0.299 * roi[:, :, 0] + 0.587 * roi[:, :, 1] + 0.114 * roi[:, :, 2]
    # dark threshold relative to ROI median
    med = float(np.median(lum))
    thr = max(10.0, med * 0.55)
    mask = lum < thr
    comps = _connected_components(mask.astype(np.uint8))
    # Prefer medium sized components; eyes are not huge
    eye_cands = []
    for minx, miny, maxx, maxy, area, cx, cy in comps:
        if area < 80:
            continue
        bw = (maxx - minx) + 1
        bh = (maxy - miny) + 1
        if bw < 6 or bh < 4:
            continue
        if bw > 0.45 * (ux1 - ux0) or bh > 0.45 * (uy1 - uy0):
            continue
        # score: area with mild preference for upper region
        score = area * (1.2 - (cy / max(1, (uy1 - uy0))))
        eye_cands.append((score, cx + ux0, cy + uy0))
    if not eye_cands:
        return []
    eye_cands.sort(key=lambda t: t[0], reverse=True)
    pts = [Point(x, y) for _, x, y in eye_cands[:6]]
    # choose two that are horizontally separated
    best = None
    for i in range(len(pts)):
        for j in range(i + 1, len(pts)):
            a, b = pts[i], pts[j]
            dx = abs(a.x - b.x)
            dy = abs(a.y - b.y)
            if dx < 0.15 * w or dy > 0.20 * h:
                continue
            score = dx - dy * 0.5
            if best is None or score > best[0]:
                best = (score, a, b)
    if best is None:
        # fallback: top2 by x
        pts.sort(key=lambda p: p.x)
        return pts[:2]
    a, b = best[1], best[2]
    return sorted([a, b], key=lambda p: p.x)

def detect_eye_near_marker(rgb: np.ndarray, marker: Point) -> Point | None:
    h, w = rgb.shape[0], rgb.shape[1]
    cx = int(round(marker.x))
    cy = int(round(marker.y))
    # Window tuned to typical marker size / face distance in finder
    half_w = 120
    half_h = 90
    x0 = max(0, cx - half_w)
    x1 = min(w, cx + half_w)
    y0 = max(0, cy - half_h)
    y1 = min(h, cy + half_h)
    roi = rgb[y0:y1, x0:x1].astype(np.float32)
    if roi.size == 0:
        return None
    lum = 0.299 * roi[:, :, 0] + 0.587 * roi[:, :, 1] + 0.114 * roi[:, :, 2]
    # Weighted "dark + near marker" centroid. This avoids drifting to nose/cheek shadows.
    p = float(np.percentile(lum, 10.0))
    dark = np.clip(p - lum, 0.0, None)
    if float(dark.sum()) <= 1e-3:
        return None
    yy, xx = np.mgrid[0 : lum.shape[0], 0 : lum.shape[1]]
    mx = cx - x0
    my = cy - y0
    sigma = 42.0
    dist2 = (xx - mx) ** 2 + (yy - my) ** 2
    near = np.exp(-dist2 / (2.0 * sigma * sigma))
    wts = dark * near
    s = float(wts.sum())
    if s <= 1e-3:
        return None
    ex = float((wts * xx).sum() / s + x0)
    ey = float((wts * yy).sum() / s + y0)
    return Point(ex, ey)


def dist(a: Point, b: Point) -> float:
    return math.hypot(a.x - b.x, a.y - b.y)


def main() -> int:
    if len(sys.argv) < 3:
        print("usage: pns_eye_af_overlay_align.py <screencap.png> <out.json> [<out_annot.png>]")
        return 2
    in_path = Path(sys.argv[1])
    out_json = Path(sys.argv[2])
    out_annot = Path(sys.argv[3]) if len(sys.argv) >= 4 else None

    img = Image.open(in_path).convert("RGB")
    rgb = np.array(img)

    markers = detect_green_markers(rgb)
    face_box = detect_face_box_skin(rgb)
    eyes = detect_eyes_dark_blobs(rgb, face_box) if face_box else []
    if not eyes and len(markers) == 2:
        # Fallback: estimate each eye near its marker (works even when skin mask fails).
        e0 = detect_eye_near_marker(rgb, markers[0])
        e1 = detect_eye_near_marker(rgb, markers[1])
        eyes = [e for e in [e0, e1] if e is not None]

    result = {
        "input": str(in_path),
        "w": int(rgb.shape[1]),
        "h": int(rgb.shape[0]),
        "markers": [{"x": m.x, "y": m.y} for m in markers],
        "face_box": None
        if not face_box
        else {
            "x0": int(face_box[0]),
            "y0": int(face_box[1]),
            "x1": int(face_box[2]),
            "y1": int(face_box[3]),
        },
        "eyes": [{"x": e.x, "y": e.y} for e in eyes],
        "score": {},
        "pass": False,
    }

    if len(markers) == 2 and len(eyes) == 2:
        d0 = dist(markers[0], eyes[0])
        d1 = dist(markers[1], eyes[1])
        # allow swap if markers are reversed
        ds = d0 + d1
        d0s = dist(markers[0], eyes[1]) + dist(markers[1], eyes[0])
        if d0s < ds:
            d0 = dist(markers[0], eyes[1])
            d1 = dist(markers[1], eyes[0])
        thresh = 55.0  # pixels; tuned for marker-outline vs pupil centroid
        result["score"] = {"d0": float(d0), "d1": float(d1), "thresh": thresh}
        result["pass"] = (d0 <= thresh and d1 <= thresh)

    out_json.parent.mkdir(parents=True, exist_ok=True)
    out_json.write_text(json.dumps(result, indent=2), encoding="utf-8")

    if out_annot is not None:
        draw = ImageDraw.Draw(img)
        if face_box:
            draw.rectangle([face_box[0], face_box[1], face_box[2], face_box[3]], outline=(255, 200, 0), width=4)
        for i, m in enumerate(markers):
            draw.ellipse([m.x - 12, m.y - 12, m.x + 12, m.y + 12], outline=(0, 255, 0), width=4)
            draw.text((m.x + 14, m.y - 10), f"M{i}", fill=(0, 255, 0))
        for i, e in enumerate(eyes):
            draw.ellipse([e.x - 12, e.y - 12, e.x + 12, e.y + 12], outline=(255, 0, 0), width=4)
            draw.text((e.x + 14, e.y - 10), f"E{i}", fill=(255, 0, 0))
        out_annot.parent.mkdir(parents=True, exist_ok=True)
        img.save(out_annot)

    print(json.dumps(result, indent=2))
    return 0 if result["pass"] else 1


if __name__ == "__main__":
    raise SystemExit(main())

