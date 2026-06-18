package dev.pointandshoot.preview.mock

/**
 * Canonical launch routes for T.14 unified mock/demo preview (ADR-0008).
 *
 * [ROUTE_MOCK] is the preferred ADB extra (`--es pns_screen mock`).
 * [ROUTE_PROHUD] and [ROUTE_GLPREVIEW] remain aliases for Milestone 6 automation.
 */
object MockPreviewScreens {
    const val ROUTE_MOCK = "mock"
    const val ROUTE_PROHUD = "prohud"
    const val ROUTE_GLPREVIEW = "glpreview"

    private val ALL_ROUTES = setOf(ROUTE_MOCK, ROUTE_PROHUD, ROUTE_GLPREVIEW)

    fun isMockRoute(screen: String?): Boolean = screen != null && screen in ALL_ROUTES

    /** Normalizes legacy aliases to the canonical mock route for logging. */
    fun normalizeRoute(screen: String?): String =
        when (screen) {
            ROUTE_PROHUD, ROUTE_GLPREVIEW, ROUTE_MOCK -> screen ?: ROUTE_MOCK
            else -> ROUTE_MOCK
        }
}
