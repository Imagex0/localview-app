plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.localview.browser"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.localview.browser"
        minSdk = 26
        targetSdk = 35
        versionCode = 2
        versionName = "0.2.0"
        vectorDrawables { useSupportLibrary = true }
    }

    signingConfigs {
        // Fixed debug key: every CI build shares one signature, so updates
        // install over each other instead of conflicting. Debug key only —
        // never used for release. Passwords are the public Android convention.
        named("debug") {
            storeFile = file("debug.keystore")
            storePassword = "android"
            keyAlias = "localview-debug"
            keyPassword = "android"
            storeType = "pkcs12"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            applicationIdSuffix = ".debug"
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    buildFeatures { viewBinding = true }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("com.google.android.material:material:1.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
}
