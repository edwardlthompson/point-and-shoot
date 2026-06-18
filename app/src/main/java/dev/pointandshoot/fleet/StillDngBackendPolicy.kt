package dev.pointandshoot.fleet

import dev.pointandshoot.StillDngBackend

import dev.pointandshoot.DngSaveBisectState

object StillDngBackendPolicy {

    fun active(): StillDngBackend =
        DngSaveBisectState.stillDngBackendOverride ?: LegacyFleetPolicy.stillDngBackend()

    fun usesFrameworkDngCreator(backend: StillDngBackend): Boolean =
        backend != StillDngBackend.ALTREFERENCEAPP_NATIVE
}
