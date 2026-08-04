plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.android.test) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.androidx.baselineprofile) apply false
    alias(libs.plugins.detekt) apply false
    alias(libs.plugins.kover) apply false
    alias(libs.plugins.paparazzi) apply false
}

// Gradle lockfile Trivy scan surfaces Netty CVEs on old tool transitive versions (not shipped in APK).
subprojects {
    configurations.configureEach {
        resolutionStrategy.eachDependency {
            if (requested.group == "io.netty") {
                useVersion("4.1.136.Final")
                because("Align Netty for Gradle tool transitive CVEs (security-scan lockfile gate; CVE-2026-56819+)")
            }
            if (requested.group == "org.bouncycastle") {
                useVersion("1.84")
                because("Align BouncyCastle for Gradle tool transitive CVEs (security-scan lockfile gate)")
            }
        }
    }
}
