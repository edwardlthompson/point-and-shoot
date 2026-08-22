@file:Suppress("MagicNumber")

package dev.pointandshoot

import android.content.Context
import java.io.File

/**
 * XMP beside a DNG — keywords / rating / crop intent.
 * Never opens or rewrites the DNG bytes.
 */
object DngXmpSidecar {
    fun write(context: Context, displayName: String, meta: GalleryLibrary.Meta): File? {
        if (!displayName.lowercase().endsWith(".dng")) return null
        val dir = context.getExternalFilesDir("xmp") ?: return null
        if (!dir.exists() && !dir.mkdirs()) return null
        val dest = File(dir, "${displayName.substringBeforeLast('.')}.xmp")
        dest.writeText(packet(displayName, meta), Charsets.UTF_8)
        return dest
    }

    fun packet(displayName: String, meta: GalleryLibrary.Meta): String {
        val keywords = meta.keywords.joinToString(",") { it }
        val rating = meta.rating.coerceIn(0, 5)
        return """
            <?xpacket begin="" id="W5M0MpCehiHzreSzNTczkc9d"?>
            <x:xmpmeta xmlns:x="adobe:ns:meta/">
              <rdf:RDF xmlns:rdf="http://www.w3.org/1999/02/22-rdf-syntax-ns#">
                <rdf:Description xmlns:xmp="http://ns.adobe.com/xap/1.0/"
                                 xmlns:dc="http://purl.org/dc/elements/1.1/"
                                 xmp:Rating="$rating">
                  <dc:title>$displayName</dc:title>
                  <dc:subject>$keywords</dc:subject>
                </rdf:Description>
              </rdf:RDF>
            </x:xmpmeta>
            <?xpacket end="w"?>
            """.trimIndent()
    }
}
