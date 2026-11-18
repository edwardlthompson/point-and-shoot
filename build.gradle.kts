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

// Gradle lockfile Trivy scan surfaces CVEs on old tool / transitive versions.
// Prefer flavor-correct Maven coordinates (Guava has no unsuffixed 33.7.2 artifact).
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
                // Maven Central publishes 33.7.2-jre / 33.7.2-android only — there is no
                // unsuffixed 33.7.2 artifact. Forcing the bare version left AGP UTP,
                // runtime, lint, and unit-test lockfile entries on 32.0.1-jre /
                // 33.3.1-* / 33.5.0-jre, which Trivy flags as CVE-2026-102554.
                val current = requested.version.orEmpty()
                val patched = if (current.endsWith("-jre")) "33.7.2-jre" else "33.7.2-android"
                useVersion(patched)
                because("Align Guava for Trivy dependency scan (CVE-2026-102554); preserve -jre/-android flavor")
            }
        }
    }
}
