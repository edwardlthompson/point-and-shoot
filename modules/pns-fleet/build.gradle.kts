plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.detekt)
    alias(libs.plugins.kover)
}

android {
    namespace = "dev.pointandshoot.fleet"
    compileSdk = 37

    defaultConfig {
        minSdk = 28
        buildConfigField("String", "LEADERBOARD_INGEST_URL", "\"\"")
        buildConfigField("int", "VERSION_CODE", "1")
    }

    buildFeatures {
        buildConfig = true
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
                packages("dev.pointandshoot.fleet")
            }
        }
    }
}

dependencies {
    implementation(project(":pns-core"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.org.json)
    testImplementation(libs.junit)
    testImplementation(libs.org.json)
    testImplementation(project(":pns-capture"))
}
