import java.util.Properties
import java.security.MessageDigest
import java.net.URI

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

// Resolve a real release-signing key from (in order):
//   1. Environment variables (CI):
//        ANDROID_KEYSTORE_PATH (file path to the .jks/.keystore on disk)
//        ANDROID_KEYSTORE_PASSWORD
//        ANDROID_KEY_ALIAS
//        ANDROID_KEY_PASSWORD
//   2. keystore.properties at the repo root (gitignored), with the same keys
//      (storeFile, storePassword, keyAlias, keyPassword).
// If neither is present, `assembleRelease` still works using the debug key
// (`releaseDebugKey`) so local probe builds don't require signing material.
data class ReleaseSigning(
    val storeFile: java.io.File,
    val storePassword: String,
    val keyAlias: String,
    val keyPassword: String,
    val source: String,
)

fun resolveReleaseSigning(): ReleaseSigning? {
    val envPath = System.getenv("ANDROID_KEYSTORE_PATH")
    val envStorePw = System.getenv("ANDROID_KEYSTORE_PASSWORD")
    val envAlias = System.getenv("ANDROID_KEY_ALIAS")
    val envKeyPw = System.getenv("ANDROID_KEY_PASSWORD")
    if (!envPath.isNullOrBlank() && !envStorePw.isNullOrBlank() && !envAlias.isNullOrBlank() && !envKeyPw.isNullOrBlank()) {
        val f = file(envPath)
        if (f.isFile) {
            return ReleaseSigning(f, envStorePw, envAlias, envKeyPw, "env")
        }
    }

    val propsFile = rootProject.file("keystore.properties")
    if (propsFile.isFile) {
        val p = Properties().apply { propsFile.inputStream().use { load(it) } }
        val storeFile = p.getProperty("storeFile")?.let { rootProject.file(it) }
        val storePassword = p.getProperty("storePassword")
        val keyAlias = p.getProperty("keyAlias")
        val keyPassword = p.getProperty("keyPassword")
        if (storeFile != null && storeFile.isFile && !storePassword.isNullOrBlank() && !keyAlias.isNullOrBlank() && !keyPassword.isNullOrBlank()) {
            return ReleaseSigning(storeFile, storePassword, keyAlias, keyPassword, "keystore.properties")
        }
    }
    return null
}

val releaseSigning: ReleaseSigning? = resolveReleaseSigning()

