plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "mx.loperstack.tokkip"
    compileSdk = 35

    defaultConfig {
        applicationId = "mx.loperstack.tokkip"
        minSdk = 31
        targetSdk = 35
        versionCode = 31
        versionName = "2.0.1"
    }

    // One stable key for every CI build, so a new APK installs over the old one
    // and keeps your tasks. The keystore comes from the repo's secrets
    // (.github/workflows/android.yml); without it the usual debug key is used.
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
        // The build you install: R8-optimised, so Compose runs smooth (a debug
        // build is several times slower, especially on the watch).
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
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
    }
    // Personal builds: lint runs on its own, it does not stop a release.
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
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.wear:wear-remote-interactions:1.1.0")
    implementation("androidx.glance:glance-appwidget:1.1.1")
    implementation("androidx.car.app:app:1.7.0")
    implementation("com.google.android.gms:play-services-auth:21.2.0")
    implementation("androidx.health.connect:connect-client:1.1.0-alpha11")
    implementation("com.google.guava:guava:33.3.1-android")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
}
