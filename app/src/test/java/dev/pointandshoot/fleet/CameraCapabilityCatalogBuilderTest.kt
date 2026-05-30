package dev.pointandshoot.fleet

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import dev.pointandshoot.Feature

class CameraCapabilityCatalogBuilderTest {

    @Test
    fun registry_meetsM18RowTarget() {
        assertTrue(
            "M18 catalog target >= 165 distinct rows (got ${CameraCapabilityCatalog.registry.size})",
            CameraCapabilityCatalog.registry.size >= 165,
        )
    }

    @Test
    fun catalogVersion_isV3() {
        assertEquals(3, CameraCapabilityCatalog.CATALOG_VERSION)
    }

    @Test
    fun buildFromFixture_hasCatalogRows() {
        val json = javaClass.getResource("/fleet_matrix_gate_minimal.json")!!.readText()
        val root = JSONObject(json)
        val catalog = CameraCapabilityCatalogBuilder.buildFromMatrix(root)
        assertTrue(catalog.length() >= CameraCapabilityCatalog.registry.size)
    }

    @Test
    fun attachTo_addsCapabilityCatalogKey() {
        val json = javaClass.getResource("/fleet_matrix_gate_minimal.json")!!.readText()
        val root = JSONObject(json)
        val attached = CameraCapabilityCatalogBuilder.attachTo(root)
        assertTrue(attached.has(FleetDeviceMatrix.KEY_CAPABILITY_CATALOG))
        assertEquals(CameraCapabilityCatalog.CATALOG_VERSION, attached.getInt(FleetDeviceMatrix.KEY_CATALOG_VERSION))
    }

    @Test
    fun rawGate_reflectsFeatureGates() {
        val json = javaClass.getResource("/fleet_matrix_gate_minimal.json")!!.readText()
        val root = JSONObject(json)
        val raw = CameraCapabilityCatalogBuilder.evaluatedRows(root).first { it.row.id == "raw.dng" }
        assertTrue(raw.deviceSupported)
        assertEquals(true, raw.sessionOk)
    }

    @Test
    fun faceGate_camera3_notAdvertised() {
        val json = javaClass.getResource("/fleet_matrix_gate_minimal.json")!!.readText()
        val root = JSONObject(json)
        val face = CameraCapabilityCatalogBuilder.evaluatedRows(root).first { it.row.id == "face.detect" }
        assertNotNull(face.detail)
    }

    @Test
    fun everyCapabilityGateFeature_mapsToCatalogId() {
        for (feature in Feature.entries) {
            assertNotNull(CameraCapabilityCatalogBuilder.catalogIdForGateFeature(feature))
        }
    }

    @Test
    fun summaryMarkdown_rendersHeader() {
        val json = javaClass.getResource("/fleet_matrix_gate_minimal.json")!!.readText()
        val root = CameraCapabilityCatalogBuilder.attachTo(JSONObject(json))
        val md = FleetCapabilitySummaryMarkdown.render(root)
        assertTrue(md.contains("# Point & Shoot"))
        assertTrue(md.contains("Feature catalog"))
        assertTrue(md.contains("fleet_device_matrix.json"))
    }
}
