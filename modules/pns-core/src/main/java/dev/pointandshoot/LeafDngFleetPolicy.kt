package dev.pointandshoot

/**
 * Fleet DNG / leaf RAW policy surface for `:pns-capture` without a Gradle dependency on `:pns-fleet`.
 * [dev.pointandshoot.fleet.LegacyFleetPolicy] registers at app startup via [LeafDngFleetPolicies].
 */
interface LeafDngFleetPolicy {
    val canonicalUw: String
    val canonicalWide: String
    val canonicalTele: String

    fun appliesToDevice(): Boolean
    fun stillDngBackend(): StillDngBackend
    fun leafRawFormatOrder(): List<Int>

    fun useReferenceAppPureDngSave(): Boolean
    fun useWideLeafCalibrationForAuxDng(): Boolean
    fun useLegacyLeafAuxColorReconcile(): Boolean
    fun useReferenceAppReferenceCalibration(): Boolean
    fun useLegacyAsnReconcileOnly(): Boolean
    fun useHalColorCalibrationReconcile(): Boolean

    fun useReferenceAppStillPrecapture(): Boolean
}

/** No-op generic policy when no legacy fleet plugin is registered. */
object GenericLeafDngFleetPolicy : LeafDngFleetPolicy {
    override val canonicalUw: String = "1"
    override val canonicalWide: String = "0"
    override val canonicalTele: String = "2"

    override fun appliesToDevice(): Boolean = false
    override fun stillDngBackend(): StillDngBackend = StillDngBackend.FRAMEWORK_REFERENCEAPP
    override fun leafRawFormatOrder(): List<Int> = emptyList()

    override fun useReferenceAppPureDngSave(): Boolean = false
    override fun useWideLeafCalibrationForAuxDng(): Boolean = false
    override fun useLegacyLeafAuxColorReconcile(): Boolean = false
    override fun useReferenceAppReferenceCalibration(): Boolean = false
    override fun useLegacyAsnReconcileOnly(): Boolean = false
    override fun useHalColorCalibrationReconcile(): Boolean = false

    override fun useReferenceAppStillPrecapture(): Boolean = false
}

object LeafDngFleetPolicies {
    @Volatile
    var active: LeafDngFleetPolicy = GenericLeafDngFleetPolicy
}
