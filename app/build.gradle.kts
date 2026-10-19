plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.parsban.callai"
    compileSdk = 36

    defaultConfig {
        val preview = providers.gradleProperty("avakavPreview").orNull == "true"
        applicationId = if (preview) "com.avakav.preview" else "com.avakav.personal"
        manifestPlaceholders["appLabel"] = if (preview) "آواکاو • تست ۳" else "آواکاو"
        minSdk = 33
        targetSdk = 36
        versionCode = 4
        versionName = "0.3.0-local"
        ndk { abiFilters += listOf("arm64-v8a") }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            isDebuggable = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlin { jvmToolchain(17) }
}

dependencies {
    implementation(project(":whisperlib"))
    implementation(project(":llamalib"))
    implementation("androidx.work:work-runtime:2.10.5")
    implementation("androidx.documentfile:documentfile:1.0.1")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
}

