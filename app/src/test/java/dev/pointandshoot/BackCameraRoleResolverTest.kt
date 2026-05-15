package dev.pointandshoot

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BackCameraRoleResolverTest {

    @Test
    fun `four physical backs assign uw wide portraitTele longTele by sorted focal`() {
        val r =
            BackCameraRoleResolver.rolesFromEnumeratedPhysicalsForTests(
                listOf(
                    "uw" to 2.2f,
                    "wide" to 6.1f,
                    "portrait" to 13f,
                    "peri" to 24f,
                ),
            )
        assertEquals("uw", r.ultraWide)
        assertEquals("wide", r.wide)
        assertEquals("portrait", r.tele)
        assertEquals("peri", r.longTele)
    }

    @Test
    fun `three physical backs cluster uw wide tele by focal length`() {
        val r =
            BackCameraRoleResolver.rolesFromEnumeratedPhysicalsForTests(
                listOf(
                    "0" to 6.59f,
                    "2" to 5.59f,
                    "4" to 9.12f,
                ),
            )
        assertEquals("2", r.ultraWide)
        assertEquals("0", r.wide)
        assertEquals("4", r.tele)
    }

    @Test
    fun `three cameras middle focal ties pick remaining id as wide`() {
        val r =
            BackCameraRoleResolver.rolesFromEnumeratedPhysicalsForTests(
                listOf(
                    "a" to 2f,
                    "b" to 4f,
                    "c" to 6f,
                ),
            )
        assertEquals("a", r.ultraWide)
        assertEquals("c", r.tele)
        assertEquals("b", r.wide)
    }

    @Test
    fun `two physical backs assign shorter focal to uw longer to wide`() {
        val r =
            BackCameraRoleResolver.rolesFromEnumeratedPhysicalsForTests(
                listOf(
                    "10" to 2.2f,
                    "11" to 6.8f,
                ),
            )
        assertEquals("10", r.ultraWide)
        assertEquals("11", r.wide)
        assertNull(r.tele)
    }

    @Test
    fun `single physical back is wide only`() {
        val r =
            BackCameraRoleResolver.rolesFromEnumeratedPhysicalsForTests(
                listOf("7" to 4.2f),
            )
        assertEquals("7", r.wide)
        assertNull(r.ultraWide)
        assertNull(r.tele)
    }

    @Test
    fun pickCameraIdFromM23Resolve_prefers_wide_when_in_roster() {
        assertEquals("2", pickCameraIdFromM23Resolve("2" to null, listOf("2", "4")))
    }

    @Test
    fun pickCameraIdFromM23Resolve_ignores_wide_not_in_roster() {
        assertEquals("9", pickCameraIdFromM23Resolve("2" to null, listOf("9", "10")))
    }

    @Test
    fun pickCameraIdFromM23Resolve_null_m23_falls_back_to_first_id() {
        assertEquals("0", pickCameraIdFromM23Resolve(null, listOf("0", "2")))
    }

    @Test
    fun pickCameraIdFromM23Resolve_empty_ids_returns_null() {
        assertNull(pickCameraIdFromM23Resolve("2" to null, emptyList()))
    }

    @Test
    fun m150_preview_pin_always_mid_tele_even_when_long_tele_enumerated() {
        val roles =
            BackCameraRoleResolver.rolesFromEnumeratedPhysicalsForTests(
                listOf(
                    "uw" to 2.2f,
                    "wide" to 6.1f,
                    "portraitTele" to 13f,
                    "periscope" to 24f,
                ),
            )
        assertEquals("portraitTele", roles.tele)
        assertEquals("periscope", roles.longTele)
        assertEquals("portraitTele", telePhysicalForPreviewPin(FocalMmSlot.M150, roles))
        assertEquals("portraitTele", telePhysicalForPreviewPin(FocalMmSlot.M73, roles))
    }
}
