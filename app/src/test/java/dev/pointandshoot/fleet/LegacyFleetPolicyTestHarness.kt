package dev.pointandshoot.fleet

import dev.pointandshoot.GenericLeafDngFleetPolicy
import dev.pointandshoot.LeafDngFleetPolicies
import org.junit.After
import org.junit.Before

/** Registers [LegacyFleetPolicy] for JVM tests that assert legacy SKU camera ids. */
abstract class LegacyFleetPolicyTestHarness {

    @Before
    fun registerLegacyFleetPolicy() {
        LeafDngFleetPolicies.active = LegacyFleetPolicy
    }

    @After
    fun resetFleetPolicy() {
        LeafDngFleetPolicies.active = GenericLeafDngFleetPolicy
    }
}
