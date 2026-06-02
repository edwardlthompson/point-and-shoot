from pathlib import Path

root = Path(r"c:\Users\edwar\AndroidStudioProjects\point-and-shoot\app\src\main\java\dev\pointandshoot")
layout = (root / "PreviewEngineChromeLayout.kt").read_text(encoding="utf-8").splitlines()
recover = Path(r"c:\Users\edwar\AndroidStudioProjects\point-and-shoot\_recover_removed.kt").read_text(encoding="utf-8").splitlines()

idx_layout_shell = next(i for i, l in enumerate(layout) if l.strip() == "val layoutDirection = LocalLayoutDirection.current")
idx_recover_shell = next(i for i, l in enumerate(recover) if l.strip() == "val layoutDirection = LocalLayoutDirection.current")

# Keep setup from layout (has focus features); shell from recover (complete)
new_layout = layout[:idx_layout_shell] + recover[idx_recover_shell:]

# Patch recover shell for our focus features - replace tap line and add manual focus params to PreviewMainViewport
text = "\n".join(new_layout) + "\n"
text = text.replace(
    "tapPreviewToCapture = chrome.tapPreviewToCapture,",
    "tapPreviewToCapture = effectiveTapPreviewToCapture,\n"
    "                            manualFocusRackEnabled = manualFocusUi.rackActive,\n"
    "                            manualFocusRackDiopters = manualFocusUi.rackDiopters,\n"
    "                            manualFocusRackMaxDiopters = manualFocusUi.rackMaxDiopters,\n"
    "                            onManualFocusRackDiopters = manualFocusUi.onRackDiopters,",
)
text = text.replace(
    "macroLocksCameraSwipe = macroLensLocked,",
    "macroLocksCameraSwipe = macroLensLocked,\n",
    1,
)

(root / "PreviewEngineChromeLayout.kt").write_text(text, encoding="utf-8")
print("layout lines", len(new_layout))
