package dev.pointandshoot

import android.content.Context
import android.content.pm.ApplicationInfo
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`

class FocusConfirmSoundTest {
    private lateinit var context: Context
    private lateinit var manager: ShutterSoundManager

    @Before
    fun setUp() {
        context = mock(Context::class.java)
        `when`(context.applicationContext).thenReturn(context)
        `when`(context.applicationInfo).thenReturn(ApplicationInfo())
        manager = ShutterSoundManager(context)
    }

    @Test
    fun playFocusConfirm_silentPack_noCrash() {
        val chrome =
            PreviewChromePreferences(
                shutterSoundPackKey = ShutterSoundPack.Silent.storageKey,
                shutterSoundVolume = 0.85f,
            )
        manager.playFocusConfirm(chrome)
    }

    @Test
    fun playFocusConfirm_zeroVolume_noCrash() {
        val chrome =
            PreviewChromePreferences(
                shutterSoundPackKey = ShutterSoundPack.ClassicMechanical.storageKey,
                shutterSoundVolume = 0f,
            )
        manager.playFocusConfirm(chrome)
    }

    @Test
    fun playFocusConfirm_mechanicalPack_noCrash() {
        val chrome =
            PreviewChromePreferences(
                shutterSoundPackKey = ShutterSoundPack.ClassicMechanical.storageKey,
                shutterSoundVolume = 0.5f,
            )
        manager.playFocusConfirm(chrome)
    }
}
