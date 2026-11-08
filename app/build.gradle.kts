plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "dev.kognos.databar"
    compileSdk = 34

    defaultConfig {
        applicationId = "dev.kognos.databar"
        minSdk = 29
        targetSdk = 34
        versionCode = 34
        versionName = "8.2"
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
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
}
