plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.statussaver"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.statussaver.whatsapp"
        minSdk = 21
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
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

// Custom APK naming - Gradle 8.0+ compatible
androidComponents {
    onVariants { variant ->
        variant.outputs.forEach { output ->
            output.outputFileName.set("WhatsApp-Status-Saver-${variant.name}-1.0.apk")
        }
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

    // Unity Ads - Latest
    implementation("com.unity3d.ads:unity-ads:4.12.2")

    // Coroutines - Latest
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    // ExoPlayer - Latest
    implementation("androidx.media3:media3-exoplayer:1.5.0")
    implementation("androidx.media3:media3-ui:1.5.0")
}
