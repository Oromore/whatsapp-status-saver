plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.statussaver"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.statussaver.whatsapp"
        minSdk = 23  // Updated from 21 to 23 (required by Unity Ads SDK 4.14+)
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // This sets the app name as it appears on the device
        setProperty("archivesBaseName", "WhatsApp-Status-Saver")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }

        debug {
            // Optional: different name for debug builds
            applicationIdSuffix = ".debug"
        }
    }

    // Custom APK naming - clean filename without build type
    applicationVariants.all {
        outputs.all {
            val output = this as com.android.build.gradle.internal.api.BaseVariantOutputImpl
            val versionName = defaultConfig.versionName
            // Format: WhatsApp-Status-Saver-v1.0.apk (no debug/release suffix)
            output.outputFileName = "WhatsApp-Status-Saver-v${versionName}.apk"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    kotlinOptions {
        jvmTarget = "1.8"
    }

    buildFeatures {
        viewBinding = true
    }
}

dependencies {
    // AndroidX Core - Latest
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.constraintlayout:constraintlayout:2.2.0")

    // Lifecycle - Latest
    implementation("androidx.lifecycle:lifecycle-process:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")

    // RecyclerView - Latest
    implementation("androidx.recyclerview:recyclerview:1.3.2")

    // Glide for image loading - Latest
    implementation("com.github.bumptech.glide:glide:4.16.0")

    // Unity Ads - Updated to 4.16.3
    implementation("com.unity3d.ads:unity-ads:4.16.3")

    // Coroutines - Latest
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    // ExoPlayer - Latest
    implementation("androidx.media3:media3-exoplayer:1.5.0")
    implementation("androidx.media3:media3-ui:1.5.0")
}
