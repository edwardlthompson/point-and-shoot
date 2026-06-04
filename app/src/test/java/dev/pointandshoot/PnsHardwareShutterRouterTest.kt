package dev.pointandshoot

import android.content.Context
import android.content.pm.ApplicationInfo
import android.view.KeyEvent
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`

class PnsHardwareShutterRouterTest {
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = mock(Context::class.java)
        `when`(context.applicationContext).thenReturn(context)
        `when`(context.applicationInfo).thenReturn(ApplicationInfo())
        PnsHardwareShutterRouter.enabled = true
        PnsHardwareShutterRouter.onShutter = null
        PnsHardwareShutterRouter.onFocusHalfPress = null
        PnsHardwareShutterRouter.onFocusHalfPressRelease = null
    }

    private fun keyEvent(action: Int, keyCode: Int, repeatCount: Int = 0): KeyEvent {
        val event = mock(KeyEvent::class.java)
        `when`(event.action).thenReturn(action)
        `when`(event.keyCode).thenReturn(keyCode)
        `when`(event.scanCode).thenReturn(0)
        `when`(event.source).thenReturn(0)
        `when`(event.repeatCount).thenReturn(repeatCount)
        return event
    }

    @Test
    fun cameraKeyUp_firesWhenEnabled() {
        var fired = false
        PnsHardwareShutterRouter.onShutter = { fired = true }
        val handled =
            PnsHardwareShutterRouter.handleKeyEvent(
                context,
                keyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_CAMERA),
                foreground = true,
            )
        assertTrue(handled)
        assertTrue(fired)
    }

    @Test
    fun cameraKeyDown_consumedWithoutShutter() {
        var fired = false
        PnsHardwareShutterRouter.onShutter = { fired = true }
        val handled =
            PnsHardwareShutterRouter.handleKeyEvent(
                context,
                keyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_CAMERA),
                foreground = true,
            )
        assertTrue(handled)
        assertFalse(fired)
    }

    @Test
    fun focusDown_triggersHalfPress() {
        var half = false
        PnsHardwareShutterRouter.onFocusHalfPress = { half = true }
        val handled =
            PnsHardwareShutterRouter.handleKeyEvent(
                context,
                keyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_FOCUS),
                foreground = true,
            )
        assertTrue(handled)
        assertTrue(half)
    }

    @Test
    fun focusDown_repeatIgnored() {
        var half = false
        PnsHardwareShutterRouter.onFocusHalfPress = { half = true }
        val handled =
            PnsHardwareShutterRouter.handleKeyEvent(
                context,
                keyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_FOCUS, repeatCount = 2),
                foreground = true,
            )
        assertTrue(handled)
        assertFalse(half)
    }

    @Test
    fun focusUp_triggersRelease() {
        var released = false
        PnsHardwareShutterRouter.onFocusHalfPressRelease = { released = true }
        val handled =
            PnsHardwareShutterRouter.handleKeyEvent(
                context,
                keyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_FOCUS),
                foreground = true,
            )
        assertTrue(handled)
        assertTrue(released)
    }

    @Test
    fun disabled_returnsFalse() {
        PnsHardwareShutterRouter.enabled = false
        val handled =
            PnsHardwareShutterRouter.handleKeyEvent(
                context,
                keyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_CAMERA),
                foreground = true,
            )
        assertFalse(handled)
    }
}
