package dev.pointandshoot.baselineprofile

import android.content.ComponentName
import android.content.Intent
import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Generates baseline / startup profiles for the `:app` module (cold start of [dev.pointandshoot.MainActivity]).
 *
 * Run on a physical device or emulator (API 28+):
 * `.\scripts\pns_baseline_profile_generate.ps1` **or** `.\gradlew.bat :app:generateBaselineProfile`
 *
 * Outputs are merged by AGP into **`app/src/release/generated/baselineProfiles/`** (see app **`baselineProfile { saveInSrc = true }`**).
 */
@RunWith(AndroidJUnit4::class)
@LargeTest
class BaselineProfileGenerator {

    @get:Rule
    val rule = BaselineProfileRule()

    @Test
    fun generateStartupProfile() {
        rule.collect(
            packageName = "dev.pointandshoot",
            includeInStartupProfile = true,
        ) {
            pressHome()
            val intent =
                Intent(Intent.ACTION_MAIN).apply {
                    addCategory(Intent.CATEGORY_LAUNCHER)
                    component =
                        ComponentName(
                            "dev.pointandshoot",
                            "dev.pointandshoot.MainActivity",
                        )
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                }
            startActivityAndWait(intent)
        }
    }
}
