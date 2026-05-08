plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

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
        // Allows `assembleRelease` without a separate keystore (debug key; internal / probe builds only).
        create("releaseDebugKey") {
            initWith(signingConfigs.getByName("debug"))
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("releaseDebugKey")
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
        // Avoid AGP lint JVM crashes on some JDK/tooling combos during release vital lint.
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
}
