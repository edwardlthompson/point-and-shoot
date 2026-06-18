package dev.pointandshoot

/**
 * Still DNG encode / IQ strategy (Milestone **13.3g**).
 * Lives in `:pns-core` so `:pns-capture` does not depend on `:pns-fleet`.
 */
enum class StillDngBackend {
    FRAMEWORK_REFERENCEAPP,
    ALTREFERENCEAPP_INSPIRED,
    ALTREFERENCEAPP_NATIVE,
}
