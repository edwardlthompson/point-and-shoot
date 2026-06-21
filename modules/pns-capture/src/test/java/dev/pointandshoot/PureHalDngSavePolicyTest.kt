package dev.pointandshoot

import dev.pointandshoot.fleet.ReferenceAppPipelineContract
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PureHalDngSavePolicyTest {

    @Test
    fun enabled_isGlobalDefault() {
        assertTrue(PureHalDngSavePolicy.ENABLED)
    }

    @Test
    fun leafPostSaveReconcile_disabledWhenPureHal() {
        assertFalse(ReferenceAppPipelineContract.leafPostSaveTiffReconcileEnabled("3"))
        assertFalse(ReferenceAppPipelineContract.leafPostSaveTiffReconcileEnabled("4"))
    }
}
