plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.detekt)
    alias(libs.plugins.kover)
}

android {
    namespace = "dev.pointandshoot.capture"
    compileSdk = 37

    defaultConfig {
        minSdk = 28
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

detekt {
    buildUponDefaultConfig = true
    allRules = false
    parallel = true
    config.from(files("$rootDir/config/detekt/detekt.yml"))
    baseline = file("$rootDir/config/detekt/baseline.xml")
}

kover {
    reports {
        filters {
            includes {
                classes(
                    "dev.pointandshoot.Dng*",
                    "dev.pointandshoot.Bracket*",
                    "dev.pointandshoot.TiffDng*",
                    "dev.pointandshoot.TiffExif*",
                    "dev.pointandshoot.TiffIfd*",
                )
            }
        }
    }
}

dependencies {
    implementation(project(":pns-core"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.exifinterface)
    testImplementation(libs.junit)
    testImplementation(libs.org.json)
}
