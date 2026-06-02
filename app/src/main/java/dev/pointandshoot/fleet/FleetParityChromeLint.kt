package dev.pointandshoot.fleet

/**
 * Ensures parity closure plans never suggest preview chrome layout changes (Milestone **21.13f**).
 */
object FleetParityChromeLint {
    private val forbiddenPhrases =
        listOf(
            "flex weight",
            "PreviewChromeFinderFlexWeight",
            "PreviewChromeRailFlexWeight",
            "aspectRatio(3/4)",
            "aspectRatio(3f / 4f)",
            "grid slot",
            "previewChromeGridSlots",
            "IconCubeVectorButton spacing",
            "reorder focal row tiles",
            "change tray layout",
        )

    fun containsForbiddenLayoutSuggestion(text: String): Boolean =
        forbiddenPhrases.any { phrase -> text.contains(phrase, ignoreCase = true) }

    fun assertClosurePlanSafe(text: String) {
        check(!containsForbiddenLayoutSuggestion(text)) {
            "Closure plan violates preview-chrome-ui-lock: contains forbidden layout suggestion"
        }
    }
}
