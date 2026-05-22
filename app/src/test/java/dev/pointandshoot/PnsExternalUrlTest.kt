package dev.pointandshoot

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PnsExternalUrlTest {
    @Test
    fun venmoDonationUrl_usesHttpsAndLockedUserId() {
        assertTrue(PNS_VENMO_DONATION_URL.startsWith("https://"))
        assertTrue(PNS_VENMO_DONATION_URL.contains("venmo.com"))
        assertEquals(
            "https://venmo.com/code?user_id=1857304970395648420",
            PNS_VENMO_DONATION_URL,
        )
    }
}
