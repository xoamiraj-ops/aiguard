plugins {
    id("com.android.application")
}

android {
    namespace = "com.arzban.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.arzban.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 2
        versionName = "1.1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
