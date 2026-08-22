package dev.pointandshoot

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PnsAppSigningTest {
    @Test
    fun androidDebugSubject_detectsPlatformDebugDn() {
        assertTrue(PnsAppSigning.isAndroidDebugSubject("CN=Android Debug, O=Android, C=US"))
        assertFalse(PnsAppSigning.isAndroidDebugSubject("CN=Point and Shoot, O=PNS, C=US"))
    }

    @Test
    fun fingerprintsMatch_requiresSharedCertBytes() {
        val cert = byteArrayOf(1, 2, 3, 4)
        val other = byteArrayOf(9, 8, 7, 6)
        assertTrue(PnsAppSigning.fingerprintsMatch(listOf(cert), listOf(cert)))
        assertFalse(PnsAppSigning.fingerprintsMatch(listOf(cert), listOf(other)))
        assertFalse(PnsAppSigning.fingerprintsMatch(emptyList(), listOf(cert)))
    }
}
