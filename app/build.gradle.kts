plugins {
    id("com.android.application")
}

android {
    namespace = "com.arzban.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.arzban.app"
        minSdk = 26
        targetSdk = 36
        versionCode = 4
        versionName = "1.3.0"
    }

    signingConfigs {
        create("release") {
            val ks = System.getenv("ARZBAN_KEYSTORE")
            if (!ks.isNullOrBlank()) {
                storeFile = file(ks)
                storePassword = System.getenv("ARZBAN_STORE_PASS")
                keyAlias = System.getenv("ARZBAN_KEY_ALIAS") ?: "arzban"
                keyPassword = System.getenv("ARZBAN_KEY_PASS")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            isDebuggable = false
            signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
