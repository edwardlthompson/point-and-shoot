import re
from pathlib import Path

p = Path(r"c:\Users\edwar\AndroidStudioProjects\point-and-shoot\app\src\main\java\dev\pointandshoot\PreviewEngineScreen.kt")
lines = p.read_text(encoding="utf-8").splitlines()

idx_fun = next(i for i, l in enumerate(lines) if l.startswith("private fun PreviewEngineContent("))
idx_params_end = next(i for i, l in enumerate(lines) if i > idx_fun and l.strip() == ") {")
idx_shell = next(i for i, l in enumerate(lines) if "// Preview tile: **3:4** width:height (4:3 sensor upright" in l)
idx_tail = next(i for i, l in enumerate(lines) if l.startswith("private tailrec fun Context.findHostActivity"))

param_lines = lines[idx_fun + 1 : idx_params_end]
param_names = []
for ln in param_lines:
    s = ln.strip().rstrip(",")
    if not s or s.startswith("//") or s.startswith("/**") or s.startswith("*"):
        continue
    param_names.append(s.split(":")[0].split("=")[0].strip())

setup = lines[idx_params_end + 1 : idx_shell]
shell = lines[idx_shell : idx_tail - 1]

local_names = []
for ln in setup:
    m = re.match(r"\s+(?:val|var)\s+(\w+)", ln)
    if m:
        local_names.append(m.group(1))
for fn in (
    "triggerStillCapture",
    "exitChartCalibrationMode",
    "openCalibrateFromPreviewFrame",
    "autoDetectChartCornersFromPreview",
    "applyLiveChartCalibrationNow",
    "onCalibrationProfileSaved",
    "onApplyFocalMmSlotGuarded",
):
    if fn not in local_names:
        local_names.append(fn)

# modifier block is between setup end and shell - actually in setup before shell
# previewChromeModifier is in setup

column_sig = (
    ["@Composable", "private fun PreviewEngineChromeColumn("]
    + param_lines
    + [f"    {n}: " + infer_type(n) for n in local_names if n not in param_names]
)
# infer_type is hard - use simpler: pass as parameters with same names from outer scope via lambda wrapper

# Simpler approach: column takes no extra sig - use closure by making shell an inline lambda in content
# Actually use @Composable lambda:

new_block = (
    lines[:idx_fun]
    + ["@Composable", "private fun PreviewEngineContent("]
    + param_lines
    + [") {"]
    + setup
    + ["    PreviewEngineChromeColumnShell()"]
    + ["}"]
    + ["", "@Composable", "private fun PreviewEngineChromeColumnShell() {}"]
    + lines[idx_tail:]
)

def infer_type(name: str) -> str:
    return "Any"

p.write_text("\n".join(lines[:idx_fun]) + "\n", encoding="utf-8")
