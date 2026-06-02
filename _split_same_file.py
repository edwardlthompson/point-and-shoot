from pathlib import Path

p = Path(r"c:\Users\edwar\AndroidStudioProjects\point-and-shoot\app\src\main\java\dev\pointandshoot\PreviewEngineScreen.kt")
lines = p.read_text(encoding="utf-8").splitlines()

idx_fun = next(i for i, l in enumerate(lines) if l.startswith("private fun PreviewEngineContent("))
idx_params_end = next(i for i, l in enumerate(lines) if i > idx_fun and l.strip() == ") {")
idx_shell = next(i for i, l in enumerate(lines) if "// Preview tile: **3:4** width:height (4:3 sensor upright" in l)
idx_tail = next(i for i, l in enumerate(lines) if l.startswith("private tailrec fun Context.findHostActivity"))

setup = lines[idx_params_end + 1 : idx_shell]
shell = lines[idx_shell:idx_tail - 1]

param_lines = lines[idx_fun + 1 : idx_params_end]
param_names = []
for ln in param_lines:
    s = ln.strip().rstrip(",")
    if not s or s.startswith("//") or s.startswith("/**") or s.startswith("*"):
        continue
    param_names.append(s.split(":")[0].split("=")[0].strip())

# Insert two composables before PreviewEngineContent
insert_at = idx_fun
new_funcs = [
    "",
    "@Composable",
    "private fun PreviewEngineChromeColumn(",
] + param_lines + [
    ") {",
] + shell + [
    "}",
    "",
    "@Composable",
    "private fun PreviewEngineContent(",
] + param_lines + [
    ") {",
] + setup + [
    "    PreviewEngineChromeColumn(",
] + [f"        {n} = {n}," for n in param_names] + [
    "    )",
    "}",
]

out = lines[:insert_at] + new_funcs + lines[idx_tail:]
p.write_text("\n".join(out) + "\n", encoding="utf-8")
print("setup", len(setup), "shell", len(shell))
