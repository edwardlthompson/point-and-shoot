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
                useVersion("4.1.137.Final")
                because("Align Netty for Gradle tool transitive CVEs (security-scan lockfile gate; CVE-2026-75595)")
            }
            if (requested.group == "org.bouncycastle") {
                useVersion("1.85")
                because("Align BouncyCastle for Gradle tool transitive CVEs (security-scan lockfile gate; CVE-2026-8763/13506)")
            }
            if (requested.group == "com.google.guava" && requested.name == "guava") {
                // Bare 33.7.2 is not published; keep -jre vs -android classifiers.
                val aligned =
                    if (requested.version.orEmpty().endsWith("-jre")) "33.7.2-jre" else "33.7.2-android"
                useVersion(aligned)
                because("Align Guava for Trivy dependency scan (CVE-2026-102554)")
            }
        }
    }
}
