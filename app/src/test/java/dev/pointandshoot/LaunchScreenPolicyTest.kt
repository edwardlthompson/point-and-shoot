package dev.pointandshoot

import android.content.Intent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LaunchScreenPolicyTest {

    @Test
    fun homeLauncherIcon_detectsResetTaskFlag() {
        val home =
            Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
            }
        assertTrue(LaunchScreenPolicy.isHomeLauncherIconLaunch(home))
    }

    @Test
    fun adbComponentStart_withoutResetTask_isNotHomeIcon() {
        // Typical `am start -n … --activity-clear-task` shape: MAIN+LAUNCHER without RESET_TASK.
        val adb =
            Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
        assertFalse(LaunchScreenPolicy.isHomeLauncherIconLaunch(adb))
    }

    @Test
    fun effectiveLaunchScreen_homeIcon_forcesPreviewNull() {
        val screen =
            LaunchScreenPolicy.effectiveLaunchScreen(
                gateOrResolvedScreen = PNS_SCREEN_PROBE_HUB,
                safeModeActive = true,
                homeLauncherIconLaunch = true,
            )
        assertNull(screen)
    }

    @Test
    fun effectiveLaunchScreen_safeMode_doesNotRemapToProbeHub() {
        val screen =
            LaunchScreenPolicy.effectiveLaunchScreen(
                gateOrResolvedScreen = null,
                safeModeActive = true,
                homeLauncherIconLaunch = false,
            )
        assertNull(screen)
        assertEquals(
            PNS_SCREEN_PREVIEW,
            LaunchScreenPolicy.effectiveLaunchScreen(
                gateOrResolvedScreen = PNS_SCREEN_PREVIEW,
                safeModeActive = true,
                homeLauncherIconLaunch = false,
            ),
        )
    }

    @Test
    fun effectiveLaunchScreen_adbProbeHub_preservedWhenNotHomeIcon() {
        assertEquals(
            PNS_SCREEN_PROBE_HUB,
            LaunchScreenPolicy.effectiveLaunchScreen(
                gateOrResolvedScreen = PNS_SCREEN_PROBE_HUB,
                safeModeActive = false,
                homeLauncherIconLaunch = false,
            ),
        )
    }
}