android {
    namespace = "dev.pointandshoot"
    compileSdk = 36

    defaultConfig {
        applicationId = "dev.pointandshoot"
        minSdk = 28
        targetSdk = 36
        versionCode = 1
        versionName = "0.0.0"
    }

    signingConfigs {
        // Fallback: allow `assembleRelease` without a separate keystore (debug key; internal / probe builds only).
        create("releaseDebugKey") {
            initWith(signingConfigs.getByName("debug"))
        }
        // Real release-signing config (env vars OR keystore.properties; both gitignored).
        create("release") {
            releaseSigning?.let { rs ->
                storeFile = rs.storeFile
                storePassword = rs.storePassword
                keyAlias = rs.keyAlias
                keyPassword = rs.keyPassword
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = if (releaseSigning != null) {
                logger.lifecycle("[pns] release signing source = ${releaseSigning.source}")
                signingConfigs.getByName("release")
            } else {
                logger.lifecycle("[pns] release signing source = none (falling back to debug key)")
                signingConfigs.getByName("releaseDebugKey")
            }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    lint {
        // `:app:lintDebug` is currently not runnable on this AGP 8.7.3 + Compose BOM
        // 2026.04.01 combo: multiple compose-lint detectors (`ComposableFlowOperator`,
        // `RememberInComposition`, `FrequentlyChangingValue`, ...) crash with
        // `IncompatibleClassChangeError` against the bundled Kotlin Analysis API. This
        // is a tooling/version-mismatch bug, not project code - tracked in
        // BUILD_PLAN.md \u00a70 "Known limitations". The toolchain gate
        // (`pns_verify_toolchain.ps1`) deliberately does NOT invoke lint today;
        // revisit after the next AGP / Compose-BOM bump.
        checkReleaseBuilds = false
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)

    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.graphics.core)

    debugImplementation(libs.androidx.compose.ui.tooling)

    // Pure-JVM unit tests for engine-agnostic helpers (BracketPlan, HighlightMeter,
    // formatTimecode). Tests live in app/src/test/java and run via :app:testDebugUnitTest.
    testImplementation(libs.junit)
    // Real org.json on the unit-test classpath so EncoderAttemptJsonAdapter.decode
    // can be tested against in-memory JSONObject fixtures (the Android stub jar
    // throws "Stub!" for org.json calls otherwise). MIT-licensed; covered in LICENSES.md.
    testImplementation(libs.org.json)
}

// ----------------------------------------------------------------------------
// downloadBundledLuts task: BUILD_PLAN \u00a77 "Build-time download" infrastructure.
//
// Fetches each upstream LUT to a build-cache dir, verifies SHA-256, and copies
// into `app/src/main/assets/luts/<spdx-folder>/<name>/` alongside the
// LICENSE.txt + SOURCE.txt + SHA256.txt sidecars. Skipped silently when the
// SHA-256 of the cached file already matches the expected value (idempotent;
// CI hits the cache after the first run on a given runner).
//
// The URL list is INTENTIONALLY EMPTY today - this milestone ships the
// infrastructure; the actual upstream URLs (ACES, Filmic, ...) will be wired
// in once the LICENSES.md "Bundled LUTs" table is finalized for those entries
// and a stable public mirror is selected. The task is registered so CI dry-
// runs can confirm it parses + executes (no-op when empty).
//
// Task graph:
//   :app:downloadBundledLuts             -> downloads + verifies + writes assets
//   :app:downloadBundledLutsDryRun       -> prints the URL list without fetching
//   :app:preBuild dependsOn downloadBundledLuts (when the URL list is non-empty)
// ----------------------------------------------------------------------------

data class BundledLutSpec(
    val name: String,
    val spdx: String,
    val url: String,
    val sha256: String,
    val sourceUrl: String,
    val licenseText: String,
)

val bundledLutSpecs: List<BundledLutSpec> = emptyList()
// To wire ACES (planned): add e.g.
//   BundledLutSpec(
//     name = "aces-srgb-to-acescct",
//     spdx = "Apache-2.0",
//     url = "https://github.com/AcademySoftwareFoundation/OpenColorIO-Configs/raw/<pinned-sha>/aces_1.0.3/luts/sRGB_to_ACEScct.cube",
//     sha256 = "<pinned-64-char-lowercase-hex>",
//     sourceUrl = "https://github.com/AcademySoftwareFoundation/OpenColorIO-Configs",
//     licenseText = "Apache License 2.0 - Copyright (c) Academy of Motion Picture Arts and Sciences",
//   )

fun verifySha256(file: java.io.File, expected: String): Boolean {
    if (!file.isFile) return false
    val md = MessageDigest.getInstance("SHA-256")
    file.inputStream().use { stream ->
        val buf = ByteArray(64 * 1024)
        while (true) {
            val n = stream.read(buf)
            if (n <= 0) break
            md.update(buf, 0, n)
        }
    }
    val actual = md.digest().joinToString("") { "%02x".format(it) }
    return actual.equals(expected.lowercase(), ignoreCase = false)
}

tasks.register("downloadBundledLuts") {
    group = "pns"
    description = "Download + SHA-256 verify each bundled LUT into app/src/main/assets/luts/. " +
        "No-op when bundledLutSpecs is empty."
    val cacheDir = layout.buildDirectory.dir("pns-lut-cache").get().asFile
    val assetsDir = file("src/main/assets/luts")
    val specs = bundledLutSpecs
    inputs.property("specCount", specs.size)
    inputs.property("specHash", specs.joinToString("|") { "${it.name}:${it.sha256}" })
    outputs.dir(assetsDir)
    doLast {
        if (specs.isEmpty()) {
            logger.lifecycle("[pns] downloadBundledLuts: no LUTs configured (bundledLutSpecs is empty); skipping.")
            return@doLast
        }
        cacheDir.mkdirs()
        for (spec in specs) {
            val cached = cacheDir.resolve("${spec.name}.cube")
            if (verifySha256(cached, spec.sha256)) {
                logger.lifecycle("[pns] cache hit: ${spec.name} (sha256 OK)")
            } else {
                logger.lifecycle("[pns] downloading: ${spec.name} <- ${spec.url}")
                URI(spec.url).toURL().openStream().use { input ->
                    cached.outputStream().use { out -> input.copyTo(out) }
                }
                if (!verifySha256(cached, spec.sha256)) {
                    val md = MessageDigest.getInstance("SHA-256")
                    cached.inputStream().use { s ->
                        val b = ByteArray(64 * 1024)
                        while (true) { val n = s.read(b); if (n <= 0) break; md.update(b, 0, n) }
                    }
                    val actual = md.digest().joinToString("") { "%02x".format(it) }
                    cached.delete()
                    throw GradleException(
                        "downloadBundledLuts: SHA-256 mismatch for ${spec.name}\n" +
                            "  expected: ${spec.sha256}\n" +
                            "  actual:   $actual\n" +
                            "  url:      ${spec.url}",
                    )
                }
                logger.lifecycle("[pns] downloaded + verified: ${spec.name}")
            }
            val leafDir = assetsDir.resolve("${spec.spdx}/${spec.name}")
            leafDir.mkdirs()
            cached.copyTo(leafDir.resolve("${spec.name}.cube"), overwrite = true)
            leafDir.resolve("LICENSE.txt").writeText(spec.licenseText)
            leafDir.resolve("SOURCE.txt").writeText("Source: ${spec.sourceUrl}\nDirect URL: ${spec.url}\n")
            leafDir.resolve("SHA256.txt").writeText("${spec.sha256}  ${spec.name}.cube\n")
        }
    }
}

tasks.register("downloadBundledLutsDryRun") {
    group = "pns"
    description = "Print the bundled-LUT URL list without downloading anything."
    val specs = bundledLutSpecs
    doLast {
        if (specs.isEmpty()) {
            logger.lifecycle("[pns] downloadBundledLutsDryRun: bundledLutSpecs is empty.")
        } else {
            specs.forEach { spec ->
                logger.lifecycle("[pns] ${spec.spdx}/${spec.name} sha256=${spec.sha256} url=${spec.url}")
            }
        }
    }
}

if (bundledLutSpecs.isNotEmpty()) {
    tasks.named("preBuild") { dependsOn("downloadBundledLuts") }
}
