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

    buildTypes {
        release {
            isMinifyEnabled = false
            isDebuggable = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
