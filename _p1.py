from pathlib import Path
path = Path("app/src/main/java/dev/pointandshoot/PreviewEngineScreen.kt")
s = path.read_text(encoding="utf-8")

# 1) Import IntOffset
if "import androidx.compose.ui.unit.IntOffset" not in s:
    s = s.replace(
        "import androidx.compose.ui.unit.IntSize\n",
        "import androidx.compose.ui.unit.IntSize\nimport androidx.compose.ui.unit.IntOffset\n",
        1,
    )

# 2) schedulePreviewPhysicalForFocalSlot: synchronous pin
old_sched = """    Handler(Looper.getMainLooper()).post {
        controller.setPreviewSurfacePhysicalCameraId(physical)
    }
}"""
new_sched = """    controller.setPreviewSurfacePhysicalCameraId(physical)
}"""
if old_sched not in s:
    raise SystemExit("schedule block not found")
s = s.replace(old_sched, new_sched, 1)

# 3) Remove android.os.Handler import if only used here - grep: Handler( still used elsewhere
# 4) Focal crop clamp: reorder when branches
old_when = """        val clamped =
            when {
                sid == uwId -> null
                sid == wideId ->
                    focalCrop?.takeIf {
                        it == FocalMode.Street35 || it == FocalMode.Standard50
                    }
                sid == teleId ->
                    focalCrop?.takeIf {
                        it == FocalMode.Portrait85 || it == FocalMode.LongTele150
                    }
                containsTele && containsWide ->
                    focalCrop?.takeIf {
                        it == FocalMode.Street35 ||
                            it == FocalMode.Standard50 ||
                            it == FocalMode.Portrait85 ||
                            it == FocalMode.LongTele150
                    }"""
new_when = """        val clamped =
            when {
                sid == uwId -> null
                containsTele && containsWide ->
                    focalCrop?.takeIf {
                        it == FocalMode.Street35 ||
                            it == FocalMode.Standard50 ||
                            it == FocalMode.Portrait85 ||
                            it == FocalMode.LongTele150
                    }
                sid == wideId ->
                    focalCrop?.takeIf {
                        it == FocalMode.Street35 || it == FocalMode.Standard50
                    }
                sid == teleId ->
                    focalCrop?.takeIf {
                        it == FocalMode.Portrait85 || it == FocalMode.LongTele150
                    }"""
if old_when not in s:
    raise SystemExit("focal clamp when not found")
s = s.replace(old_when, new_when, 1)

# 5) Video: remove failure-hold gate that blocks retries (lines after wantRecord true)
old_vid = """        if (inAppVideoShellStartFailureHold) return
        if (inAppVideoRecorder != null) return
        if (desiredFps >= 120) {"""
new_vid = """        if (inAppVideoRecorder != null) return
        if (desiredFps >= 120) {"""
if old_vid not in s:
    raise SystemExit("video hold gate not found")
s = s.replace(old_vid, new_vid, 1)

# 6) After camId validated in applyInAppVideoRecordingShellLocked, clear hold before prepare
old_cam = """        if (camId.isNullOrBlank()) {
            inAppVideoShellStartFailureHold = true
            mainHandler.post { onUi(InAppVideoRecordingUiEvent.StartFailed) }
            return
        }
        val chars = runCatching { cm.getCameraCharacteristics(camId) }.getOrNull()"""
new_cam = """        if (camId.isNullOrBlank()) {
            inAppVideoShellStartFailureHold = true
            mainHandler.post { onUi(InAppVideoRecordingUiEvent.StartFailed) }
            return
        }
        inAppVideoShellStartFailureHold = false
        val chars = runCatching { cm.getCameraCharacteristics(camId) }.getOrNull()"""
if old_cam not in s:
    raise SystemExit("camId block not found")
s = s.replace(old_cam, new_cam, 1)

# 7) previewHud state after previewTilePx
old_px = """    var previewTilePx by remember { mutableStateOf(IntSize.Zero) }
    val focusRequester = remember { FocusRequester() }"""
