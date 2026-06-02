        previewReadoutIso = c.previewMeterIso(),
        previewReadoutExposureNs = c.previewMeterExposureNs(),
    val context = LocalContext.current
    val snackbarHostState = LocalPnsSnackbarHostState.current
    val settings = hudState.current
    val chrome = chromePrefs.current
    val imagingProfile = composedStillIntent.storageProfile()
    val focalMapCalibratingHint = rememberFocalMapCalibratingHintVisible()
    val fpsTargetEditable =
        CaptureMediaFamily.fromPrimaryPhoto(primaryPhoto) == CaptureMediaFamily.Video || isSweeping
    val captureScope = rememberCoroutineScope()
    var commandDialMode by remember(adbInitialDial) {
        mutableStateOf(
            adbInitialDial ?: HudSettings.loadCommandDialMode(context),
        )
    }
    LaunchedEffect(primaryPhoto, commandDialMode) {
        val allowed =
            CaptureMediaFamily.commandDialModesFor(CaptureMediaFamily.fromPrimaryPhoto(primaryPhoto))
                .toSet()
        if (commandDialMode !in allowed) {
            commandDialMode = CommandDialMode.Auto
            HudSettings.saveCommandDialMode(context, CommandDialMode.Auto)
        }
    }
    LaunchedEffect(commandDialMode, selectedCameraId) {
        if (commandDialMode == CommandDialMode.M) {
            controller.ensureManualFocusForDialM()
        } else {
            controller.clearManualFocusDistance()
        }
    }
    var selfTimerRemaining by remember { mutableIntStateOf(0) }
    var selfTimerCountdownActive by remember { mutableStateOf(false) }

    fun triggerStillCapture() {
        val delaySec =
            PreviewChromePreferences.normalizeSelfTimerDelaySec(chromePrefs.current.selfTimerDelaySec)
        if (commandDialMode == CommandDialMode.BKT) {
            val pat = HudSettings.loadBracketPattern(context)
            when {
                controller.canCaptureBracketBurst() -> onBracketBurst(pat)
                else ->
                    captureScope.pnsShowSnackbar(
                        snackbarHostState,
                        "Bracket: set IMG tiers (RAW and/or JPEG) and preview Î“Ã«Ã±119 fps.",
                        longDuration = true,
                    )
            }
            return
        }
        if (delaySec <= 0) {
            onCaptureDng()
            return
        }
        if (selfTimerCountdownActive) return
        selfTimerCountdownActive = true
        captureScope.launch {
            try {
                var remaining = delaySec
                while (remaining > 0) {
                    selfTimerRemaining = remaining
                    delay(1000)
                    remaining--
                }
                selfTimerRemaining = 0
                onCaptureDng()
            } finally {
                selfTimerCountdownActive = false
                selfTimerRemaining = 0
            }
        }
    }

    val liveChartTarget = remember { BundledReferenceTargets.Generic24 }
    var chartCorners by remember { mutableStateOf<List<Offset>>(emptyList()) }
    LaunchedEffect(chrome.liveChartCornerOverlay) {
        if (!chrome.liveChartCornerOverlay) chartCorners = emptyList()
    }
    var centerViewSize by remember { mutableStateOf(IntSize.Zero) }
    /** TextureView / rotated inner box size in px (for bufferÎ“Ã¥Ã†eye-mark mapping; not the full letterboxed viewport). */
    var previewTilePx by remember { mutableStateOf(IntSize.Zero) }
    /** Parent finder + inner GL box offset Î“Ã‡Ã¶ matches [applyTapFocusFromView] viewport. */
    var previewHudParentPx by remember { mutableStateOf(IntSize.Zero) }
    var previewHudInnerOffsetPx by remember { mutableStateOf(IntOffset.Zero) }
    val focusRequester = remember { FocusRequester() }
    var lastStillPostReadout by remember { mutableStateOf<StillPostReadoutSnapshot?>(null) }
    DisposableEffect(controller) {
        val listener: (StillPostReadoutSnapshot?) -> Unit = { lastStillPostReadout = it }
        controller.setLastStillPostReadoutListener(listener)
        onDispose { controller.setLastStillPostReadoutListener(null) }
    }
    var afShutterGateActiveForUi by remember { mutableStateOf(false) }
    DisposableEffect(controller) {
        controller.setAfShutterGateUiListener { active -> afShutterGateActiveForUi = active }
        onDispose { controller.setAfShutterGateUiListener(null) }
    }
    // Same-frame sync: LaunchedEffect runs after the first frame, so a TextureView-driven
    // maybeRestart could observe a stale dial on the controller Î“Ã‡Ã¶ SideEffect aligns first.
    SideEffect {
        controller.setCommandDialMode(commandDialMode)
    }
    SideEffect {
        controller.setPreviewTextureCoverCrop(chrome.previewTextureCoverCrop)
    }
    SideEffect {
        controller.setPreviewFlashMode(chrome.previewFlashMode)
    }
    TrackModeTransition("camera", selectedCameraId ?: "null")
    TrackModeTransition("fps", selectedFps.toString())
    TrackModeTransition(
        "imaging_profile",
        runCatching { imagingProfile.id }.getOrElse { "invalid_profile" },
    TrackModeTransition("recording", isRecording.toString())
    TrackModeTransition("focal_crop", focalCrop?.name ?: "null")
    TrackModeTransition("command_dial", commandDialMode.name)
    TrackModeTransition("primary_photo", primaryPhoto.toString())
    // Highlight (H) metering + hardware highlight AE need a non-HFR preview session: [createSession] only
    // attaches YUV when `desiredFps < 120` under `!useHighSpeed`. Default fps is 120, so H at 120 skips YUV.
    LaunchedEffect(commandDialMode, selectedFps, fpsOptions) {
        if (commandDialMode != CommandDialMode.H) return@LaunchedEffect
        if (selectedFps < 120) return@LaunchedEffect
        val cap = fpsOptions.asSequence().map { it.targetFps }.filter { it < 120 }.maxOrNull()
        if (cap == null) {
            Log.w("PNS.Preview", "Highlight (H): no fps ladder entry below 120; YUV metering unavailable")
            return@LaunchedEffect
        }
        val prev = selectedFps
        if (cap != prev) {
            onSetFps(cap)
            Log.i("PNS.Preview", "Highlight (H): preview fps set to $cap for YUV metering (was $prev)")
        }
    }
    var eyeMarksBuffer by remember { mutableStateOf<List<EyeMark>>(emptyList()) }
    var faceTrackBoxesBuffer by remember { mutableStateOf<List<FaceTrackBoxBuffer>>(emptyList()) }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    LaunchedEffect(settings.showEyeAfOverlay) {
        controller.setHudFaceOverlayEnabled(settings.showEyeAfOverlay)
    }

    LaunchedEffect(settings.showCommandDial) {
        Log.i(
            "PNS.ChromeUx",
            if (settings.showCommandDial) {
                "modeDialPopout=anchorVisible"
            } else {
                "modeDialPopout=skipped_no_dial"
            },
        )
    }

    val stillCaptureJpegCompanionPref = chromePrefs.current.stillCaptureJpegCompanion
    LaunchedEffect(previewJpegCompanion, composedStillIntent, stillCaptureJpegCompanionPref) {
        val captureLabel =
            PreviewReadoutStillPipeline.chromeUxLogValue(
                composedStillIntent,
                stillCaptureJpegCompanionPref,
                previewJpegCompanion,
            )
        Log.i(
            "PNS.ChromeUx",
            "readoutCapture=$captureLabel",
        )
    }

    DisposableEffect(controller) {
        controller.setFaceHudOverlayListener { state ->
            eyeMarksBuffer = state.eyeMarks
            faceTrackBoxesBuffer = state.faceBoxesBuffer
        }
        onDispose {
            controller.setFaceHudOverlayListener(null)
        }
    }

    var previewHistogramBins by remember { mutableStateOf<IntArray?>(null) }
    DisposableEffect(controller) {
        controller.setPreviewHistogramListener { previewHistogramBins = it }
        onDispose {
            controller.setPreviewHistogramListener(null)
        }
    }

    LaunchedEffect(settings.showHistogram, controller) {
        controller.setPreviewHistogramEnabled(settings.showHistogram)
    }

    var highlightClipZebraFrame by remember { mutableStateOf<HighlightClipZebraFrame?>(null) }
    DisposableEffect(controller) {
        controller.setHighlightClipZebraListener { highlightClipZebraFrame = it }
        onDispose {
            controller.setHighlightClipZebraListener(null)
            highlightClipZebraFrame = null
        }
    }
    LaunchedEffect(settings.showHighlightClipZebra, controller) {
        controller.setHighlightClipZebraEnabled(settings.showHighlightClipZebra)
    }

    // Sony-Photography-Pro chrome rotation: each rail icon / settings cube counter-rotates
    // about its own centre while the preview texture stays visually fixed (buffer aspect + fit
    // transform only; device rotation does not re-layout the preview).
    // Per-element rotation keeps the rails fixed in screen position while only the glyphs
    // spin to read upright.
    val uiRotationDeg = deviceUiRotationState.snappedDegrees
    val uiRotationDegSmooth = deviceUiRotationState.smoothDegrees

    var calibrateOverlayActive by remember { mutableStateOf(false) }
    var calibratePendingInitialBitmap by remember { mutableStateOf<Bitmap?>(null) }

    fun openCalibrateFromPreviewFrame() {
        val gl = previewHostSlot.view
        if (gl == null) {
            captureScope.pnsShowSnackbar(snackbarHostState, "Preview not ready.")
            return
        }
        captureScope.launch(Dispatchers.IO) {
            val bmp = controller.grabPreviewFrameBitmap(gl)
            if (bmp == null) {
                withContext(Dispatchers.Main) {
                    captureScope.pnsShowSnackbar(snackbarHostState, "Could not grab preview frame.")
                }
                return@launch
            }
            withContext(Dispatchers.Main) {
                calibratePendingInitialBitmap = bmp
                calibrateOverlayActive = true
            }
        }
    }

    LaunchedEffect(adbCalibrateGrabSmoke, controller) {
        if (!adbCalibrateGrabSmoke) return@LaunchedEffect
        PnsAdbLog.i(context, "calibrate preview grab smoke: polling GLSurfaceView")
        repeat(90) {
            delay(400)
            val gl = previewHostSlot.view
            if (gl != null && gl.width > 0 && gl.height > 0) {
                // RENDERMODE_WHEN_DIRTY: nudge a frame before PixelCopy.
                gl.requestRender()
                // [grabPreviewFrameBitmap] posts PixelCopy completion to the main looper and awaits
                // on the caller thread Î“Ã‡Ã¶ must not run that await on Main or we deadlock (Milestone 6
                // `calibrate preview frame grab ok` gate never fired on device).
                val bmp =
                    withContext(Dispatchers.IO) {
                        controller.grabPreviewFrameBitmap(gl)
                    }
                if (bmp != null) {
                    bmp.recycle()
                    return@LaunchedEffect
                }
            }
        }
        PnsAdbLog.e(context, "calibrate preview grab smoke FAILED (no successful grab)")
    }

    val readoutMenuSnapshot =
        remember(selectedCameraId) {
            controller.readoutMenuSnapshot()
        }

    val previewMirrorForOverlays = controller.previewMirrorHorizontally()
    val eyeMarksView =
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
            val vwGl = previewTilePx.width
            val vhGl = previewTilePx.height
            val vwParent = previewHudParentPx.width
            val vhParent = previewHudParentPx.height
            val ox = previewHudInnerOffsetPx.x.toFloat()
            val oy = previewHudInnerOffsetPx.y.toFloat()
            if (buf == null || vwGl <= 0 || vhGl <= 0) {
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
                                vhGl,
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
        }

    val faceTrackBoxesView =
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
            val vwGl = previewTilePx.width
            val vhGl = previewTilePx.height
            val vwParent = previewHudParentPx.width
            val vhParent = previewHudParentPx.height
            val ox = previewHudInnerOffsetPx.x.toFloat()
            val oy = previewHudInnerOffsetPx.y.toFloat()
            if (buf == null || vwGl <= 0 || vhGl <= 0) {
                emptyList()
            } else {
                val useParent = vwParent > 0 && vhParent > 0
                faceTrackBoxesBuffer.mapNotNull { box ->
                    val (vx0, vy0) =
                        if (useParent) {
                            val p =
                                TexturePreviewFit.mapBufferToView(
                                    box.left,
                                    box.top,
                                    vwParent,
                                    vhParent,
                                    buf.width,
                                    buf.height,
                                    coverCrop = chrome.previewTextureCoverCrop,
                                )
                            (p.first - ox) to (p.second - oy)
                        } else {
                            TexturePreviewFit.mapBufferToView(
                                box.left,
                                box.top,
                                vwGl,
                                vhGl,
                                buf.width,
                                buf.height,
                                coverCrop = chrome.previewTextureCoverCrop,
                            )
                        }
                    val (vx1, vy1) =
                        if (useParent) {
                            val p =
                                TexturePreviewFit.mapBufferToView(
                                    box.right,
                                    box.bottom,
                                    vwParent,
                                    vhParent,
                                    buf.width,
                                    buf.height,
                                    coverCrop = chrome.previewTextureCoverCrop,
                                )
                            (p.first - ox) to (p.second - oy)
                        } else {
                            TexturePreviewFit.mapBufferToView(
                                box.right,
                                box.bottom,
                                vwGl,
                                vhGl,
                                buf.width,
                                buf.height,
                                coverCrop = chrome.previewTextureCoverCrop,
                            )
                        }
                    val l0 = kotlin.math.min(vx0, vx1)
                    val t0 = kotlin.math.min(vy0, vy1)
                    val r0 = kotlin.math.max(vx0, vx1)
                    val b0 = kotlin.math.max(vy0, vy1)
                    val l = if (previewMirrorForOverlays) vwGl - r0 else l0
                    val r = if (previewMirrorForOverlays) vwGl - l0 else r0
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
        }

    val layoutDirection = LocalLayoutDirection.current
    val previewChromeModifier =
        Modifier
            .fillMaxSize()
            .background(PnsColors.Charcoal)
            .padding(
                start = padding.calculateStartPadding(layoutDirection),
                top = 0.dp,
                end = padding.calculateEndPadding(layoutDirection),
                bottom = padding.calculateBottomPadding(),
            )
            .focusRequester(focusRequester)
            .focusable()
            .onPreviewKeyEvent {
                if (it.nativeKeyEvent.action != AndroidKeyEvent.ACTION_DOWN) return@onPreviewKeyEvent false
                if (!chrome.volumeKeysCapture) return@onPreviewKeyEvent false
                when (it.nativeKeyEvent.keyCode) {
                    AndroidKeyEvent.KEYCODE_VOLUME_UP -> {
                        when {
                            commandDialMode == CommandDialMode.BKT && controller.canCaptureBracketBurst() ->
                                onBracketBurst(HudSettings.loadBracketPattern(context))
                            controller.canCaptureStill() && !afShutterGateActiveForUi -> triggerStillCapture()
                            else ->
                                captureScope.pnsShowSnackbar(
                                    snackbarHostState,
                                    "DNG/BKT: switch preview to 119 fps or below (RAW session); BKT needs dial on BKT",
                                    longDuration = true,
                                )
                        }
                        true
                    }
                    AndroidKeyEvent.KEYCODE_VOLUME_DOWN -> {
                        val next = !isRecording
                        onRecordingChange(next)
                        captureScope.pnsShowSnackbar(
                            snackbarHostState,
                            if (next) "Recording started (volume down)" else "Recording stopped (volume down)",
                            longDuration = false,
                        )
                        true
                    }
                    else -> false
                }
            }

    // Preview tile: **3:4** width:height (4:3 sensor upright Î“Ã‡Ã¶ long edge vertical). Chrome scroll stack fills remaining height.
    Box(modifier = previewChromeModifier) {
        var frontRearSpotlightStep by remember { mutableIntStateOf(-1) }
        val spotlightCtx = context.applicationContext
        LaunchedEffect(Unit) {
            if (!PnsUiHintsStore.hasSeenFrontRearSpotlight(spotlightCtx)) {
                frontRearSpotlightStep = 0
            }
        }
        val showBottomTrayForSpotlight =
            chrome.showOnScreenShutter || lastGalleryUri != null || settings.showCommandDial
        if (frontRearSpotlightStep in 0..2) {
            val spotlightBody =
                when (frontRearSpotlightStep) {
                    0 ->
                        "On the live preview (not the tray or side tiles), swipe up for the front camera " +
                            "and swipe down to return to rear cameras. System edge back/home gestures can steal tall vertical drags Î“Ã‡Ã¶ " +
                            "use Capture and tools Î“Ã¥Ã† Front / Rear if a swipe fails."
                    1 ->
                        if (showBottomTrayForSpotlight) {
                            "When the bottom shutter strip is visible, use it for Photo vs Video and the on-screen shutter."
                        } else {
                            "Enable the on-screen shutter or mode strip in Settings if you want Photo vs Video controls on the bottom."
                        }
                    2 ->
                        if (settings.showCommandDial) {
                            "The Mode dial in the tray switches P, Auto, S, M, H, BKT, and more (HUD can hide it)."
                        } else {
                            "Turn on Show command dial in HUD settings to open P / Auto / S / M from the tray."
                        }
                    else -> ""
                }
            AlertDialog(
                onDismissRequest = {
                    PnsUiHintsStore.markFrontRearSpotlightSeen(spotlightCtx)
                    frontRearSpotlightStep = -1
                },
                title = {
                    Text("Preview quick tour", color = Color.White)
                },
                text = {
                    Text(
                        spotlightBody,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.88f),
                    )
                },
                confirmButton = {
                    if (frontRearSpotlightStep < 2) {
                        TextButton(
                            onClick = { frontRearSpotlightStep = frontRearSpotlightStep + 1 },
                        ) {
                            Text("Next", color = PnsColors.PhotoOrange)
                        }
                    } else {
                        TextButton(
                            onClick = {
                                PnsUiHintsStore.markFrontRearSpotlightSeen(spotlightCtx)
                                frontRearSpotlightStep = -1
                            },
                        ) {
                            Text("Got it", color = PnsColors.PhotoOrange)
                        }
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = {
                            PnsUiHintsStore.markFrontRearSpotlightSeen(spotlightCtx)
                            frontRearSpotlightStep = -1
                        },
                    ) {
                        Text("Skip", color = Color.White.copy(alpha = 0.75f))
                    }
                },
                containerColor = PnsColors.Charcoal,
            )
        }
        Column(modifier = Modifier.fillMaxSize()) {
            // Top Î“Ã¥Ã† bottom: inset band, finder, readout chips, 7â”œÃ¹3 quick settings (+ focal row), shutter tray.
            // Canonical spec: docs/preview-chrome-layout-style-guide.md + .cursor/rules/preview-chrome-ui-lock.mdc
            val topInsetBand = padding.calculateTopPadding()
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(topInsetBand)
                        .background(PnsColors.Charcoal),
            )
            PreviewChromeSectionDivider()
            // Share vertical space with the chrome rail ([PreviewChromeFinderFlexWeight] : rail).
            // Target **width / height = 3 / 4**; when the slot is tall enough, use full width and
            // exact height (no side letterbox). Otherwise fit inside the slot without clipping.
            BoxWithConstraints(
                modifier =
                    Modifier
                        .weight(PreviewChromeFinderFlexWeight)
                        .fillMaxWidth()
                        // Keep preview + overlays from painting into the chrome below when collapsed.
                        .clip(RectangleShape),
            ) {
                val targetAspect = 3f / 4f // width / height
                val idealTileH = maxWidth / targetAspect
                val tileW: Dp
                val tileH: Dp
                if (idealTileH <= maxHeight) {
                    tileW = maxWidth
                    tileH = idealTileH
                } else if (maxWidth / maxHeight >= targetAspect) {
                    tileW = maxHeight * targetAspect
                    tileH = maxHeight
                } else {
                    tileW = maxWidth
                    tileH = maxWidth / targetAspect
                }
                val bandAlignment =
                    if (idealTileH <= maxHeight) {
                        // Slack sits toward the status bar; preview sits just above the readout strip.
                        Alignment.BottomCenter
                    } else {
                        Alignment.Center
                    }
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = bandAlignment,
                ) {
                    Box(
                        modifier =
                            Modifier
                                .width(tileW)
                                .height(tileH),
                    ) {
                        PreviewMainViewport(
                            modifier = Modifier.fillMaxSize(),
                            centerViewSize = centerViewSize,
                            onCenterViewSize = { centerViewSize = it },
                            onPreviewTilePx = { previewTilePx = it },
                            onPreviewHudViewport = { sz, off ->
                                previewHudParentPx = sz
                                previewHudInnerOffsetPx = off
                            },
                            previewHostSlot = previewHostSlot,
                            controller = controller,
                            uiRotationDeg = uiRotationDeg,
                            uiRotationDegSmooth = uiRotationDegSmooth,
                            hudState = hudState,
                            compositionGuide = compositionGuide,
                            previewBufferSize = previewBufferSize,
                            isRecording = isRecording,
                            eyeMarks = eyeMarksView,
                            faceTrackBoxes = faceTrackBoxesView,
                            focusRequester = focusRequester,
                            previewTextureCoverCrop = chrome.previewTextureCoverCrop,
                            tapPreviewToCapture = chrome.tapPreviewToCapture,
                            liveChartCornerOverlay = chrome.liveChartCornerOverlay,
                            chartCorners = chartCorners,
                            onChartCornersChange = { chartCorners = it },
                            liveChartRows = liveChartTarget.rows,
                            liveChartCols = liveChartTarget.cols,
                            sensorOrientationDeg = sensorOrientationDeg,
                            previewHistogramBins = previewHistogramBins,
                            highlightClipZebraFrame = highlightClipZebraFrame,
                            previewMirrorHorizontally = controller.previewMirrorHorizontally(),
                            onSwitchToFrontCamera = onSwitchToFrontCamera,
                            onSwitchToRearCamera = onSwitchToRearCamera,
                            onCaptureDng = { triggerStillCapture() },
                            afShutterGateBlocksTapCapture = afShutterGateActiveForUi,
                            commandDialMode = commandDialMode,
                            videoPrimaryPreview = !primaryPhoto,
                            selectedFps = selectedFps,
                            enableResearchDcgHdr =
                                settings.enableResearchDcgHDR || adbAutomationVideoDcg,
                            adbForcePowerThermalOverlay = adbForcePowerThermalOverlay,
                            videoEncodeSize = videoEncodeSize,
                            adbStorageAvailableBytes = adbStorageAvailableBytes,
                            adbAutomationVideoDcg = adbAutomationVideoDcg,
                            adbAutomationVideoTenBit = adbAutomationVideoTenBit,
                            adbAutomationVideoRawSec = adbAutomationVideoRawSec,
                            rawVideoLane =
                                settings.videoEncodeLane == VideoEncodeLane.Raw ||
                                    adbAutomationVideoRawSec > 0,
                        )
                        if (selfTimerRemaining > 0) {
                            Box(
                                modifier =
                                    Modifier
                                        .fillMaxSize()
                                        .background(Color.Black.copy(alpha = 0.45f)),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = selfTimerRemaining.toString(),
                                    style = MaterialTheme.typography.displayLarge,
                                    color = Color.White,
                                )
                            }
                        }
                    }
                }
            }
            PreviewChromeSectionDivider()
            PreviewReadoutStrip(
                iso = previewReadoutIso,
                exposureNs = previewReadoutExposureNs,
                awbMode = previewReadoutAwbMode,
                measuredFps = measuredFps,
                stillCaptureJpegCompanion = chrome.stillCaptureJpegCompanion,
                sessionJpegCompanionReady = previewJpegCompanion,
                composedStillIntent = composedStillIntent,
                menu = readoutMenuSnapshot,
                fpsOptions = fpsOptions,
                fpsTargetEditable = fpsTargetEditable,
                videoResSelectorVisible = fpsTargetEditable,
                videoEncodeSizes = videoEncodeSizes,
                videoEncodeShortLabel = videoEncodeShortLabel,
                onPickVideoEncodeSize = onPickVideoEncodeSize,
                onPickIso = { iso -> controller.setReadoutManualIso(iso) },
                onPickShutter = { ns -> controller.setReadoutManualShutter(ns) },
                onPickAwb = { mode -> controller.setReadoutManualAwbMode(mode) },
                onPickFps = onSetFps,
                stillLut = settings.stillsLut(),
                videoLut = settings.videoLut(),
                onPickStillLut = { entry ->
                    hudState.update(settings.copy(selectedLutForStills = entry.name))
                },
                onPickVideoLut = { entry ->
                    hudState.update(settings.copy(selectedLutForVideo = entry.name))
                },
                onComposedStillIntentChange = onComposedStillIntentChange,
                onGrayCardWb = {
                    controller.applyGrayCardWhiteBalance { err ->
                        if (err != null) {
                            captureScope.pnsShowSnackbar(snackbarHostState, err, longDuration = true)
                        } else {
                            captureScope.pnsShowSnackbar(
                                snackbarHostState,
                                "Custom WB from center chroma (preview shader + AWB off).",
                                longDuration = false,
                            )
                        }
                    }
                },
                focalMapCalibratingHint = focalMapCalibratingHint,
                capturePipelineHint = capturePipelineHint,
                lastStillPostReadout = lastStillPostReadout,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .clip(RectangleShape),
            )
            PreviewChromeSectionDivider()
            PreviewRightRail(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .weight(PreviewChromeRailFlexWeight)
                        .clip(RectangleShape),
                uiRotationDeg = uiRotationDeg,
                cameraIds = cameraIds,
                onApplyFocalMmSlot = onApplyFocalMmSlot,
                onOpenDeveloperMenu = onOpenDeveloperMenu,
                fpsOptions = fpsOptions,
                selectedFps = selectedFps,
                onSetFps = onSetFps,
                hudState = hudState,
                compositionGuide = compositionGuide,
                chromePrefs = chromePrefs,
                onPickFirstCamera = onPickFirstCamera,
                onSwitchToFrontCamera = onSwitchToFrontCamera,
                onSwitchToRearCamera = onSwitchToRearCamera,
                selectedCameraId = selectedCameraId,
                focalCrop = focalCrop,
                onCaptureDng = { triggerStillCapture() },
                onBracketBurst = onBracketBurst,
                canCaptureRawStill = controller.canCaptureStill() && !afShutterGateActiveForUi,
                canCaptureBracketBurst = controller.canCaptureBracketBurst(),
                commandDialMode = commandDialMode,
                onCalibrateFromPreviewFrame = { openCalibrateFromPreviewFrame() },
                previewJpegCompanion = previewJpegCompanion,
                rawStillNotReadyReason = controller.rawStillNotReadyReason(),
                fineLocationGranted = fineLocationGranted,
                onPendingEnableGeotagChange = onPendingEnableGeotagChange,
                onRequestLocationForGeotag = onRequestLocationForGeotag,
                fpsTargetEditable = fpsTargetEditable,
                onKickPreviewPipeline = { controller.kickPreviewPipelineRestart() },
            )
            val showBottomTray =
                chrome.showOnScreenShutter || lastGalleryUri != null || settings.showCommandDial
            if (showBottomTray) {
                PreviewChromeSectionDivider()
                PreviewBottomCaptureTray(
                    lastGalleryUri = lastGalleryUri,
                    onExternalGalleryViewerLaunched = onExternalGalleryViewerLaunched,
                    showOnScreenShutter = chrome.showOnScreenShutter,
                    canCaptureRawStill = controller.canCaptureStill() && !afShutterGateActiveForUi,
                    onCaptureDng = { triggerStillCapture() },
                    isRecording = isRecording,
                    onRecordingChange = onRecordingChange,
                    onSetFps = onSetFps,
                    selectedCameraId = selectedCameraId,
                    primaryPhoto = primaryPhoto,
                    onPrimaryPhotoChange = onPrimaryPhotoChange,
                    selectedFps = selectedFps,
                    shootingModesSlot =
                        if (settings.showCommandDial) {
                            {
                                var modeMenuExpanded by remember { mutableStateOf(false) }
                                Box(contentAlignment = Alignment.Center) {
                                    FloatingActionButton(
                                        onClick = { modeMenuExpanded = true },
                                        modifier =
                                            Modifier
                                                .size(52.dp)
                                                .border(
                                                    2.dp,
                                                    Color.White.copy(alpha = 0.88f),
                                                    CircleShape,
                                                ).semantics {
                                                    contentDescription =
                                                        "Shooting mode ${commandDialMode.label}. Opens menu: Auto, Manual, Highlight, Snap, Bracket."
                                                },
                                        containerColor = PnsColors.PhotoOrange.copy(alpha = 0.92f),
                                        contentColor = Color.Black,
                                        shape = CircleShape,
                                    ) {
                                        Text(
                                            text = commandDialMode.label,
                                            style = MaterialTheme.typography.titleMedium,
                                            fontSize =
                                                if (commandDialMode == CommandDialMode.BKT) {
                                                    11.sp
                                                } else {
                                                    17.sp
                                                },
                                            maxLines = 1,
                                        )
                                    }
                                    DropdownMenu(
                                        expanded = modeMenuExpanded,
                                        onDismissRequest = { modeMenuExpanded = false },
                                        modifier = Modifier.widthIn(min = 288.dp),
                                    ) {
                                        Text(
                                            text = "Shooting mode",
                                            modifier =
                                                Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                                            style = MaterialTheme.typography.titleSmall,
                                            color = MaterialTheme.colorScheme.onSurface,
                                        )
                                        HorizontalDivider()
                                        val dialModes =
                                            CaptureMediaFamily.commandDialModesFor(
                                                CaptureMediaFamily.fromPrimaryPhoto(primaryPhoto),
                                            )
                                        dialModes.forEach { mode ->
                                            DropdownMenuItem(
                                                text = {
                                                    Text("${mode.label} Î“Ã‡Ã¶ ${mode.description}")
                                                },
                                                leadingIcon = {
                                                    Box(
                                                        modifier =
                                                            Modifier
                                                                .width(28.dp)
                                                                .height(24.dp),
                                                        contentAlignment = Alignment.Center,
                                                    ) {
                                                        if (mode == commandDialMode) {
                                                            Icon(
                                                                imageVector = Icons.Outlined.Check,
                                                                contentDescription = null,
                                                                tint = PnsColors.PhotoOrange,
                                                                modifier = Modifier.size(20.dp),
                                                            )
                                                        }
                                                    }
                                                },
                                                onClick = {
                                                    commandDialMode = mode
                                                    HudSettings.saveCommandDialMode(context, mode)
                                                    modeMenuExpanded = false
                                                    Log.i(
                                                        "PNS.ChromeUx",
                                                        "modeDialPopout=menuSelect mode=${mode.name}",
                                                    )
                                                },
                                            )
                                        }
                                    }
                                }
                            }
                        } else {
                            null
                        },
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .clip(RectangleShape),
                )
            }
        }
        if (calibrateOverlayActive) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.surface,
            ) {
                CalibrateScreen(
                    initialChartBitmap = calibratePendingInitialBitmap,
                    onInitialChartBitmapConsumed = { calibratePendingInitialBitmap = null },
                    onBack = { calibrateOverlayActive = false },
                )
            }
        }
    }
