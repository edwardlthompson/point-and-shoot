package dev.pointandshoot

import android.content.Context
import java.io.File

/** Catalog + imported `.cube` + negative invert as one look resolver. */
object PnsActiveLook {
    const val IMPORTED_PREFIX: String = PnsProductPrefs.PREFIX_IMPORTED_LUT

    fun resolveLut3d(context: Context, catalogName: String, cameraId: String?): Lut3D {
        val perLens = cameraId?.let { PnsProductPrefs.lookForLens(context, it) }
        val imported = PnsProductPrefs.selectedImportedLut(context)
        val name = perLens?.takeIf { it.isNotBlank() } ?: imported ?: catalogName
        if (name.startsWith(IMPORTED_PREFIX)) {
            val file = File(ImportedLutStore.directory(context) ?: return LutCatalog.None.load(), name.removePrefix(IMPORTED_PREFIX))
            return loadImported(file) ?: LutCatalog.None.load()
        }
        if (name.endsWith(".cube", ignoreCase = true)) {
            val file = ImportedLutStore.list(context).firstOrNull { it.name == name }
            if (file != null) return loadImported(file) ?: LutCatalog.None.load()
        }
        return LutCatalog.entries.firstOrNull { it.name == name }?.load() ?: LutCatalog.None.load()
    }

    fun loadImported(file: File): Lut3D? {
        if (!file.isFile) return null
        return runCatching { LutPipeline.parseCube(file.readText()) }.getOrNull()
    }

    fun pickerRows(context: Context): List<Pair<String, String>> {
        val rows = mutableListOf<Pair<String, String>>()
        LutCatalog.entries.forEach { rows += it.name to it.displayName }
        ImportedLutStore.list(context).forEach { file ->
            rows += (IMPORTED_PREFIX + file.name) to "Imported · ${file.name}"
        }
        return rows
    }
}