new_px = """    var previewTilePx by remember { mutableStateOf(IntSize.Zero) }
    /** Parent finder size + inner GL box offset — buffer→HUD uses same viewport as tap-to-meter ([applyTapFocusFromView]). */
    var previewHudParentPx by remember { mutableStateOf(IntSize.Zero) }
    var previewHudInnerOffsetPx by remember { mutableStateOf(IntOffset.Zero) }
    val focusRequester = remember { FocusRequester() }"""
if old_px not in s:
    raise SystemExit("previewTilePx block not found")
s = s.replace(old_px, new_px, 1)

# 8) eyeMarksView: use parent viewport and subtract inner offset
old_eye_remember = """    val eyeMarksView =
        remember(
            eyeMarksBuffer,
            previewTilePx,
            previewBufferSize,
            chrome.previewTextureCoverCrop,
            previewMirrorForOverlays,
        ) {
            val buf = previewBufferSize
            val vw = previewTilePx.width
            val vh = previewTilePx.height
            if (buf == null || vw <= 0 || vh <= 0) {
                emptyList()
            } else {
                eyeMarksBuffer.map { m ->
                    val (vx, vy) =
                        TexturePreviewFit.mapBufferToView(
                            m.position.x,
                            m.position.y,
                            vw,
                            vh,
                            buf.width,
                            buf.height,
                            coverCrop = chrome.previewTextureCoverCrop,
                        )
                    val xOut = if (previewMirrorForOverlays) vw.toFloat() - vx else vx
                    EyeMark(
                        Offset(xOut, vy),
                        m.confidence,
                        m.trackingLocked,
                        m.referenceTrack,
                    )
                }
            }
        }"""
new_eye_remember = """    val eyeMarksView =
        remember(
            eyeMarksBuffer,
            previewTilePx,
            previewHudParentPx,
            previewHudInnerOffsetPx,
            previewBufferSize,
            chrome.previewTextureCoverCrop,
            previewMirrorForOverlays,
        ) {
            val buf = previewBufferSize
            val vwParent = previewHudParentPx.width
            val vhParent = previewHudParentPx.height
            val ox = previewHudInnerOffsetPx.x.toFloat()
            val oy = previewHudInnerOffsetPx.y.toFloat()
            val vwGl = previewTilePx.width
            if (buf == null || vwGl <= 0 || previewTilePx.height <= 0) {
                emptyList()
            } else {
                val useParent = vwParent > 0 && vhParent > 0
                eyeMarksBuffer.map { m ->
                    val (vx, vy) =
                        if (useParent) {
                            val (vxP, vyP) =
                                TexturePreviewFit.mapBufferToView(
                                    m.position.x,
                                    m.position.y,
                                    vwParent,
                                    vhParent,
                                    buf.width,
                                    buf.height,
                                    coverCrop = chrome.previewTextureCoverCrop,
                                )
                            (vxP - ox) to (vyP - oy)
                        } else {
                            TexturePreviewFit.mapBufferToView(
                                m.position.x,
                                m.position.y,
                                vwGl,
                                previewTilePx.height,
                                buf.width,
                                buf.height,
                                coverCrop = chrome.previewTextureCoverCrop,
                            )
                        }
                    val xOut = if (previewMirrorForOverlays) vwGl.toFloat() - vx else vx
                    EyeMark(
                        Offset(xOut, vy),
                        m.confidence,
                        m.trackingLocked,
                        m.referenceTrack,
                    )
                }
            }
        }"""
if old_eye_remember not in s:
    raise SystemExit("eyeMarksView block not found")
s = s.replace(old_eye_remember, new_eye_remember, 1)

