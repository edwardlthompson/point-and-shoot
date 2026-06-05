package dev.pointandshoot.fleet

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LeaderboardRomReportTest {

    @Test
    fun stockReport_acceptsStockAndRoot() {
        assertTrue(LeaderboardRomReport.isConsistent(LeaderboardRomReport.Reported.STOCK, "stock"))
        assertTrue(LeaderboardRomReport.isConsistent(LeaderboardRomReport.Reported.STOCK, "root_unlocked"))
    }

    @Test
    fun lineageReport_rejectsPureStock() {
        assertFalse(LeaderboardRomReport.isConsistent(LeaderboardRomReport.Reported.LINEAGE, "stock"))
        assertTrue(LeaderboardRomReport.isConsistent(LeaderboardRomReport.Reported.LINEAGE, "custom_likely"))
    }
}
