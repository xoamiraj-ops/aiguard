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
        versionCode = 3
        versionName = "1.2.0"
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