private class PreviewHostSlot {
                !liveChartCornerOverlay
            if (knownBuf) {
                    manualFocusDragEnabled = commandDialMode == CommandDialMode.M && knownBuf,
                    onManualFocusDragPixels = { controller.nudgeManualFocusFromDrag(it) },
                                    FocalLensStripSupport.focalSlotInteractionEnabled(
                                        appCtx,
                                        slot,
                                        cameraIds,
                                        selectedCameraId,
                                        digitalEqOk,
                                    )
        PreviewRailSettingToggle(
            title = "Corner test chart overlay",
            subtitle = "Small alignment grid overlay for display checks.",
            checked = chrome.liveChartCornerOverlay,
            onCheckedChange = { chromePrefs.update(chrome.copy(liveChartCornerOverlay = it)) },
        )
    manualFocusDragEnabled: Boolean = false,
    onManualFocusDragPixels: (Float) -> Unit = {},
    val cameraSwipeActive = !isRecording && !liveChartCornerOverlay
                swipeEnabled = true,
    Box(
        modifier =
            modifier
                .then(finderSemantics)
                .then(pointerModifier),
    ) {
private class PreviewController(
    /** Null = automatic AE for sensitivity / exposure time; non-null forces manual sensor row (AE off). */
    /** Manual focus distance (diopters) when [commandDialMode] is [CommandDialMode.M]; null otherwise. */
        if (mode != CommandDialMode.M) {
        if (commandDialMode != CommandDialMode.M) return
    fun peekManualFocusActive(): Boolean =
        commandDialMode == CommandDialMode.M && manualFocusDiopters != null
            isoChoices = ReadoutExposureCatalog.isoChoices(chars),
        manualIsoOverride = iso
        refreshRepeatingPreviewOnly()
    /** Adjust ISO only; keeps the current manual shutter selection (or auto). */
        manualIsoOverride = iso
        refreshRepeatingPreviewOnly()
    /** Adjust shutter only; keeps the current manual ISO selection (or auto). */
        refreshRepeatingPreviewOnly()
        val manualSensor = manualIsoOverride != null || manualExposureNsOverride != null
        val fps =
            if (targetFps >= 120) {
                targetFps.coerceIn(15, 240)
            } else {
                targetFps.coerceIn(15, VideoRecordingController.IN_APP_VIDEO_PREVIEW_CAP_FPS)
            }
        val formats =
            VideoFormatPresets.getAvailableFormats(
                resolution = size,
                fps = fps,
        val picked =
            when {
                wantDcg ->
                    formats.firstOrNull { it.isDcg }
                        ?: formats.firstOrNull { it.isTenBit }
                        ?: formats.first()
                adbAutomationVideoTenBit ->
                    formats.firstOrNull { it.isTenBit && !it.isDcg }
                        ?: formats.first()
                else -> formats.first()
            }
        Log.i(
            tag,
            "inAppVideoFormat label=${picked.getLabel()} dcg=${picked.isDcg} tenBit=${picked.isTenBit} " +
                "fps=${picked.frameRate} wantDcg=$wantDcg adbDcg=$adbAutomationVideoDcg",
        )
        if (stillsLut != LutCatalog.None) {
            StillRgbLut.applyToRgb888InPlace(rgb, w, h, stillsLut.load(BuiltInLuts.DEFAULT_SIZE))
        }
        val manualSensorStill = manualIsoOverride != null || manualExposureNsOverride != null
                applyReadoutManualExposureAndWb(this, chars, camId)
                if (!proShotPureLeafStill) {
                RawStillProcessingHints.applyProShotPreviewExposureFromResult(
                    this,
                    chars,
                    camId,
                    proShotPreviewResult,
                    latchManualExposureFromPreview = latchProShotManualExposure,
                    exposureLatch = proShotExposureLatch,
                )
        val manualSensorStill = manualIsoOverride != null || manualExposureNsOverride != null
                applyReadoutManualExposureAndWb(this, chars, camId)
                RawStillProcessingHints.applyProShotPreviewExposureFromResult(
                    this,
                    chars,
                    camId,
                    lastPreviewTotalCaptureResult,
                )
                if (commandDialMode == CommandDialMode.H && !manualSensorStill && adbValidationShotLabel == null) {
        val manualSensorBracket = manualIsoOverride != null || manualExposureNsOverride != null
                applyReadoutManualExposureAndWb(this, chars, camId)
                RawStillProcessingHints.applyProShotPreviewExposureFromResult(
                    this,
                    chars,
                    camId,
                    lastPreviewTotalCaptureResult,
                )
        if (!superMacroAdbProbe) return false
                    (smileStillEnabled && !automationSuppressFacePipeline)
                val manualSensor = manualIsoOverride != null || manualExposureNsOverride != null
                applySuperMacroVendorProbe(this, chars, camId)
                    manualIsoOverride != null || manualExposureNsOverride != null,
        val wantsManualSensor = manualIsoOverride != null || manualExposureNsOverride != null
        if (wantsManualSensor) {
                Log.w(tag, "Readout manual ISO/shutter unavailable: no CONTROL_AE_MODE_OFF (AWB still applied)")
                val isoPick = manualIsoOverride ?: previewMetadata.get().iso ?: isoRange?.lower ?: 100
                val isoClamped = ReadoutExposureCatalog.clampIso(isoRange, isoPick)
                req.set(CaptureRequest.SENSOR_SENSITIVITY, isoClamped)
                val expPick =
                    manualExposureNsOverride
                        ?: previewMetadata.get().exposureNs
                        ?: expRange?.lower
                        ?: 33_333_333L
                val expClamped = ReadoutExposureCatalog.clampExposure(expRange, expPick)
                req.set(CaptureRequest.SENSOR_EXPOSURE_TIME, expClamped)
     * Sprint 5.3: when ADB passes [superMacroAdbProbe] and preview targets ultra-wide, set OPLUS
     * close-up enable on the repeating request if the key is advertised Î“Ã‡Ã¶ proof for â”¬Âº5 / Super Macro gate.
    private fun applySuperMacroVendorProbe(
        if (!superMacroAdbProbe) return
            if (!loggedSuperMacroProbeWrongCam) {
        if (superMacroSessionConfigured) return
        if (loggedSuperMacroProbeUw) return
        loggedSuperMacroProbeUw = true
        val lookup = VendorKeyGuard.captureRequestKey(chars, macroName)
        val reqAvail = VendorKeyGuard.isRequestKeyAvailable(chars, macroName)
        val sessAvail = VendorKeyGuard.isSessionKeyAvailable(chars, macroName)
        PnsAdbLog.i(
            appContext,
            "superMacroCloseup keyLookup requestKeyObject=${lookup != null} requestEnum=$reqAvail sessionEnum=$sessAvail",
        )
        PnsAdbLog.i(
            appContext,
            "superMacroCloseup probe cameraId=$camId vendorKeyApplied=${appliedKind != null} type=${appliedKind ?: "none"}",
        )
            CommandDialMode.M, CommandDialMode.S,
        if (!wantHighlight && !wantHist && !wantFace && !wantZebra) {
        val histGapMs =
        val histOk =
            (!wantHighlight && !wantHist && !wantZebra) ||
                (now - lastHighlightProcessWallMs >= histGapMs)
        if (!histOk && !faceOk) {
        if (histOk && (wantHighlight || wantHist || wantZebra)) {
                    if (!wantHighlight && !wantHist && !wantZebra) return
                        if ((wantHist || wantHighlight) && w > 0 && h > 0) {
                    if (wantHighlight && hist != null) {
                    if (wantHist && hist != null) {
                    if (!wantHighlight && !wantHist) return@execute
        if (tap == null && faceMeter == null && commandDialMode == CommandDialMode.S) {
        }
        if (tap == null && faceMeter == null && commandDialMode == CommandDialMode.M) {
            if (faceMeter == null) {
                applyAutoProgramAf(req, chars)
            applyAutoProgramAeOn(req, chars)

