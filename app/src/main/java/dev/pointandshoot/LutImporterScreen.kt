package dev.pointandshoot

import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import java.io.File

/**
 * SAF "Import LUT\u2026" picker per BUILD_PLAN \u00a77 ("User-imported LUTs land
 * in `getExternalFilesDir(null)/luts/imported/`; SAF "Import LUT\u2026" picker
 * reads the user's `.cube` file, validates it (size + grid spacing + value
 * range), and copies it in. Invalid files are rejected with a toast").
 *
 * Flow:
 *   1. User taps "Pick `.cube` file\u2026" \u2192 SAF OpenDocument with the
 *      wildcard mime filter (we cannot rely on a `.cube` mime being
 *      registered system-wide so we accept anything and validate after read).
 *   2. We read the file body via `ContentResolver.openInputStream`.
 *   3. [LutImportValidator] inspects the bytes; on failure we toast the
 *      `Failure.toastMessage()` and remain on this screen.
 *   4. On success we pass the bytes to [ImportedLutStore.save] which writes
 *      `<safeName>.cube` plus a `<safeName>.cube.sha256.txt` sidecar; the
 *      computed grid size + SHA-256 are surfaced inline so the user knows
 *      the import succeeded.
 *
 * The screen also lists every previously-imported LUT under the imported-
 * LUTs directory so the user can verify what's already on disk.
 */
@Composable
fun LutImporterScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var importedFiles by remember { mutableStateOf<List<File>>(emptyList()) }
    var lastResult by remember { mutableStateOf<String?>(null) }
    var lastResultIsError by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        importedFiles = ImportedLutStore.list(context)
    }

    val pickLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? ->
        if (uri == null) {
            // User cancelled - no toast needed.
            return@rememberLauncherForActivityResult
        }
        val (displayName, bytes) = readUri(context, uri) ?: run {
            lastResult = "Could not read picked file."
            lastResultIsError = true
            return@rememberLauncherForActivityResult
        }
        when (val outcome = LutImportValidator.validate(bytes)) {
            is LutImportValidator.Result.Failure -> {
                lastResult = outcome.toastMessage()
                lastResultIsError = true
            }
            is LutImportValidator.Result.Success -> {
                val saved = try {
                    ImportedLutStore.save(context, displayName, bytes)
                } catch (ex: java.io.IOException) {
                    lastResult = "Save failed: ${ex.message}"
                    lastResultIsError = true
                    return@rememberLauncherForActivityResult
                }
                if (saved == null) {
                    lastResult = "External storage unavailable."
                    lastResultIsError = true
                    return@rememberLauncherForActivityResult
                }
                importedFiles = ImportedLutStore.list(context)
                val msg = "Imported ${saved.file.name} (size=${outcome.lut.size}\u00b3, sha256=${saved.sha256.take(12)}\u2026)"
                lastResult = msg
                lastResultIsError = false
            }
        }
    }

    val insets = rememberSystemInsetsDp()
    val padding: PaddingValues = insets.asPaddingValues(extra = 16.dp)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        OutlinedButton(onClick = onBack) { Text("Back") }

        Text("Import LUT", style = MaterialTheme.typography.titleLarge)
        Text(
            text = "Adobe `.cube` 3D LUTs at 17/33/65 grid sizes. The file is validated " +
                "(size, [0,1] domain, sample values) before being copied to app-private storage.",
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White.copy(alpha = 0.75f),
        )

        Button(onClick = { pickLauncher.launch(arrayOf("*/*")) }) {
            Text("Pick `.cube` file\u2026")
        }

        val resultText = lastResult
        if (resultText != null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(
                        if (lastResultIsError) PnsColors.RecordRed.copy(alpha = 0.15f)
                        else PnsColors.PhotoOrange.copy(alpha = 0.15f),
                    )
                    .padding(12.dp),
            ) {
                Text(
                    text = resultText,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = Color.White.copy(alpha = 0.95f),
                )
            }
        }

        ImportedLutsList(files = importedFiles)
    }
}

@Composable
private fun ImportedLutsList(files: List<File>) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "Imported LUTs (newest first)",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(top = 8.dp),
        )
        if (files.isEmpty()) {
            Text(
                text = "(no imports yet)",
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.6f),
            )
        }
        for (file in files) {
            ImportedLutRow(file = file)
        }
    }
}

@Composable
private fun ImportedLutRow(file: File) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(Color.White.copy(alpha = 0.04f))
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = file.name,
            style = MaterialTheme.typography.bodyMedium,
            fontFamily = FontFamily.Monospace,
        )
        Text(
            text = "${file.length()} bytes",
            style = MaterialTheme.typography.labelSmall,
            color = Color.White.copy(alpha = 0.55f),
        )
    }
}

/** Read the SAF Uri body + the user-visible filename. Returns null on any IO failure. */
private fun readUri(context: android.content.Context, uri: Uri): Pair<String, ByteArray>? {
    return try {
        val resolver = context.contentResolver
        val displayName: String = resolver.query(uri, null, null, null, null)?.use { cursor ->
            val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (idx >= 0 && cursor.moveToFirst()) cursor.getString(idx) else uri.lastPathSegment ?: "imported.cube"
        } ?: (uri.lastPathSegment ?: "imported.cube")
        val bytes = resolver.openInputStream(uri)?.use { it.readBytes() } ?: return null
        displayName to bytes
    } catch (ex: SecurityException) {
        null
    } catch (ex: java.io.IOException) {
        null
    }
}
