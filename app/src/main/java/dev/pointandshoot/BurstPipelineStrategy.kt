package dev.pointandshoot

enum class BurstPipelineStrategy(
    val storageId: String,
) {
    Aggressive("aggressive"),
    Paced("paced"),
    ;

    companion object {
        fun parse(raw: String?): BurstPipelineStrategy? =
            entries.firstOrNull { it.storageId.equals(raw?.trim(), ignoreCase = true) }
    }
}
