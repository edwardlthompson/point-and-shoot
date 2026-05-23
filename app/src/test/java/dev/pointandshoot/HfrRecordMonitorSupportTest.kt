package dev.pointandshoot

import org.junit.Assert.assertEquals
import org.junit.Test

class HfrRecordMonitorSupportTest {
    @Test
    fun pickMonitor_wideWhenRecordingTele() {
        val roles =
            BackCameraRoleResolver.rolesFromEnumeratedPhysicalsForTests(
                listOf("2" to 14f, "3" to 24f, "4" to 73f),
            )
        assertEquals("3", HfrRecordMonitorSupport.pickMonitorCameraId(roles, "4", listOf("2", "3", "4")))
    }

    @Test
    fun pickMonitor_uwWhenRecordingWide() {
        val roles =
            BackCameraRoleResolver.rolesFromEnumeratedPhysicalsForTests(
                listOf("2" to 14f, "3" to 24f, "4" to 73f),
            )
        assertEquals("2", HfrRecordMonitorSupport.pickMonitorCameraId(roles, "3", listOf("2", "3", "4")))
    }
}
