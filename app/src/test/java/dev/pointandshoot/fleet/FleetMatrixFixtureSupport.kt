package dev.pointandshoot.fleet

import java.io.File
import org.json.JSONObject

/** Loads fleet matrix JSON from repo `tests/fixtures/fleet_matrix/` or test classpath. */
internal object FleetMatrixFixtureSupport {
    fun loadRepoFixture(fileName: String): JSONObject {
        var dir = File(System.getProperty("user.dir") ?: error("no user.dir"))
        while (true) {
            val candidate = File(dir, "tests/fixtures/fleet_matrix/$fileName")
            if (candidate.isFile) return JSONObject(candidate.readText())
            val parent = dir.parentFile ?: error("Missing tests/fixtures/fleet_matrix/$fileName")
            dir = parent
        }
    }

    fun loadClasspath(resourceName: String): JSONObject {
        val stream =
            checkNotNull(FleetMatrixFixtureSupport::class.java.getResourceAsStream("/$resourceName")) {
                "Missing classpath fixture $resourceName"
            }
        return JSONObject(stream.bufferedReader().readText())
    }
}
