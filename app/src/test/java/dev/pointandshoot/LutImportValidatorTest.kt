package dev.pointandshoot

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LutImportValidatorTest {

    private fun identityCubeText(size: Int): String {
        val lut = Lut3D.identity(size)
        return LutPipeline.serializeCube(lut, title = "test identity")
    }

    // ---------- success ----------

    @Test
    fun `accepts identity cube at every supported size`() {
        for (size in Lut3D.SUPPORTED_SIZES) {
            val result = LutImportValidator.validate(identityCubeText(size))
            assertTrue("size=$size yielded $result", result is LutImportValidator.Result.Success)
            val success = result as LutImportValidator.Result.Success
            assertEquals(size, success.lut.size)
            assertTrue("size=$size lut should be identity", success.lut.isIdentity())
        }
    }

    @Test
    fun `accepts a non-identity cube within the value-range tolerance`() {
        val size = 17
        val samples = FloatArray(size * size * size * 3)
        for (b in 0 until size) for (g in 0 until size) for (r in 0 until size) {
            val idx = ((b * size + g) * size + r) * 3
            samples[idx] = (r.toFloat() / (size - 1)) * 0.95f
            samples[idx + 1] = (g.toFloat() / (size - 1)) * 0.95f
            samples[idx + 2] = (b.toFloat() / (size - 1)) * 0.95f
        }
        val text = LutPipeline.serializeCube(Lut3D(size, samples))
        val result = LutImportValidator.validate(text)
        assertTrue(result is LutImportValidator.Result.Success)
    }

    @Test
    fun `validate(ByteArray) wraps the String overload`() {
        val bytes = identityCubeText(33).toByteArray(Charsets.UTF_8)
        val result = LutImportValidator.validate(bytes)
        assertTrue(result is LutImportValidator.Result.Success)
    }

    // ---------- failure: file size cap ----------

    @Test
    fun `rejects a payload larger than MAX_TEXT_BYTES on the bytes overload`() {
        // Allocate slightly larger than the cap; content does not need to be valid Cube.
        val tooMany = (LutImportValidator.MAX_TEXT_BYTES + 1).toInt()
        val bytes = ByteArray(tooMany) { '0'.code.toByte() }
        val result = LutImportValidator.validate(bytes)
        assertTrue(result is LutImportValidator.Result.Failure)
        val fail = result as LutImportValidator.Result.Failure
        assertEquals(LutImportValidator.FailureCategory.TooLarge, fail.category)
        assertNull(fail.cause)
    }

    // ---------- failure: malformed header ----------

    @Test
    fun `rejects cube with no LUT_3D_SIZE`() {
        val text = "0 0 0\n1 1 1\n"
        val result = LutImportValidator.validate(text)
        assertTrue(result is LutImportValidator.Result.Failure)
        val fail = result as LutImportValidator.Result.Failure
        assertEquals(LutImportValidator.FailureCategory.MalformedHeader, fail.category)
        assertNotNull(fail.cause)
    }

    @Test
    fun `rejects cube with non-integer LUT_3D_SIZE`() {
        val text = "LUT_3D_SIZE seventeen\n"
        val result = LutImportValidator.validate(text)
        assertTrue(result is LutImportValidator.Result.Failure)
        val fail = result as LutImportValidator.Result.Failure
        assertEquals(LutImportValidator.FailureCategory.MalformedHeader, fail.category)
    }

    // ---------- failure: 1D LUT ----------

    @Test
    fun `rejects cube declaring LUT_1D_SIZE`() {
        val text = "LUT_1D_SIZE 33\n0 0 0\n"
        val result = LutImportValidator.validate(text)
        assertTrue(result is LutImportValidator.Result.Failure)
        val fail = result as LutImportValidator.Result.Failure
        assertEquals(LutImportValidator.FailureCategory.OneDLut, fail.category)
    }

    // ---------- failure: unsupported size ----------

    @Test
    fun `rejects cube with unsupported LUT_3D_SIZE`() {
        // 13 is a valid cube format but our pipeline only supports 17 / 33 / 65.
        val size = 13
        val sb = StringBuilder()
        sb.appendLine("LUT_3D_SIZE $size")
        sb.appendLine("DOMAIN_MIN 0 0 0")
        sb.appendLine("DOMAIN_MAX 1 1 1")
        for (i in 0 until (size * size * size)) sb.appendLine("0 0 0")
        val result = LutImportValidator.validate(sb.toString())
        assertTrue(result is LutImportValidator.Result.Failure)
        val fail = result as LutImportValidator.Result.Failure
        assertEquals(LutImportValidator.FailureCategory.UnsupportedSize, fail.category)
    }

    // ---------- failure: non-[0,1] domain ----------

    @Test
    fun `rejects cube declaring a non-unit DOMAIN_MAX`() {
        val text = """
            LUT_3D_SIZE 17
            DOMAIN_MIN 0 0 0
            DOMAIN_MAX 4 4 4
        """.trimIndent()
        val result = LutImportValidator.validate(text)
        assertTrue(result is LutImportValidator.Result.Failure)
        val fail = result as LutImportValidator.Result.Failure
        assertEquals(LutImportValidator.FailureCategory.NonUnitDomain, fail.category)
    }

    // ---------- failure: malformed body ----------

    @Test
    fun `rejects cube with a non-float in the body`() {
        val text = """
            LUT_3D_SIZE 17
            DOMAIN_MIN 0 0 0
            DOMAIN_MAX 1 1 1
            0 zero 0
        """.trimIndent()
        val result = LutImportValidator.validate(text)
        assertTrue(result is LutImportValidator.Result.Failure)
        val fail = result as LutImportValidator.Result.Failure
        assertEquals(LutImportValidator.FailureCategory.MalformedBody, fail.category)
    }

    @Test
    fun `rejects cube with the wrong number of triples`() {
        val text = """
            LUT_3D_SIZE 17
            DOMAIN_MIN 0 0 0
            DOMAIN_MAX 1 1 1
            0 0 0
            1 1 1
        """.trimIndent()
        val result = LutImportValidator.validate(text)
        assertTrue(result is LutImportValidator.Result.Failure)
        val fail = result as LutImportValidator.Result.Failure
        assertEquals(LutImportValidator.FailureCategory.SizeMismatch, fail.category)
    }

    // ---------- failure: out-of-range cell value ----------

    @Test
    fun `rejects cube with a cell well outside the value tolerance band`() {
        val size = 17
        val samples = FloatArray(size * size * size * 3) { 0f }
        samples[0] = 5.0f  // wildly out of range
        val text = LutPipeline.serializeCube(Lut3D(size, samples))
        val result = LutImportValidator.validate(text)
        assertTrue(result is LutImportValidator.Result.Failure)
        val fail = result as LutImportValidator.Result.Failure
        assertEquals(LutImportValidator.FailureCategory.OutOfRange, fail.category)
        assertTrue("expected message to mention sample index: ${fail.message}",
            fail.message.contains("sample index"))
    }

    @Test
    fun `accepts cube with cells inside the +-0_001 tolerance band`() {
        val size = 17
        val samples = FloatArray(size * size * size * 3) { 0f }
        samples[0] = -0.0005f                                 // tiny negative ok
        samples[samples.size - 1] = 1.0009f                    // tiny over-1 ok
        val text = LutPipeline.serializeCube(Lut3D(size, samples))
        val result = LutImportValidator.validate(text)
        assertTrue("expected Success but got $result", result is LutImportValidator.Result.Success)
    }

    // ---------- toast message ----------

    @Test
    fun `Failure_toastMessage prefixes the category label`() {
        val text = "LUT_1D_SIZE 33\n0 0 0\n"
        val result = LutImportValidator.validate(text) as LutImportValidator.Result.Failure
        val toast = result.toastMessage()
        assertTrue("expected '1D LUT' label in toast: $toast", toast.contains("1D LUT"))
        assertTrue("expected 'rejected' wording: $toast", toast.contains("rejected"))
    }

    // ---------- categorize coverage ----------

    @Test
    fun `every FailureCategory has a non-blank toast label`() {
        for (cat in LutImportValidator.FailureCategory.entries) {
            assertTrue("category $cat has blank toastLabel", cat.toastLabel.isNotBlank())
        }
    }
}
