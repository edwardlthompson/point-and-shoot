package dev.pointandshoot

/**
 * Canonical DCIM tree for every Point & Shoot MediaStore insert.
 *
 * **Writes** always use [RELATIVE_PATH] (`DCIM/PointAndShoot`, no spaces).
 * **Reads** also include [LEGACY_RELATIVE_PATH] (`DCIM/Point & Shoot`) so files
 * already on device stay visible in the gallery / library desk. Do not move or
 * delete the legacy folder automatically.
 *
 * MediaStore typically stores a trailing slash (`DCIM/PointAndShoot/`); LIKE
 * args from [relativePathLikeArgs] cover that.
 */
object PnsDcimPaths {
    const val FOLDER_NAME: String = "PointAndShoot"
    const val LEGACY_FOLDER_NAME: String = "Point & Shoot"

    /** MediaStore `RELATIVE_PATH` for new stills, video, sidecars, and gallery exports. */
    const val RELATIVE_PATH: String = "DCIM/$FOLDER_NAME"

    /** Pre-unification folder used by still/video MediaStore inserts (`Point & Shoot`). */
    const val LEGACY_RELATIVE_PATH: String = "DCIM/$LEGACY_FOLDER_NAME"

    fun readRelativePaths(): List<String> = listOf(RELATIVE_PATH, LEGACY_RELATIVE_PATH)

    fun canonicalRelativePathLikeArg(): String = "$RELATIVE_PATH%"

    fun relativePathLikeArgs(): Array<String> =
        readRelativePaths().map { "$it%" }.toTypedArray()

    /**
     * SQL fragment + bind args for a MediaStore `RELATIVE_PATH` filter that
     * matches the canonical folder and the legacy spaced folder.
     */
    fun relativePathSqlClause(columnName: String): Pair<String, Array<String>> {
        val args = relativePathLikeArgs()
        val sql =
            args.joinToString(separator = " OR ", prefix = "(", postfix = ")") { "$columnName LIKE ?" }
        return sql to args
    }

    fun matchesRelativePath(relativePath: String?): Boolean {
        val value = relativePath ?: return false
        return readRelativePaths().any { value.startsWith(it) }
    }
}
