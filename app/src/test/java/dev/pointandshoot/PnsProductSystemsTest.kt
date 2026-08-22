package dev.pointandshoot

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PnsProductSystemsTest {
    @Test
    fun intervalometerRamp_isLinear() {
        assertEquals(100, IntervalometerRamp.step(100, 800, 0, 3))
        assertEquals(450, IntervalometerRamp.step(100, 800, 1, 3))
        assertEquals(800, IntervalometerRamp.step(100, 800, 2, 3))
    }

    @Test
    fun powerProfile_capsFps() {
        assertEquals(120, PnsPowerProfile.applyCap(120, PnsPowerProfile.Performance))
        assertEquals(60, PnsPowerProfile.applyCap(120, PnsPowerProfile.Balanced))
        assertEquals(30, PnsPowerProfile.applyCap(120, PnsPowerProfile.Endurance))
    }

    @Test
    fun finderReason_isPlainLanguage() {
        assertEquals(
            "Finder slowed because the phone is hot.",
            PnsFinderChangeReason.sentence("thermal=3 cap=60"),
        )
        assertEquals(
            "Finder slowed to save battery.",
            PnsFinderChangeReason.sentence("battery<=10% cap=30"),
        )
    }

    @Test
    fun motionTrip_needsDelta() {
        assertFalse(PnsMotionTrip.shouldFire(null, 10f))
        assertFalse(PnsMotionTrip.shouldFire(10f, 12f, 8f))
        assertTrue(PnsMotionTrip.shouldFire(10f, 20f, 8f))
    }

    @Test
    fun focusStack_hasRequestedCount() {
        val dists = PnsFocusStack.distancesDiopter(0.1f, 1f, 5)
        assertEquals(5, dists.size)
        assertTrue(dists.first() < dists.last())
    }

    @Test
    fun chapterMarks_formatSidecar() {
        VideoChapterMarks.clear()
        VideoChapterMarks.add(1_500L, "mark")
        assertTrue(VideoChapterMarks.sidecarText().contains("1.500"))
        VideoChapterMarks.clear()
    }

    @Test
    fun privacyReceipt_isNonEmpty() {
        assertTrue(PnsPrivacyReceipt.LINES.size >= 4)
    }

    @Test
    fun geotagMode_legacyOnIsPrecise() {
        assertEquals(PnsGeotagMode.Precise, PnsGeotagMode.fromStorage(null, true))
        assertEquals(PnsGeotagMode.Off, PnsGeotagMode.fromStorage(null, false))
        assertEquals(PnsGeotagMode.Coarse, PnsGeotagMode.fromStorage("coarse", false))
    }

    @Test
    fun deepLinkShoot_setsComposedStill() {
        val route = PlatformIntegration.parseDeepLinkString("pointandshoot://preview?shoot=1")
        assertEquals(true, route?.composedStill)
        assertEquals(false, PlatformIntegration.parseDeepLinkString("pointandshoot://preview")?.composedStill)
    }

    @Test
    fun galleryGroupKey_usesBracketAndTimestamp() {
        assertEquals(
            "bkt-abc123def",
            GalleryCaptureGroups.groupKey("pns_20260821t120000z_standard_pro_0001_bkt1of3-bkt-abc123def.dng"),
        )
        assertEquals(
            "shot:20260821t120000z",
            GalleryCaptureGroups.groupKey("pns_20260821t120000z_standard_pro_0001.dng"),
        )
    }

    @Test
    fun captureJournal_keepsLatest() {
        CaptureJournal.clear()
        CaptureJournal.record(true, "ok")
        CaptureJournal.record(false, "nope")
        assertEquals("failed · nope", CaptureJournal.latestLine())
        CaptureJournal.clear()
    }

    @Test
    fun remoteProtocol_parsesHttpAndBle() {
        val http = PnsRemoteProtocol.parseQuery("shutter", null)
        assertEquals(PnsRemoteProtocol.Action.Shutter, http?.action)
        val ble = PnsRemoteProtocol.parseBle(byteArrayOf(0x10, 5))
        assertEquals(PnsRemoteProtocol.Action.Timer, ble?.action)
        assertEquals(5, ble?.normalizedTimerSec)
        assertTrue(PnsRemoteProtocol.statusJson(true, false, true, "10.0.0.4", 28766).contains("recording\":true"))
        val cancel = PnsRemoteProtocol.parseQuery("timer_cancel", null)
        assertEquals(PnsRemoteProtocol.Action.CancelTimer, cancel?.action)
        assertEquals(
            PnsRemoteProtocol.Action.CancelTimer,
            PnsRemoteProtocol.parseBle(byteArrayOf(0x11))?.action,
        )
    }

    @Test
    fun hdmiTypeRank_prefersCableOverWifi() {
        assertTrue(PnsExternalOutput.typeRankFor(PnsExternalOutput.TYPE_HDMI, true) >
            PnsExternalOutput.typeRankFor(PnsExternalOutput.TYPE_WIFI, true))
        assertEquals(0, PnsExternalOutput.typeRankFor(0, false))
        assertEquals(5, PnsExternalOutput.typeRankFor(0, true))
    }

    @Test
    fun webcamLadder_thermalDropsUhd() {
        assertEquals("uhd60", PnsWebcamLadder.pick(0, 0, true).name)
        assertEquals("uhd30", PnsWebcamLadder.pick(ApiLevelGuards.THERMAL_STATUS_MODERATE, 0, true).name)
        assertEquals("1080p60", PnsWebcamLadder.pick(ApiLevelGuards.THERMAL_STATUS_SEVERE, 0, true).name)
        assertEquals("720p30", PnsWebcamLadder.pick(ApiLevelGuards.THERMAL_STATUS_CRITICAL, 0, true).name)
        assertEquals("1080p60", PnsWebcamLadder.pick(0, 0, allowUhd = false).name)
        assertEquals(1, PnsWebcamLadder.nextFloor(0))
        assertEquals(null, PnsWebcamLadder.nextFloor(3))
    }

    @Test
    fun webcamControls_applyFocusAndZoom() {
        val json = PnsWebcamControls.applyQuery(mapOf("focus_auto" to "0", "zoom" to "2.5", "ae_ev" to "3"))
        assertTrue(json.contains("\"af\":\"manual\""))
        assertTrue(json.contains("\"zoom\":2.5"))
        assertTrue(json.contains("\"ev\":3"))
        PnsWebcamControls.applyQuery(mapOf("focus_auto" to "1", "zoom" to "1", "ae_ev" to "0"))
    }

    @Test
    fun usbWebcam_parsesUvcAndTether() {
        val uvc = PnsUsbWebcam.parseUsbState(true, true, true, false, false, true)
        assertTrue(uvc.uvc)
        assertTrue(uvc.usbData)
        val tether = PnsUsbWebcam.parseUsbState(true, true, false, true, false, false)
        assertTrue(tether.tether)
        assertFalse(tether.uvc)
        assertEquals("usbvideo.sys", PnsUsbWebcam.WINDOWS_INBOX_DRIVER)
        assertEquals("svc usb setFunctions uvc", PnsUsbWebcam.SVC_SET_FUNCTIONS_UVC)
        assertEquals("svc usb setScreenUnlockedFunctions uvc", PnsUsbWebcam.SVC_LOCK_FUNCTIONS_UVC)
        assertTrue(PnsUsbWebcam.SETTINGS_CONNECTED_DEVICES_ACTIVITY.contains("ConnectedDeviceDashboardActivity"))
    }

    @Test
    fun geotagPrivacy_coarseRounds() {
        assertEquals(1.23, PnsGeotagPrivacy.roundCoord(1.23456), 0.001)
        assertFalse(PnsGeotagPrivacy.shouldEmbed(PnsGeotagMode.Off))
        assertTrue(PnsGeotagPrivacy.shouldEmbed(PnsGeotagMode.Coarse))
    }

    @Test
    fun xmpSidecar_includesRating() {
        val packet = DngXmpSidecar.packet("a.dng", GalleryLibrary.Meta(rating = 4, keywords = listOf("street")))
        assertTrue(packet.contains("xmp:Rating=\"4\""))
        assertTrue(packet.contains("street"))
    }

    @Test
    fun galleryDayKey_isUtcDate() {
        assertTrue(GalleryLibrary.dayKey(1_724_198_400L).matches(Regex("""\d{4}-\d{2}-\d{2}""")))
    }
}
