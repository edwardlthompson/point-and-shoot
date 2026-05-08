package dev.pointandshoot

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RootCapabilityProbeTest {

    @Test
    fun `CANONICAL_SU_PATHS has the documented entries in stable order`() {
        val expected = listOf(
            "/system/xbin/su",
            "/system/bin/su",
            "/sbin/su",
            "/system/sbin/su",
            "/su/bin/su",
            "/system/app/Superuser.apk",
            "/data/adb/magisk",
            "/data/adb/ksu",
            "/data/adb/ksud",
        )
        assertEquals(expected, RootCapabilityProbe.CANONICAL_SU_PATHS)
    }

    @Test
    fun `CANONICAL_SU_PATHS has no duplicates`() {
        val list = RootCapabilityProbe.CANONICAL_SU_PATHS
        val deduped = list.toSet()
        assertEquals(list.size, deduped.size)
    }

    @Test
    fun `CANONICAL_SU_PATHS entries are absolute paths`() {
        for (path in RootCapabilityProbe.CANONICAL_SU_PATHS) {
            assertTrue("not absolute: $path", path.startsWith('/'))
        }
    }

    @Test
    fun `collateExistence returns NotAvailable when no SU paths exist`() {
        val state = RootCapabilityProbe.collateExistence { false }
        assertEquals(RootCapability.RootState.NotAvailable, state)
    }

    @Test
    fun `collateExistence returns AvailableNotGranted when at least one SU path exists`() {
        val state = RootCapabilityProbe.collateExistence { path -> path == "/system/xbin/su" }
        assertEquals(RootCapability.RootState.AvailableNotGranted, state)
    }

    @Test
    fun `collateExistence returns AvailableNotGranted when only Magisk dir exists`() {
        val state = RootCapabilityProbe.collateExistence { path -> path == "/data/adb/magisk" }
        assertEquals(RootCapability.RootState.AvailableNotGranted, state)
    }

    @Test
    fun `collateExistence returns AvailableNotGranted when only KernelSU dir exists`() {
        val state = RootCapabilityProbe.collateExistence { path -> path == "/data/adb/ksu" }
        assertEquals(RootCapability.RootState.AvailableNotGranted, state)
    }

    @Test
    fun `collateExistence returns AvailableNotGranted when only Superuser apk exists`() {
        val state = RootCapabilityProbe.collateExistence { path -> path == "/system/app/Superuser.apk" }
        assertEquals(RootCapability.RootState.AvailableNotGranted, state)
    }

    @Test
    fun `collateExistence returns AvailableNotGranted when every SU path exists`() {
        val state = RootCapabilityProbe.collateExistence { true }
        assertEquals(RootCapability.RootState.AvailableNotGranted, state)
    }

    @Test
    fun `collateExistence ignores non-canonical paths`() {
        val state = RootCapabilityProbe.collateExistence { path -> path == "/data/local/tmp/su" }
        assertEquals(RootCapability.RootState.NotAvailable, state)
    }

    @Test
    fun `parseIdOutput returns Granted when stdout contains uid=0`() {
        val state = RootCapabilityProbe.parseIdOutput("uid=0(root) gid=0(root) groups=0(root) context=u:r:su:s0")
        assertEquals(RootCapability.RootState.Granted, state)
    }

    @Test
    fun `parseIdOutput returns Granted when uid=0 has no surrounding parens`() {
        val state = RootCapabilityProbe.parseIdOutput("uid=0 gid=0")
        assertEquals(RootCapability.RootState.Granted, state)
    }

    @Test
    fun `parseIdOutput returns Denied when stdout has a non-zero uid`() {
        val state = RootCapabilityProbe.parseIdOutput("uid=10247(u0_a247) gid=10247(u0_a247) groups=...")
        assertEquals(RootCapability.RootState.Denied, state)
    }

    @Test
    fun `parseIdOutput returns Denied for empty stdout`() {
        val state = RootCapabilityProbe.parseIdOutput("")
        assertEquals(RootCapability.RootState.Denied, state)
    }

    @Test
    fun `parseIdOutput returns Denied for null stdout`() {
        val state = RootCapabilityProbe.parseIdOutput(null)
        assertEquals(RootCapability.RootState.Denied, state)
    }

    @Test
    fun `parseIdOutput returns Denied for SU manager error message`() {
        val state = RootCapabilityProbe.parseIdOutput("Permission denied")
        assertEquals(RootCapability.RootState.Denied, state)
    }

    @Test
    fun `ACTIVE_PROBE_TIMEOUT_SECONDS is pinned at 5`() {
        assertEquals(5L, RootCapabilityProbe.ACTIVE_PROBE_TIMEOUT_SECONDS)
    }

    @Test
    fun `probeStatic on JVM unit-test classpath returns NotAvailable`() {
        val state = RootCapabilityProbe.probeStatic()
        assertFalse(
            "JVM unit-test classpath should never have a real SU binary - state was $state",
            state == RootCapability.RootState.AvailableNotGranted,
        )
        assertEquals(RootCapability.RootState.NotAvailable, state)
    }
}
