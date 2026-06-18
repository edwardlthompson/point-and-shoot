package dev.pointandshoot.fleet

import org.json.JSONObject

/** Hub / probe attach when [FleetDeviceMatrix.withCatalogIfMissing] stays module-pure. */
fun attachFleetCapabilityCatalogIfMissing(root: JSONObject): JSONObject =
    if (root.has(FleetDeviceMatrix.KEY_CAPABILITY_CATALOG)) {
        root
    } else {
        CameraCapabilityCatalogBuilder.attachTo(root)
    }
