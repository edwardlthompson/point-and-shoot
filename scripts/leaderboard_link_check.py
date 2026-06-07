#!/usr/bin/env python3
"""Validate leaderboard static site links against a local HTTP server."""
from __future__ import annotations

import json
import re
import sys
import urllib.error
import urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1] / "docs" / "leaderboard"
BASE = "http://127.0.0.1:8765/leaderboard"
IMPORT_RE = re.compile(r"""from\s+['"](\./[^'"]+)['"]""")


def load_json(rel: str):
    return json.loads((ROOT / rel).read_text(encoding="utf-8-sig"))


def collect_module_chain() -> set[str]:
    seen: set[str] = set()
    queue = ["assets/app.js"]
    while queue:
        rel = queue.pop(0)
        if rel in seen:
            continue
        seen.add(rel)
        path = ROOT / rel.split("?")[0]
        if not path.is_file():
            continue
        text = path.read_text(encoding="utf-8")
        for m in IMPORT_RE.finditer(text):
            imp = m.group(1).split("?")[0]
            if imp.startswith("./"):
                nxt = str((path.parent / imp[2:]).relative_to(ROOT)).replace("\\", "/")
                queue.append(nxt)
    return seen


def http_check(url: str) -> tuple[int, str]:
    req = urllib.request.Request(url, method="GET")
    try:
        with urllib.request.urlopen(req, timeout=15) as resp:
            return resp.status, resp.headers.get("Content-Type", "")
    except urllib.error.HTTPError as e:
        return e.code, str(e.reason)
    except Exception as e:
        return 0, str(e)


def main() -> int:
    errors: list[str] = []
    warnings: list[str] = []

    # --- On-disk required boot assets ---
    site = load_json("data/site.json")
    slugs = site.get("deviceSlugs") or []
    required = [
        "index.html",
        "assets/style.css",
        "data/site.json",
        "data/product_groups.json",
        "data/oem_accountability.json",
        "data/glossary.json",
        "data/feed.json",
        "data/feed.xml",
        "data/leaderboard.csv",
    ]
    for slug in slugs:
        required.append(f"data/devices/{slug}.json")
    for rel in required:
        if not (ROOT / rel).is_file():
            errors.append(f"MISSING file: {rel}")

    # --- ES module import graph ---
    modules = collect_module_chain()
    for rel in sorted(modules):
        if not (ROOT / rel).is_file():
            errors.append(f"MISSING module: {rel}")

    # --- Product group slug refs ---
    groups = load_json("data/product_groups.json").get("groups") or []
    slug_set = set(slugs)
    for g in groups:
        gid = g.get("groupId")
        for v in g.get("testedVariants") or []:
            s = v.get("slug")
            if s and s not in slug_set:
                errors.append(f"product group {gid} references unknown slug: {s}")

    # --- Device JSON internal + external links ---
    external: set[str] = set()
    for slug in slugs:
        d = load_json(f"data/devices/{slug}.json")
        ident = d.get("identity") or {}
        pg = ident.get("productGroupId")
        if pg and not any(x.get("groupId") == pg for x in groups):
            errors.append(f"device {slug} productGroupId not found: {pg}")
        for link in ident.get("specLinks") or []:
            if link.get("url"):
                external.add(link["url"])
        if ident.get("gsmarenaUrl"):
            external.add(ident["gsmarenaUrl"])
        for link in (ident.get("externalScores") or []):
            if link.get("url"):
                external.add(link["url"])
        sensors = d.get("sensors") or {}
        if sensors.get("sourceUrl"):
            external.add(sensors["sourceUrl"])

    for g in groups:
        adv = g.get("advertisedSpec") or {}
        if adv.get("gsmarenaUrl"):
            external.add(adv["gsmarenaUrl"])

    # --- HTTP checks (local server must be running) ---
    http_paths = [
        "",
        "index.html",
        "assets/app.js?v=20260606d",
        "assets/device-detail.js?v=20260606d",
        "assets/style.css?v=20260606d",
        "data/site.json",
    ] + [f"data/devices/{s}.json" for s in slugs]
    for rel in http_paths:
        url = f"{BASE}/{rel}" if rel else f"{BASE}/"
        code, detail = http_check(url)
        if code != 200:
            errors.append(f"HTTP {code} {url} ({detail})")

    # --- Hash routes (data exists = routable) ---
    routes = ["#/oem"] + [f"#/device/{s}" for s in slugs]
    routes += [f"#/product/{g['groupId']}" for g in groups if g.get("groupId")]
    print(f"Routable hash routes ({len(routes)}): OK (data-backed)")

    # --- External URLs (HEAD/GET, warn on failure) ---
    ext_ok = 0
    for url in sorted(external):
        code, detail = http_check(url)
        if code in (200, 301, 302, 303, 307, 308):
            ext_ok += 1
        else:
            warnings.append(f"External {code}: {url} ({detail})")

    # --- GitHub issue templates (from device-detail.js) ---
    gh_templates = [
        "https://github.com/edwardlthompson/point-and-shoot/issues/new?template=leaderboard_device_request.md",
        "https://github.com/edwardlthompson/point-and-shoot/issues/new?template=leaderboard_dispute.md",
    ]
    for url in gh_templates:
        code, detail = http_check(url)
        if code not in (200, 301, 302, 303, 307, 308):
            warnings.append(f"GitHub template {code}: {url}")

    print(f"Local files checked: {len(required) + len(modules)}")
    print(f"HTTP local checks: {len(http_paths)}")
    print(f"External URLs checked: {len(external)} ({ext_ok} ok)")
    if warnings:
        print("\nWARNINGS:")
        for w in warnings:
            print(f"  {w}")
    if errors:
        print("\nERRORS:")
        for e in errors:
            print(f"  {e}")
        return 1
    print("\nAll local links and routes OK.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
