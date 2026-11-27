plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

android {
    namespace = "com.wkcw.scanify"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.wkcw.scanify"
        minSdk = 29
        targetSdk = 37
        // Each CI run gets a higher versionCode so an install can update in place.
        versionCode = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: 1
        versionName = "1.0"
        // OpenCV ships native libs per ABI; arm64 only keeps the APK small.
        ndk { abiFilters += listOf("arm64-v8a") }
    }

    // Compress native libs to keep the APK well under Telegram's upload limit.
    packaging {
        jniLibs {
            useLegacyPackaging = true
        }
    }

    // CI supplies a dedicated keystore so every build keeps the same signature.
    val signingKeystore = System.getenv("SCANIFY_KEYSTORE_FILE")?.let(::file)
    signingConfigs {
        if (signingKeystore != null) {
            create("ci") {
                storeFile = signingKeystore
                storePassword = System.getenv("SCANIFY_STORE_PASSWORD")
                keyAlias = System.getenv("SCANIFY_KEY_ALIAS")
                keyPassword = System.getenv("SCANIFY_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        debug {
            if (signingKeystore != null) signingConfig = signingConfigs.getByName("ci")
        }
        release {
            isMinifyEnabled = false
            if (signingKeystore != null) signingConfig = signingConfigs.getByName("ci")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    buildFeatures {
        compose = true
    }
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")

    val couiVersion = "2.1.0"
    implementation("io.github.suqi8.coui.kmp:coui-ui-android:$couiVersion")
    implementation("io.github.suqi8.coui.kmp:coui-preference-android:$couiVersion")
    implementation("io.github.suqi8.coui.kmp:coui-blur-android:$couiVersion")
    implementation("io.github.suqi8.coui.kmp:coui-icons-android:$couiVersion")
    implementation("io.github.suqi8.coui.kmp:coui-squircle-android:$couiVersion")
    implementation("io.github.suqi8.coui.kmp:coui-nav-android:$couiVersion")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-core:1.11.0")

    val camerax = "1.3.4"
    implementation("androidx.camera:camera-core:$camerax")
    implementation("androidx.camera:camera-camera2:$camerax")
    implementation("androidx.camera:camera-lifecycle:$camerax")
    implementation("androidx.camera:camera-view:$camerax")

    implementation("org.opencv:opencv:4.9.0")
}
