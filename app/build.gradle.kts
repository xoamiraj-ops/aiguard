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
        versionCode = 5
        versionName = "1.4.0"
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

dependencies {
    implementation("androidx.work:work-runtime:2.12.0")
    implementation("org.jsoup:jsoup:1.23.2")
}
