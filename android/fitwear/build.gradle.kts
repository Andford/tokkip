// Tokkip Fit on the watch: the routines from your wrist, with Tokkip doing each
// move and your heart rate live. Same id and key as the phone app (the Data
// Layer only links apps that match): finished workouts go to the phone, which
// writes them to Health Connect.

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "mx.loperstack.tokkipfit.wear"
    compileSdk = 35

    defaultConfig {
        applicationId = "mx.loperstack.tokkipfit"
        minSdk = 33
        targetSdk = 35
        versionCode = 200006
        versionName = "0.6.0"
    }

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
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.wear.compose:compose-material:1.4.0")
    implementation("androidx.wear.compose:compose-foundation:1.4.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.wear.tiles:tiles:1.4.1")
    implementation("androidx.wear.protolayout:protolayout:1.2.1")
    implementation("androidx.wear.watchface:watchface-complications-data-source-ktx:1.2.1")
    implementation("com.google.guava:guava:33.3.1-android")
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}
