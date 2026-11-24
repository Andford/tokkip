// Tokkip Fit: exercise with Tokkip by your side (docs/FIT-PLAN.md). Its own app
// (mx.loperstack.tokkipfit), reading Health Connect, sharing Tokkip's drawing,
// shapes and sounds through :shared.

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "mx.loperstack.tokkipfit"
    compileSdk = 35

    defaultConfig {
        applicationId = "mx.loperstack.tokkipfit"
        // Health Connect: built into Android 14, an app from the Play Store before.
        minSdk = 31
        targetSdk = 35
        versionCode = 3
        versionName = "0.5.0"
    }

    // The same stable key as Tokkip (.github/workflows/android.yml).
    signingConfigs {
        create("stable") {
            val path = System.getenv("TOKKIP_KEYSTORE")
            if (path != null && file(path).exists()) {
                storeFile = file(path)
                storePassword = System.getenv("TOKKIP_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("TOKKIP_KEY_ALIAS") ?: "tokkip"
                keyPassword = System.getenv("TOKKIP_KEYSTORE_PASSWORD")
            }
        }
    }

    buildTypes {
        debug {
            if (System.getenv("TOKKIP_KEYSTORE") != null) signingConfig = signingConfigs.getByName("stable")
        }
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = if (System.getenv("TOKKIP_KEYSTORE") != null) signingConfigs.getByName("stable")
            else signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
    }
    lint {
        checkReleaseBuilds = false
        abortOnError = false
    }
}

dependencies {
    implementation(project(":shared"))
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.glance:glance-appwidget:1.1.1")
    // 1.1.0-alpha12 (1.1.0 needs compileSdk 36): records need a recording method (Metadata.activelyRecorded…).
    implementation("androidx.health.connect:connect-client:1.1.0-alpha12")

    testImplementation("junit:junit:4.13.2")
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}
