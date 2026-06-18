package dev.pointandshoot

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CapabilityGateTest {

    private val fullStack = HardwareCaps(
        hasRawCapability = true,
        has12BitDepth = true,
        has120FpsHfr = true,
        hasFaceDetectFull = true,
        hasPreviewHistogram = true,
        aeCompensationStepsAvailable = 24,
        hasMacroMode = true,
        has10BitHdrPipeline = true,
        hasOpticalStabilization = true,
        supportedCameraExtensionLabels = "HDR,NIGHT",
        supportsYuvReprocessing = true,
        supportsPrivateReprocessing = false,
        reprocessMaxCaptureStall = 4,
        reprocessEffectiveExposureRequestKey = true,
    )

    private val barebones = HardwareCaps(
        hasRawCapability = false,
        has12BitDepth = false,
        has120FpsHfr = false,
        hasFaceDetectFull = false,
        hasPreviewHistogram = false,
        aeCompensationStepsAvailable = 0,
        hasMacroMode = false,
        has10BitHdrPipeline = false,
        hasOpticalStabilization = false,
        supportedCameraExtensionLabels = "",
        supportsYuvReprocessing = false,
        supportsPrivateReprocessing = false,
        reprocessMaxCaptureStall = null,
        reprocessEffectiveExposureRequestKey = false,
    )

    @Test
    fun `every feature is enabled on a fully-capable device with no reasons`() {
        val results = CapabilityGate.evaluate(fullStack)
        assertEquals(Feature.entries.size, results.size)
        for (r in results) {
            assertTrue("expected ${r.feature} enabled", r.enabled)
            assertNull(r.disabledReason)
        }
    }

    @Test
    fun `every feature is disabled with a reason on a barebones device`() {
        val results = CapabilityGate.evaluate(barebones)
        assertEquals(Feature.entries.size, results.size)
        for (r in results) {
            assertFalse("expected ${r.feature} disabled", r.enabled)
            assertNotNull("expected reason for ${r.feature}", r.disabledReason)
        }
    }

    @Test
    fun `Ultra-Max requires both RAW and 12-bit depth`() {
        val rawOnly = barebones.copy(hasRawCapability = true)
        val results = CapabilityGate.evaluate(rawOnly)
        val ultra = results.first { it.feature == Feature.UltraMaxProfile }
        assertFalse(ultra.enabled)
        val rawDng = results.first { it.feature == Feature.RawDng }
        assertTrue(rawDng.enabled)
    }

    @Test
    fun `recommended defaults are empty when standard pro baseline is unmet`() {
        // Standard Pro = RAW + 10-bit AVIF.
        val partial = barebones.copy(hasRawCapability = true) // missing 10-bit pipeline
        val defaults = CapabilityGate.recommendedDefaults(partial)
        assertTrue(defaults.isEmpty())
    }

    @Test
    fun `recommended defaults include every enabled feature when baseline is met`() {
        val defaults = CapabilityGate.recommendedDefaults(fullStack)
        for (feat in Feature.entries) {
            assertTrue("expected $feat in defaults", defaults.contains(feat))
        }
    }

    @Test
    fun `OIS gate flips with hasOpticalStabilization`() {
        val withOis = barebones.copy(hasOpticalStabilization = true)
        val withoutOis = barebones.copy(hasOpticalStabilization = false)
        val on = CapabilityGate.evaluate(withOis).first { it.feature == Feature.OpticalStabilization }
        val off = CapabilityGate.evaluate(withoutOis).first { it.feature == Feature.OpticalStabilization }

        assertTrue(on.enabled)
        assertNull(on.disabledReason)
        assertFalse(off.enabled)
        assertNotNull(off.disabledReason)
    }

    @Test
    fun `OIS feature is included in the gate output and surfaces a stable display name`() {
        val results = CapabilityGate.evaluate(fullStack)
        val ois = results.first { it.feature == Feature.OpticalStabilization }
        assertTrue(ois.enabled)
        assertEquals("Optical image stabilization", Feature.OpticalStabilization.displayName)
    }

    @Test
    fun `HardwareCaps OIS field defaults to false for backward compatibility`() {
        val caps = HardwareCaps(
            hasRawCapability = true,
            has12BitDepth = true,
            has120FpsHfr = false,
            hasFaceDetectFull = false,
            hasPreviewHistogram = false,
            aeCompensationStepsAvailable = 0,
            hasMacroMode = false,
            has10BitHdrPipeline = true,
        )
        assertFalse(caps.hasOpticalStabilization)
    }
}
