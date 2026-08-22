@file:Suppress("MagicNumber")

package dev.pointandshoot

/** Passwordless day page for a laptop on the same Wi‑Fi. */
object LanProofing {
    fun html(files: List<LanMediaTransferServer.FileEntry>): String {
        val rows =
            files.take(80).joinToString("") { f ->
                """<li><a href="/file?id=${f.id}">${escape(f.name)}</a> (${f.size})</li>"""
            }
        return """
            <!doctype html><html><head><meta charset="utf-8"><title>P&S proofing</title>
            <style>body{font-family:sans-serif;background:#111;color:#eee}a{color:#ff9800}</style>
            </head><body><h1>Point & Shoot proofing</h1><ol>$rows</ol></body></html>
            """.trimIndent()
    }

    private fun escape(s: String): String =
        s.replace("&", "&amp;").replace("<", "&lt;").replace("\"", "&quot;")
}
