// Tokkip's watch face, in the Watch Face Format (resource-only: no code, so it
// draws with almost no battery). The XML and images come from
// tools/make_watchface.py.
plugins {
    id("com.android.application")
}

android {
    namespace = "mx.loperstack.tokkip.watchface"
    compileSdk = 35

    defaultConfig {
        applicationId = "mx.loperstack.tokkip.watchface"
        // Watch Face Format 2: Wear OS 5 and up (the Pixel Watch 4 runs Wear OS 6).
        minSdk = 34
        targetSdk = 34
        versionCode = 5
        versionName = "2.0.0"
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
        release {
            isMinifyEnabled = false
            isShrinkResources = false
            signingConfig = if (System.getenv("TOKKIP_KEYSTORE") != null) signingConfigs.getByName("stable")
            else signingConfigs.getByName("debug")
        }
    }
}
