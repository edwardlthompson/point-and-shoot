import com.android.build.gradle.BaseExtension
import java.io.File
import java.util.Properties
import java.security.MessageDigest
import java.net.URI

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.detekt)
    alias(libs.plugins.androidx.baselineprofile)
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
    // Pin NDK for reproducible CMake builds (matches scripts/pns_install_ndk.ps1 default).
    ndkVersion = "26.3.11579264"

    defaultConfig {
        applicationId = "dev.pointandshoot"
        minSdk = 28
        targetSdk = 36
        versionCode = 14005
        versionName = "0.14.0-beta.5"
        // Legacy target: app hit ClassNotFound on large Compose entrypoints when they landed in
        // secondary dex. Keep multidex explicitly enabled to ensure all classesN.dex are loaded.
        multiDexEnabled = true

        ndk {
            // Device (arm64) + emulator (x86_64). Omit 32-bit ABIs to keep CI/APK lean.
            abiFilters += listOf("arm64-v8a", "x86_64")
        }

        externalNativeBuild {
            cmake {
                arguments += listOf("-DANDROID_STL=c++_shared")
            }
        }
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
            isMinifyEnabled = true
            isShrinkResources = true
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
        // Release shrinking uses R8 + shrinkResources (see `buildTypes.release`). Native `.so`
        // packaging defaults trade APK zip size vs mmap install — do not toggle
        // `jniLibs.useLegacyPackaging` without measuring and checking 16 KB page-size guidance.
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    externalNativeBuild {
        cmake {
            path = rootProject.file("native/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    lint {
        checkReleaseBuilds = true
        warningsAsErrors = false
        baseline = file("lint-baseline.xml")
    }

    baselineProfile {
        // Generated `baseline-prof.txt` lives in src/main/ (see :baselineprofile:generateBaselineProfile).
        saveInSrc = true
    }
}

detekt {
    buildUponDefaultConfig = true
    allRules = false
    parallel = true
    config.from(files("$rootDir/config/detekt/detekt.yml"))
    baseline = file("$rootDir/config/detekt/baseline.xml")
}

dependencies {
    baselineProfile(project(":baselineprofile"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.profileinstaller)
    implementation("androidx.multidex:multidex:2.0.1")

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    // Apache-2.0 Material Symbols–compatible glyphs (BOM controls version).
    implementation(libs.androidx.compose.material.icons.extended)

    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.extensions)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)
    implementation(libs.google.zxing.core)
    implementation(libs.androidx.graphics.core)
    implementation(libs.androidx.exifinterface)
    implementation("androidx.documentfile:documentfile:1.1.0")
    // Face HUD fallback when Camera2 STATISTICS_FACES is empty (common on some OEM preview streams).
    implementation(libs.google.mlkit.face.detection)

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
// `bundledLutSpecs` lists pinned upstream `.cube` / `.spi3d` blobs (ACES OCIO
// configs). Filmic Blender upstream ships mostly `.spi1d` / large false-colour
// `.spi3d`; optional Filmic asset wiring remains a follow-up when 1D LUT import
// lands. Run `./gradlew :app:downloadBundledLutsDryRun` to print URLs + hashes.
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
    /** Output filename suffix after `${name}.` — OCIO ships `.cube` or Sony `.spi3d`. */
    val fileExtension: String = "cube",
)

/** Pinned commit on https://github.com/colour-science/OpenColorIO-Configs (archived OCIO ACES configs; Apache-2.0). */
private val ocioConfigsCommit = "3af87f1d70ca3ea2a19cfd431b80de8014a00763"

private val ocioConfigsRaw =
    "https://raw.githubusercontent.com/colour-science/OpenColorIO-Configs/$ocioConfigsCommit"

/** SHA-256 verified host-side (`certutil` / Gradle task). Build-time fetch only — see LICENSES.md. */
val bundledLutSpecs: List<BundledLutSpec> =
    listOf(
        BundledLutSpec(
            name = "aces-rrt-v011-srgb",
            spdx = "Apache-2.0",
            url = "$ocioConfigsRaw/aces_0.1.1/luts/rrt/rrt_v0_1_1_sRGB.spi3d",
            sha256 = "5091538e3d9d9b201fd4fc1f3b38a625f4138d1fb1764311248eaf768bfaecab",
            sourceUrl = "https://github.com/colour-science/OpenColorIO-Configs",
            licenseText =
                "Apache License 2.0 — Academy of Motion Picture Arts and Sciences (AMPAS), Sony Pictures Imageworks, " +
                    "and contributors. Full text: upstream LICENSE.AMPAS in colour-science/OpenColorIO-Configs.",
            fileExtension = "spi3d",
        ),
        BundledLutSpec(
            name = "alexa-logc-video-nuke1d",
            spdx = "Apache-2.0",
            url = "$ocioConfigsRaw/aces_0.7.1/luts/AlexaV3_K1S1_LogC2Video_EE_nuke1d.cube",
            sha256 = "320004345d44b6a63152b6762c70e39ffe0d0863a05515644c366f50783b4b1f",
            sourceUrl = "https://github.com/colour-science/OpenColorIO-Configs",
            licenseText =
                "Apache License 2.0 — Academy of Motion Picture Arts and Sciences (AMPAS), Sony Pictures Imageworks, " +
                    "and contributors. Full text: upstream LICENSE.AMPAS in colour-science/OpenColorIO-Configs.",
            fileExtension = "cube",
        ),
    )

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
            val cached = cacheDir.resolve("${spec.name}.${spec.fileExtension}")
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
            val assetLeaf = "${spec.name}.${spec.fileExtension}"
            cached.copyTo(leafDir.resolve(assetLeaf), overwrite = true)
            leafDir.resolve("LICENSE.txt").writeText(spec.licenseText)
            leafDir.resolve("SOURCE.txt").writeText("Source: ${spec.sourceUrl}\nDirect URL: ${spec.url}\n")
            leafDir.resolve("SHA256.txt").writeText("${spec.sha256}  $assetLeaf\n")
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

// SVT-AV1 emits Ninja archive rules that invoke `llvm-ar`/`llvm-ranlib` without a path; Windows PATH does not
// include the NDK LLVM bin dir for the Ninja subprocess. Patch generated Ninja files after configure.
fun pnsNdkLlvmTools(project: Project): Pair<String, String> {
    val props = Properties().apply {
        val lp = project.rootProject.file("local.properties")
        if (lp.exists()) lp.inputStream().use { load(it) }
    }
    val sdkRoot = props.getProperty("sdk.dir")?.let { File(it) }
        ?: error("local.properties must define sdk.dir")
    val hostPrebuilt = when {
        System.getProperty("os.name").contains("Windows", ignoreCase = true) -> "windows-x86_64"
        System.getProperty("os.name").contains("Mac", ignoreCase = true) -> "darwin-x86_64"
        else -> "linux-x86_64"
    }
    val ndkVer = project.extensions.getByType(BaseExtension::class.java).ndkVersion
    val llvmDir = File(sdkRoot, "ndk/$ndkVer/toolchains/llvm/prebuilt/$hostPrebuilt/bin")
    val llvmExe = if (System.getProperty("os.name").contains("Windows", ignoreCase = true)) ".exe" else ""
    val llvmAr = File(llvmDir, "llvm-ar$llvmExe").absolutePath.replace("\\", "/")
    val llvmRanlib = File(llvmDir, "llvm-ranlib$llvmExe").absolutePath.replace("\\", "/")
    return Pair(llvmAr, llvmRanlib)
}

fun pnsPatchNinjaLlvm(project: Project, cxxRoot: File, llvmAr: String, llvmRanlib: String) {
    if (!cxxRoot.isDirectory) return
    project.fileTree(cxxRoot).matching {
        include("**/build.ninja")
        include("**/rules.ninja")
    }.forEach { f ->
        var s = f.readText()
        val before = s
        s = s.replace(Regex("""(?<![\\w/])llvm-ar\b"""), "\"$llvmAr\"")
        s = s.replace(Regex("""(?<![\\w/])llvm-ranlib\b"""), "\"$llvmRanlib\"")
        if (s != before) {
            f.writeText(s)
        }
    }
}

afterEvaluate {
    val cxxRoot = layout.projectDirectory.dir(".cxx").asFile
    val (llvmAr, llvmRanlib) = pnsNdkLlvmTools(project)
    tasks.configureEach {
        if (!name.matches(Regex("""configureCMake(Debug|RelWithDebInfo)\[.*\]"""))) return@configureEach
        doLast {
            pnsPatchNinjaLlvm(project, cxxRoot, llvmAr, llvmRanlib)
        }
    }
}
