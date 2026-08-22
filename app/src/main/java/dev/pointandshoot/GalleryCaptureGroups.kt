package dev.pointandshoot

/**
 * Treats DNG + processed stills (and BKT / HDR / NightScape stacks) as one capture.
 */
object GalleryCaptureGroups {
    enum class Kind { Pair, Stack, Single, Video }

    data class Group(
        val id: String,
        val kind: Kind,
        val items: List<MediaItem>,
    ) {
        val cover: MediaItem
            get() = items.firstOrNull { !it.isRaw && !it.isVideo } ?: items.first()

        val processed: List<MediaItem>
            get() = items.filter { !it.isRaw && !it.isVideo }

        val raws: List<MediaItem>
            get() = items.filter { it.isRaw }
    }

    enum class ShareFormat { Jpeg, Dng, Both }

    fun groupKey(displayName: String): String {
        val stem = displayName.substringBeforeLast('.').lowercase()
        Regex("""(bkt-[a-f0-9]{8,}|hdr-[a-f0-9]{8,})""").find(stem)?.let { return it.groupValues[1] }
        if (stem.contains("nightscape")) return "nightscape:${timestampPrefix(stem)}"
        if (stem.contains("maxphoto")) return "maxphoto:${timestampPrefix(stem)}"
        return "shot:${timestampPrefix(stem)}"
    }

    fun group(items: List<MediaItem>): List<Group> {
        val buckets = linkedMapOf<String, MutableList<MediaItem>>()
        items.forEach { item ->
            val key = if (item.isVideo) "video:${item.displayName.lowercase()}" else groupKey(item.displayName)
            buckets.getOrPut(key) { mutableListOf() }.add(item)
        }
        return buckets.map { (id, members) ->
            val kind =
                when {
                    members.any { it.isVideo } -> Kind.Video
                    members.size > 2 || id.startsWith("bkt-") || id.startsWith("hdr-") ||
                        id.startsWith("nightscape") -> Kind.Stack
                    members.any { it.isRaw } && members.any { !it.isRaw } -> Kind.Pair
                    else -> Kind.Single
                }
            Group(id, kind, members.sortedBy { it.displayName.lowercase() })
        }
    }

    fun urisForShare(group: Group, format: ShareFormat): List<android.net.Uri> =
        when (format) {
            ShareFormat.Jpeg ->
                group.processed.map { it.uri }.ifEmpty { group.items.filter { !it.isVideo }.map { it.uri } }
            ShareFormat.Dng ->
                group.raws.map { it.uri }.ifEmpty { group.items.filter { it.isRaw }.map { it.uri } }
            ShareFormat.Both -> group.items.map { it.uri }
        }

    fun describe(group: Group): String {
        val cover = group.cover
        val parts = mutableListOf<String>()
        parts +=
            when (group.kind) {
                Kind.Pair -> "RAW + processed pair"
                Kind.Stack -> "Stack of ${group.items.size} files"
                Kind.Video -> "Video"
                Kind.Single -> if (cover.isRaw) "RAW still" else "Processed still"
            }
        cover.lens?.let { parts += it }
        cover.focalLength?.let { parts += it }
        cover.iso?.let { parts += "ISO $it" }
        cover.shutterSpeed?.let { parts += it }
        cover.colorSpace?.let { parts += it }
        if (cover.hasLocation) parts += "has location"
        return parts.joinToString(" · ")
    }

    private fun timestampPrefix(stem: String): String {
        val match = Regex("""^pns_(\d{8}t\d{6}z)""").find(stem)
        return match?.groupValues?.get(1) ?: stem.substringBefore('_').ifBlank { stem }
    }
}
