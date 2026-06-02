from pathlib import Path

root = Path(r"c:\Users\edwar\AndroidStudioProjects\point-and-shoot\app\src\main\java\dev\pointandshoot")
screen = (root / "PreviewEngineScreen.kt").read_text(encoding="utf-8").splitlines()
layout = (root / "PreviewEngineChromeLayout.kt").read_text(encoding="utf-8").splitlines()
recover = Path(r"c:\Users\edwar\AndroidStudioProjects\point-and-shoot\_recover_removed.kt").read_text(encoding="utf-8").splitlines()

idx_content = next(i for i, l in enumerate(screen) if l.startswith("private fun PreviewEngineContent(")) - 1
idx_content_end = next(i for i, l in enumerate(screen) if i > idx_content and l.startswith("private tailrec fun Context.findHostActivity"))

idx_host_body = next(i for i, l in enumerate(layout) if l.strip() == ") {" and i > 60)
idx_recover_shell = next(i for i, l in enumerate(recover) if l.strip() == "val layoutDirection = LocalLayoutDirection.current")
idx_recover_shell_end = next(i for i, l in enumerate(recover) if i > idx_recover_shell and l.strip() == "}" and i + 1 < len(recover) and recover[i + 1].strip() == "}")

body = layout[idx_host_body + 1 : next(i for i, l in enumerate(layout) if l.strip() == "val layoutDirection = LocalLayoutDirection.current")]
shell = recover[idx_recover_shell : idx_recover_shell_end + 1]

merged = body + shell
text = "\n".join(merged)

# Patches for latest focus / macro / readout wiring
text = text.replace(
    "tapPreviewToCapture = chrome.tapPreviewToCapture,",
    "tapPreviewToCapture = effectiveTapPreviewToCapture,\n"
    "                            manualFocusRackEnabled = manualFocusUi.rackActive,\n"
    "                            manualFocusRackDiopters = manualFocusUi.rackDiopters,\n"
    "                            manualFocusRackMaxDiopters = manualFocusUi.rackMaxDiopters,\n"
    "                            onManualFocusRackDiopters = manualFocusUi.onRackDiopters,\n"
    "                            macroLocksCameraSwipe = macroLensLocked,",
)
text = text.replace(
    "onApplyFocalMmSlot = onApplyFocalMmSlot,",
    "onApplyFocalMmSlot = onApplyFocalMmSlotGuarded,\n"
    "                macroLensLocked = macroLensLocked,",
    1,
)
text = text.replace(
    """                modifier =
                    Modifier
                        .fillMaxWidth()
                        .clip(RectangleShape),
            )
            PreviewChromeSectionDivider()
            PreviewRightRail(""",
    """                focusChipValue = focusChipValue,
                onFocusChipClick = { focusModePickerOpen = true },
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .clip(RectangleShape),
            )
            if (showVideoFormatPicker) {
                InAppVideoFormatPickerDialog(
                    catalog = videoFormatCatalog,
                    selected = selectedInAppVideoFormat,
                    onPick = { format ->
                        chromePrefs.update(
                            chrome.copy(
                                inAppVideoEncodeWidth = format.resolution.width,
                                inAppVideoEncodeHeight = format.resolution.height,
                            ),
                        )
                        if (format.frameRate != selectedFps) {
                            onSetFps(format.frameRate)
                        }
                        showVideoFormatPicker = false
                    },
                    onDismiss = { showVideoFormatPicker = false },
                )
            }
            if (focusModePickerOpen) {
                PreviewFocusModePickerDialog(
                    onDismiss = { focusModePickerOpen = false },
                    menuSelections = controller.previewFocusMenuSelections(),
                    current = previewFocusSelection,
                    onPick = { pick ->
                        commandDialMode =
                            macroFocusDialCoupling.applyFocusPick(
                                pick = pick,
                                currentDial = commandDialMode,
                                setFocus = { controller.setPreviewFocusSelection(it) },
                            )
                        HudSettings.saveCommandDialMode(context, commandDialMode)
                    },
                )
            }
            PreviewChromeSectionDivider()
            PreviewRightRail(""",
    1,
)
text = text.replace(
    """                onKickPreviewPipeline = { controller.kickPreviewPipelineRestart() },
            )""",
    """                onKickPreviewPipeline = { controller.kickPreviewPipelineRestart() },
                onOpenFocusModePicker = { focusModePickerOpen = true },
            )""",
    1,
)
text = text.replace(
    """                                                onClick = {
                                                    commandDialMode = mode
                                                    HudSettings.saveCommandDialMode(context, mode)
                                                    modeMenuExpanded = false""",
    """                                                onClick = {
                                                    commandDialMode =
                                                        macroFocusDialCoupling.applyDialChange(
                                                            previousDial = commandDialMode,
                                                            newDial = mode,
                                                            currentFocus = previewFocusSelection,
                                                            menuSelections =
                                                                controller.previewFocusMenuSelections(),
                                                            setFocus = {
                                                                controller.setPreviewFocusSelection(it)
                                                            },
                                                        )
                                                    HudSettings.saveCommandDialMode(context, commandDialMode)
                                                    modeMenuExpanded = false""",
    1,
)
# Use PreviewCommandDialDropdownMenu if recover has DropdownMenu - check
if "PreviewCommandDialDropdownMenu" not in text and "DropdownMenu(" in text:
    pass  # keep old dropdown for now

new_screen = screen[: idx_content + 2]  # through ") {"
new_screen.append(text)
new_screen.append("}")
new_screen.extend(screen[idx_content_end :])

out = "\n".join(new_screen) + "\n"
out = out.replace("internal class PreviewHostSlot", "private class PreviewHostSlot")
out = out.replace("internal class PreviewController(", "private class PreviewController(")
(root / "PreviewEngineScreen.kt").write_text(out, encoding="utf-8")
(root / "PreviewEngineChromeLayout.kt").unlink(missing_ok=True)
print("restored PreviewEngineContent,", len(text.splitlines()), "body lines")
