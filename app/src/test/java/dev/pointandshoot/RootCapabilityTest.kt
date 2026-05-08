package dev.pointandshoot

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RootCapabilityTest {

    @Test
    fun `every Feature has a FEATURE_DESCRIPTOR with non-blank purpose and fallback`() {
        for (feature in RootCapability.Feature.entries) {
            val descriptor = RootCapability.FEATURE_DESCRIPTORS[feature]
            assertNotNull("missing descriptor for $feature", descriptor)
            assertTrue("purpose blank for $feature", descriptor!!.purpose.isNotBlank())
            assertTrue("fallback blank for $feature", descriptor.fallback.isNotBlank())
            assertTrue("displayName blank for $feature", descriptor.displayName.isNotBlank())
        }
    }

    @Test
    fun `requireFallbacks does not throw on the shipped catalog`() {
        RootGate.requireFallbacks()
    }

    @Test
    fun `disabledReason always starts with the canonical Requires root prefix`() {
        for (descriptor in RootCapability.FEATURE_DESCRIPTORS.values) {
            assertTrue(
                "disabledReason for ${descriptor.feature}: '${descriptor.disabledReason}'",
                descriptor.disabledReason.startsWith("Requires root. Fallback: "),
            )
        }
    }

    @Test
    fun `RootState grantsPrivileged is true only for Granted`() {
        for (state in RootCapability.RootState.entries) {
            val expected = state == RootCapability.RootState.Granted
            assertEquals("grantsPrivileged for $state", expected, state.grantsPrivileged)
        }
    }

    @Test
    fun `RootState displayName is non-blank for every state`() {
        for (state in RootCapability.RootState.entries) {
            assertTrue("displayName blank for $state", state.displayName.isNotBlank())
        }
    }

    @Test
    fun `evaluate returns one entry per feature in stable enum order`() {
        val results = RootGate.evaluate(RootCapability.RootState.NotAvailable)
        assertEquals(RootCapability.Feature.entries.size, results.size)
        for ((idx, feature) in RootCapability.Feature.entries.withIndex()) {
            assertEquals("at index $idx", feature, results[idx].feature)
        }
    }

    @Test
    fun `evaluate disables every feature when state is NotAvailable`() {
        val results = RootGate.evaluate(RootCapability.RootState.NotAvailable)
        for (r in results) {
            assertFalse("$r should be disabled", r.enabled)
            assertNotNull(r.disabledReason)
            assertTrue(r.disabledReason!!.startsWith("Requires root. Fallback: "))
        }
    }

    @Test
    fun `evaluate disables every feature when state is AvailableNotGranted`() {
        val results = RootGate.evaluate(RootCapability.RootState.AvailableNotGranted)
        for (r in results) {
            assertFalse("$r should be disabled", r.enabled)
            assertNotNull(r.disabledReason)
        }
    }

    @Test
    fun `evaluate enables every feature when state is Granted`() {
        val results = RootGate.evaluate(RootCapability.RootState.Granted)
        for (r in results) {
            assertTrue("$r should be enabled", r.enabled)
            assertNull(r.disabledReason)
        }
    }

    @Test
    fun `evaluate disables every feature when state is Denied`() {
        val results = RootGate.evaluate(RootCapability.RootState.Denied)
        for (r in results) {
            assertFalse("$r should be disabled", r.enabled)
        }
    }

    @Test
    fun `evaluate disables every feature when state is Unknown`() {
        val results = RootGate.evaluate(RootCapability.RootState.Unknown)
        for (r in results) {
            assertFalse("$r should be disabled", r.enabled)
        }
    }

    @Test
    fun `evaluate single-feature accessor returns matching result`() {
        val state = RootCapability.RootState.Granted
        val one = RootGate.evaluate(RootCapability.Feature.VendorSetProp, state)
        assertEquals(RootCapability.Feature.VendorSetProp, one.feature)
        assertTrue(one.enabled)
        assertEquals(state, one.state)
    }

    @Test
    fun `FeatureDescriptor rejects blank displayName`() {
        try {
            RootCapability.FeatureDescriptor(
                feature = RootCapability.Feature.VendorSetProp,
                displayName = "  ",
                purpose = "p",
                fallback = "f",
            )
            org.junit.Assert.fail("expected IllegalArgumentException")
        } catch (_: IllegalArgumentException) {
        }
    }

    @Test
    fun `FeatureDescriptor rejects blank purpose`() {
        try {
            RootCapability.FeatureDescriptor(
                feature = RootCapability.Feature.VendorSetProp,
                displayName = "d",
                purpose = "",
                fallback = "f",
            )
            org.junit.Assert.fail("expected IllegalArgumentException")
        } catch (_: IllegalArgumentException) {
        }
    }

    @Test
    fun `FeatureDescriptor rejects blank fallback`() {
        try {
            RootCapability.FeatureDescriptor(
                feature = RootCapability.Feature.VendorSetProp,
                displayName = "d",
                purpose = "p",
                fallback = "",
            )
            org.junit.Assert.fail("expected IllegalArgumentException")
        } catch (_: IllegalArgumentException) {
        }
    }

    @Test
    fun `RootState transitions match the documented state machine`() {
        assertEquals(RootCapability.RootState.NotAvailable, RootGate.evaluate(RootCapability.RootState.NotAvailable)[0].state)
        assertEquals(RootCapability.RootState.AvailableNotGranted, RootGate.evaluate(RootCapability.RootState.AvailableNotGranted)[0].state)
        assertEquals(RootCapability.RootState.Granted, RootGate.evaluate(RootCapability.RootState.Granted)[0].state)
        assertEquals(RootCapability.RootState.Denied, RootGate.evaluate(RootCapability.RootState.Denied)[0].state)
        assertEquals(RootCapability.RootState.Unknown, RootGate.evaluate(RootCapability.RootState.Unknown)[0].state)
    }

    @Test
    fun `schema version is pinned`() {
        assertEquals(1, RootCapability.SCHEMA_VERSION)
    }

    @Test
    fun `every Feature is included in the catalog`() {
        for (feature in RootCapability.Feature.entries) {
            assertTrue(
                "feature $feature missing from FEATURE_DESCRIPTORS",
                RootCapability.FEATURE_DESCRIPTORS.containsKey(feature),
            )
        }
        assertEquals(RootCapability.Feature.entries.size, RootCapability.FEATURE_DESCRIPTORS.size)
    }
}
