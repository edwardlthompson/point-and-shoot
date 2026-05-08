package dev.pointandshoot

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 0 fallback tests for [NativeEncoders]. Runs on the JVM unit-test
 * classpath where no `pns_native.so` is present, so [NativeEncoders.isAvailable]
 * MUST be `false` and every public method MUST return [NativeEncoders.Result.NotAvailable]
 * (or, for [NativeEncoders.version], [NativeEncoders.VERSION_UNAVAILABLE]).
 *
 * These tests lock the no-native fallback contract so a refactor cannot
 * silently re-introduce an `UnsatisfiedLinkError` propagation that would
 * crash the camera app at startup.
 */
class NativeEncodersFallbackTest {

    @Test
    fun `isAvailable is false on the JVM unit test classpath`() {
        assertFalse(
            "JVM unit tests have no pns_native.so; isAvailable must be false " +
                "(actual lastLoadError = ${NativeEncoders.lastLoadError})",
            NativeEncoders.isAvailable,
        )
    }

    @Test
    fun `lastLoadError describes the missing library`() {
        val msg = NativeEncoders.lastLoadError
        assertNotNull("lastLoadError must surface the loadLibrary failure for diagnostics", msg)
        assertTrue("lastLoadError should be non-blank (was: '$msg')", !msg!!.isBlank())
    }

    @Test
    fun `version returns VERSION_UNAVAILABLE when the library is absent`() {
        assertEquals(NativeEncoders.VERSION_UNAVAILABLE, NativeEncoders.version())
    }

    @Test
    fun `encodeAvif10Hdr returns NotAvailable instead of throwing`() {
        val result = NativeEncoders.encodeAvif10Hdr(
            planeY = ByteArray(64),
            planeU = ByteArray(16),
            planeV = ByteArray(16),
            width = 8,
            height = 8,
            strideY = 16,
            strideUV = 8,
        )
        assertEquals(NativeEncoders.Result.NotAvailable, result)
    }

    @Test
    fun `encodeJxl12Rec2020 returns NotAvailable instead of throwing`() {
        val result = NativeEncoders.encodeJxl12Rec2020(
            planeRgb = ByteArray(96),
            width = 4,
            height = 4,
            stride = 24,
        )
        assertEquals(NativeEncoders.Result.NotAvailable, result)
    }

    @Test
    fun `encodeAvif10Hdr handles empty inputs without crashing`() {
        val result = NativeEncoders.encodeAvif10Hdr(
            planeY = ByteArray(0),
            planeU = ByteArray(0),
            planeV = ByteArray(0),
            width = 0,
            height = 0,
            strideY = 0,
            strideUV = 0,
        )
        assertEquals(NativeEncoders.Result.NotAvailable, result)
    }

    @Test
    fun `Result Success equality compares ByteArray contents`() {
        val a = NativeEncoders.Result.Success(byteArrayOf(1, 2, 3))
        val b = NativeEncoders.Result.Success(byteArrayOf(1, 2, 3))
        val c = NativeEncoders.Result.Success(byteArrayOf(1, 2, 4))
        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
        assertTrue(a != c)
    }

    @Test
    fun `Result NotAvailable is a singleton`() {
        assertEquals(NativeEncoders.Result.NotAvailable, NativeEncoders.Result.NotAvailable)
        assertEquals(
            NativeEncoders.Result.NotAvailable.hashCode(),
            NativeEncoders.Result.NotAvailable.hashCode(),
        )
    }

    @Test
    fun `Result NativeError carries opaque code and optional message`() {
        val withMessage = NativeEncoders.Result.NativeError(code = -42, message = "oops")
        val codeOnly = NativeEncoders.Result.NativeError(code = -42)
        assertEquals(-42, withMessage.code)
        assertEquals("oops", withMessage.message)
        assertEquals(-42, codeOnly.code)
        assertNull(codeOnly.message)
        assertTrue("Different message means different result", withMessage != codeOnly)
    }

    @Test
    fun `VERSION_UNAVAILABLE is zero so Phase 0 stub matches`() {
        assertEquals(0, NativeEncoders.VERSION_UNAVAILABLE)
    }
}