# 9) faceTrackBoxesView similar
old_face = """    val faceTrackBoxesView =
        remember(
            faceTrackBoxesBuffer,
            previewTilePx,
            previewBufferSize,
            chrome.previewTextureCoverCrop,
            previewMirrorForOverlays,
        ) {
            val buf = previewBufferSize
            val vw = previewTilePx.width
            val vh = previewTilePx.height
            if (buf == null || vw <= 0 || vh <= 0) {
                emptyList()
            } else {
                faceTrackBoxesBuffer.mapNotNull { box ->
                    val (vx0, vy0) =
                        TexturePreviewFit.mapBufferToView(
                            box.left,
                            box.top,
                            vw,
                            vh,
                            buf.width,
                            buf.height,
                            coverCrop = chrome.previewTextureCoverCrop,
                        )
                    val (vx1, vy1) =
                        TexturePreviewFit.mapBufferToView(
                            box.right,
                            box.bottom,
                            vw,
                            vh,
                            buf.width,
                            buf.height,
                            coverCrop = chrome.previewTextureCoverCrop,
                        )
                    val l0 = kotlin.math.min(vx0, vx1)
                    val t0 = kotlin.math.min(vy0, vy1)
                    val r0 = kotlin.math.max(vx0, vx1)
                    val b0 = kotlin.math.max(vy0, vy1)
                    val l = if (previewMirrorForOverlays) vw - r0 else l0
                    val r = if (previewMirrorForOverlays) vw - l0 else r0
                    val t = t0
                    val b = b0
                    if (r - l < 4f || b - t < 4f) return@mapNotNull null
                    FaceTrackBoxView(
                        rect =
                            androidx.compose.ui.geometry.Rect(
                                offset = Offset(l, t),
                                size = androidx.compose.ui.geometry.Size(r - l, b - t),
                            ),
                        trackingLocked = box.trackingLocked,
                    )
                }
            }
        }"""
new_face = """    val faceTrackBoxesView =
        remember(
            faceTrackBoxesBuffer,
            previewTilePx,
            previewHudParentPx,
            previewHudInnerOffsetPx,
            previewBufferSize,
            chrome.previewTextureCoverCrop,
            previewMirrorForOverlays,
        ) {
            val buf = previewBufferSize
            val vwParent = previewHudParentPx.width
            val vhParent = previewHudParentPx.height
            val ox = previewHudInnerOffsetPx.x.toFloat()
            val oy = previewHudInnerOffsetPx.y.toFloat()
            val vwGl = previewTilePx.width
            val vhGl = previewTilePx.height
            if (buf == null || vwGl <= 0 || vhGl <= 0) {
                emptyList()
            } else {
                val useParent = vwParent > 0 && vhParent > 0
                fun mapCorners(left: Float, top: Float, right: Float, bottom: Float): androidx.compose.ui.geometry.Rect {
                    val (vx0, vy0) =
                        if (useParent) {
                            val a =
                                TexturePreviewFit.mapBufferToView(
                                    left,
                                    top,
                                    vwParent,
                                    vhParent,
                                    buf.width,
                                    buf.height,
                                    coverCrop = chrome.previewTextureCoverCrop,
                                )
                            val b =
                                TexturePreviewFit.mapBufferToView(
                                    right,
                                    bottom,
                                    vwParent,
                                    vhParent,
                                    buf.width,
                                    buf.height,
                                    coverCrop = chrome.previewTextureCoverCrop,
                                )
                            (a.first - ox) to (a.second - oy) to (b.first - ox) to (b.second - oy)
                        } else {
                            val a =
                                TexturePreviewFit.mapBufferToView(
                                    left,
                                    top,
                                    vwGl,
                                    vhGl,
                                    buf.width,
                                    buf.height,
                                    coverCrop = chrome.previewTextureCoverCrop,
                                )
                            val b =
                                TexturePreviewFit.mapBufferToView(
                                    right,
                                    bottom,
                                    vwGl,
                                    vhGl,
                                    buf.width,
                                    buf.height,
                                    coverCrop = chrome.previewTextureCoverCrop,
                                )
                            a to b
                        }
                    val (p0, p1) = if (useParent) {
                        val (vx0, vy0, vx1, vy1) = vx0 as Any
                        null
                    } else {
                        null
                    }
                    throw RuntimeError()
                }
                emptyList()
            }
        }"""
# The face block got too complex - use simpler approach without inner function
