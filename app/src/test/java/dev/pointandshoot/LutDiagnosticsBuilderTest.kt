package dev.pointandshoot

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LutDiagnosticsBuilderTest {

    @Test
    fun `default state reports identity LUT and no calibration`() {
        val text = LutDiagnosticsBuilder.buildSection(LutDiagnosticsBuilder.ActiveColorState.Default)
        assertTrue("expected '## Color & LUT' header in:\n$text", text.contains("## Color & LUT"))
        assertTrue("expected None LUT in:\n$text", text.contains("active LUT: None"))
        assertTrue("expected no-calibration marker in:\n$text", text.contains("active calibration: (none"))
    }

    @Test
    fun `custom state reports active LUT name spdx and sha`() {
        val state = LutDiagnosticsBuilder.ActiveColorState(
            activeLutName = "Point & Shoot Cinematic",
            activeLutSpdx = "Apache-2.0",
            activeLutSha256 = "deadbeefcafebabe",
            calibrationProfileId = "wb-D65-2026-05-08T1830Z.json",
            calibrationCapturedAtUtc = "2026-05-08T18:30:00Z",
        )
        val text = LutDiagnosticsBuilder.buildSection(state)
        assertTrue(text.contains("active LUT: Point & Shoot Cinematic"))
        assertTrue(text.contains("spdx=Apache-2.0"))
        assertTrue(text.contains("LUT sha256: deadbeefcafebabe"))
        assertTrue(text.contains("active calibration: wb-D65-2026-05-08T1830Z.json"))
        assertTrue(text.contains("calibration captured (UTC): 2026-05-08T18:30:00Z"))
    }

    @Test
    fun `every catalog entry appears in the bundled section`() {
        val text = LutDiagnosticsBuilder.buildSection(LutDiagnosticsBuilder.ActiveColorState.Default)
        for (entry in LutCatalog.entries) {
            assertTrue(
                "expected catalog entry '${entry.displayName}' in:\n$text",
                text.contains(entry.displayName),
            )
        }
    }

    @Test
    fun `bundled section lists each entry's spdx and source`() {
        val text = LutDiagnosticsBuilder.buildSection(LutDiagnosticsBuilder.ActiveColorState.Default)
        for (entry in LutCatalog.entries) {
            assertTrue(
                "expected SPDX '${entry.spdx}' alongside '${entry.displayName}'",
                text.contains("spdx=${entry.spdx}"),
            )
        }
    }

    @Test
    fun `Allowed SPDX whitelist is enumerated in sorted order`() {
        val text = LutDiagnosticsBuilder.buildSection(LutDiagnosticsBuilder.ActiveColorState.Default)
        val sortedJoined = LutCatalog.ALLOWED_SPDX.sorted().joinToString(", ")
        assertTrue("expected '$sortedJoined' in:\n$text", text.contains(sortedJoined))
    }

    @Test
    fun `output ends with a trailing newline`() {
        val text = LutDiagnosticsBuilder.buildSection(LutDiagnosticsBuilder.ActiveColorState.Default)
        assertTrue("expected trailing newline", text.endsWith("\n"))
    }

    @Test
    fun `null calibration capture timestamp omits the captured-at line`() {
        val state = LutDiagnosticsBuilder.ActiveColorState.Default
        val text = LutDiagnosticsBuilder.buildSection(state)
        assertFalse("did not expect captured-at line", text.contains("calibration captured"))
    }

    @Test
    fun `Default state matches LutCatalog None entry`() {
        val state = LutDiagnosticsBuilder.ActiveColorState.Default
        assertEquals(LutCatalog.None.displayName, state.activeLutName)
        assertEquals(LutCatalog.None.spdx, state.activeLutSpdx)
    }
}
