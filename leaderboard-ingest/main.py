"""Leaderboard submission ingest service for Render deployment."""

from __future__ import annotations

import hashlib
import json
import os
import uuid
from datetime import datetime, timezone
from pathlib import Path

from fastapi import FastAPI, HTTPException, Request

app = FastAPI(title="PNS Leaderboard Ingest")

PINS_PATH = Path(__file__).parent / "config" / "signing_pins.json"
REPLAY: set[str] = set()


def load_pins() -> set[str]:
    if not PINS_PATH.exists():
        return set()
    data = json.loads(PINS_PATH.read_text(encoding="utf-8"))
    return set(data.get("allowedCertSha256", []))


def recompute_digest(body: dict) -> str:
    parity = body.get("parityReport") or {}
    matrix = body.get("matrix") or {}
    cells = json.dumps(parity.get("cells"), sort_keys=True, separators=(",", ":"))
    scan = json.dumps(matrix.get("scanMeta"), sort_keys=True, separators=(",", ":"))
    catalog = str(parity.get("catalogVersion") or matrix.get("catalogVersion") or 0)
    canon = f"{cells}|{scan}|{catalog}"
    return hashlib.sha256(canon.encode("utf-8")).hexdigest()


def score_cells(cells: list) -> tuple[int, int]:
    score = 0
    max_score = 0
    for c in cells:
        max_score += 10
        if c.get("provenOk"):
            score += 10
        elif str(c.get("failReason", "")).startswith("skip:matrix_gate:"):
            score += 8
        elif c.get("advertised"):
            score += 4
    return score, max_score


def validate_submission(body: dict) -> tuple[str, str]:
    if body.get("schema") != "pns.leaderboard_submission.v1":
        return "auto_rejected", "bad_schema"
    digest = body.get("submissionDigest")
    if not digest or digest != recompute_digest(body):
        return "auto_rejected", "digest_mismatch"
    pins = load_pins()
    cert = body.get("appSigningCertSha256")
    if pins and cert not in pins:
        return "auto_rejected", "cert_not_allowed"
    cells = (body.get("parityReport") or {}).get("cells") or []
    if not cells:
        return "auto_rejected", "no_cells"
    if all(c.get("provenOk") for c in cells) and len(cells) > 50:
        return "pending_review", "all_proven_suspicious"
    mode = str((body.get("parityReport") or {}).get("mode", "")).lower()
    if mode != "full":
        return "pending_review", "quick_sweep"
    dur = (body.get("parityReport") or {}).get("durationMs") or 0
    if dur < 5000:
        return "auto_rejected", "duration_too_short"
    parity = body.get("parityReport") or {}
    if parity.get("resolutionBetrayalIndex") is None:
        return "pending_review", "missing_resolution_betrayal"
    mc = body.get("measurementContext") or parity.get("measurementContext") or {}
    if mc.get("api") != "camera2":
        return "pending_review", "bad_measurement_context"
    build_display = body.get("buildDisplay")
    if not build_display:
        product = (body.get("matrix") or {}).get("product") or {}
        build_display = (product.get("buildIdentity") or {}).get("display")
    if not build_display:
        return "pending_review", "missing_build_display"
    unlock = ((body.get("matrix") or {}).get("product") or {}).get("experimentalUnlockState") or {}
    if unlock.get("rootGranted"):
        return "approved", "root_unlocked_lane"
    return "approved", "auto_pass"


@app.get("/health")
def health():
    return {"ok": True}


@app.post("/v1/submit")
async def submit(request: Request):
    raw = await request.body()
    if len(raw) > 2_000_000:
        raise HTTPException(413, "payload too large")
    try:
        body = json.loads(raw.decode("utf-8"))
    except json.JSONDecodeError as exc:
        raise HTTPException(400, f"invalid json: {exc}") from exc

    digest = body.get("submissionDigest")
    if digest in REPLAY:
        raise HTTPException(409, "duplicate submission")
    REPLAY.add(digest)

    status, reason = validate_submission(body)
    submission_id = str(uuid.uuid4())
    artifact = {
        "submissionId": submission_id,
        "submittedUtc": body.get("submittedUtc") or datetime.now(timezone.utc).isoformat(),
        "status": status,
        "reason": reason,
        "trustTier": "community_verified" if status == "approved" and reason == "auto_pass" else "community_preview",
        **body,
    }

    out_root = Path(os.environ.get("SUBMISSION_OUT_DIR", Path(__file__).parent.parent / "docs" / "leaderboard" / "submissions"))
    subdir = "approved" if status == "approved" else "pending" if status == "pending_review" else "rejected"
    out_dir = out_root / subdir
    out_dir.mkdir(parents=True, exist_ok=True)
    (out_dir / f"{submission_id}.json").write_text(json.dumps(artifact, indent=2), encoding="utf-8")

    return {"submissionId": submission_id, "status": status, "reason": reason}
