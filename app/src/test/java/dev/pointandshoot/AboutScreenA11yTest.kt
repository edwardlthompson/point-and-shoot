package dev.pointandshoot

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Host-side semantics contract for [AboutScreen] external link chips. */
class AboutScreenA11yTest {

    @Test
    fun externalLinkDescriptions_areNonBlankAndUnique() {
        val descriptions = AboutScreenA11y.EXTERNAL_LINK_DESCRIPTIONS
        assertEquals(4, descriptions.size)
        assertEquals(descriptions.size, descriptions.toSet().size)
        descriptions.forEach { desc ->
            assertTrue("contentDescription must not be blank", desc.isNotBlank())
            assertTrue("contentDescription too short: $desc", desc.length >= 8)
        }
    }

    @Test
    fun backDescription_isNonBlank() {
        assertTrue(AboutScreenA11y.BACK.isNotBlank())
    }
}
