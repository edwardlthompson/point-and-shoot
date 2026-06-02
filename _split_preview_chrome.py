from pathlib import Path

root = Path(r"c:\Users\edwar\AndroidStudioProjects\point-and-shoot\app\src\main\java\dev\pointandshoot")
src = root / "PreviewEngineScreen.kt"
lines = src.read_text(encoding="utf-8").splitlines()

idx_content_start = next(i for i, l in enumerate(lines) if l.startswith("@Composable") and i + 1 < len(lines) and "PreviewEngineContent(" in lines[i + 1])
idx_params_end = next(i for i, l in enumerate(lines) if i > idx_content_start and l.strip() == ") {")
idx_setup_start = idx_params_end + 1
idx_setup_end = next(i for i, l in enumerate(lines) if l.strip() == "val layoutDirection = LocalLayoutDirection.current")
idx_shell_end = next(
    i
    for i, l in enumerate(lines)
    if i > idx_setup_end and l.strip() == "}" and i + 1 < len(lines) and lines[i + 1].strip() == "}"
)

setup_block = lines[idx_setup_start:idx_setup_end]
shell_block = lines[idx_setup_end : idx_shell_end + 1]

param_names = []
for ln in lines[idx_content_start + 1 : idx_params_end]:
    s = ln.strip()
    if not s or s.startswith("//") or s.startswith("/**") or s.startswith("*") or s.startswith("@"):
        continue
    if s.endswith(","):
        s = s[:-1]
    name = s.split(":")[0].split("=")[0].strip()
    if name:
        param_names.append(name)

header = """package dev.pointandshoot

import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import android.util.Size
import android.view.KeyEvent as AndroidKeyEvent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RectangleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

"""

host_fn = ["@Composable", "internal fun PreviewEngineChromeHost("]
host_fn.extend(lines[idx_content_start + 1 : idx_params_end])
host_fn.append(") {")
host_fn.extend(setup_block)
host_fn.extend(shell_block)
host_fn.append("}")

(root / "PreviewEngineChromeLayout.kt").write_text(header + "\n".join(host_fn) + "\n", encoding="utf-8")

new_content = lines[:idx_setup_start]
new_content.append("    PreviewEngineChromeHost(")
for n in param_names:
    new_content.append(f"        {n} = {n},")
new_content.append("    )")
new_content.append("}")
new_content.extend(lines[idx_shell_end + 1 :])

text = "\n".join(new_content)
text = text.replace("private class PreviewHostSlot", "internal class PreviewHostSlot")
text = text.replace("private class PreviewController(", "internal class PreviewController(")
src.write_text(text, encoding="utf-8")
print("params", len(param_names), "host lines", len(host_fn), "screen lines", len(new_content))
