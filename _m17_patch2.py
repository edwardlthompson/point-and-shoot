from pathlib import Path
p = Path("app/src/main/java/dev/pointandshoot/fleet/FleetDeviceMatrixStore.kt")
t = p.read_text(encoding="utf-8")
if "SUMMARY_FILE_NAME" not in t:
    t = t.replace(
        'const val MATRIX_FILE_NAME = "fleet_device_matrix.json"',
        'const val MATRIX_FILE_NAME = "fleet_device_matrix.json"\n    const val SUMMARY_FILE_NAME = "fleet_device_capability_summary.md"',
    )
    insert = """
    fun summaryFile(context: Context): File =
        File(context.applicationContext.filesDir, SUMMARY_FILE_NAME)

    /** Persist matrix JSON + human-readable summary markdown (Milestone **17.1**). */
    fun saveWithArtifacts(context: Context, root: JSONObject, rotatePreviousToHistory: Boolean = false) {
        val withCatalog =
            if (root.has(FleetDeviceMatrix.KEY_CAPABILITY_CATALOG)) {
                root
            } else {
                CameraCapabilityCatalogBuilder.attachTo(root)
            }
        save(context, withCatalog, rotatePreviousToHistory)
        summaryFile(context).writeText(FleetCapabilitySummaryMarkdown.render(withCatalog))
    }

"""
    t = t.replace(
        "    fun save(context: Context, root: JSONObject, rotatePreviousToHistory: Boolean = false) {",
        insert + "    fun save(context: Context, root: JSONObject, rotatePreviousToHistory: Boolean = false) {",
    )
    p.write_text(t, encoding="utf-8")
    print("FleetDeviceMatrixStore.kt updated")
