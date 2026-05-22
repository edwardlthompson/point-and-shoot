package dev.pointandshoot

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class QrScanResultActionsTest {
    @Test
    fun resolve_https_isViewUriWithOpenLink() {
        val action =
            QrScanResultActions.resolve("https://example.com/path", "QR_CODE")
        assertTrue(action is QrScanAction.ViewUri)
        val view = action as QrScanAction.ViewUri
        assertEquals("https://example.com/path", view.uri)
        assertEquals("Open link", view.actionLabel)
    }

    @Test
    fun resolve_plainText_isCopyOnly() {
        val action = QrScanResultActions.resolve("hello world", "QR_CODE")
        assertTrue(action is QrScanAction.CopyOnly)
    }

    @Test
    fun resolve_wifi_isCopyOnly() {
        val action = QrScanResultActions.resolve("WIFI:S:MyNet;T:WPA;P:secret;;", null)
        assertTrue(action is QrScanAction.CopyOnly)
    }

    @Test
    fun resolve_tel_isViewUri() {
        val action = QrScanResultActions.resolve("tel:+15551212", null)
        assertTrue(action is QrScanAction.ViewUri)
        assertEquals("Call", (action as QrScanAction.ViewUri).actionLabel)
    }
}
