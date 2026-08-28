plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.acidicx.fusedlocationtest"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.acidicx.fusedlocationtest"
        minSdk = 31
        targetSdk = 34
        versionCode = 3
        versionName = "v3"
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

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation("com.google.android.gms:play-services-location:21.3.0")
}
