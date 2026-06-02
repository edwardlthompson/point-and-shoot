package dev.pointandshoot.fleet

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FleetParityLogcatParserTest {
    private val sampleLine =
        "05-30 00:34:45.973  7082  7099 I PNS.FleetParity: parityCell=catalogId=raw.dng " +
            "advertised=false sessionOk=false appEnabled=true provenOk=false " +
            "gap=GAP_ADVERTISED_NOT_PROVEN impact=SHIP_BLOCKER failReason=not_advertised durationMs=0"

    @Test
    fun `parseLine extracts catalogId and provenOk`() {
        val cell = FleetParityLogcatParser.parseLine(sampleLine)
        requireNotNull(cell)
        assertEquals("raw.dng", cell.catalogId)
        assertFalse(cell.advertised)
        assertFalse(cell.provenOk)
        assertEquals("GAP_ADVERTISED_NOT_PROVEN", cell.gap)
    }

    @Test
    fun `parseLog dedupes duplicate catalog ids`() {
        val log =
            """
            $sampleLine
            05-30 00:34:45.973  7082  7099 I PNS.AdbValidation: $sampleLine.substringAfter("I PNS.FleetParity: ")
            """.trimIndent()
        val cells = FleetParityLogcatParser.parseLog(log)
        assertEquals(1, cells.size)
    }

    @Test
    fun `gapBreakdown counts advertised not proven`() {
        val cells = FleetParityLogcatParser.parseLog(sampleLine)
        val breakdown = FleetParityLogcatParser.gapBreakdownFromCells(cells)
        assertTrue(breakdown.containsKey("GAP_ADVERTISED_NOT_PROVEN"))
        assertEquals(1, breakdown["GAP_ADVERTISED_NOT_PROVEN"])
    }
}
