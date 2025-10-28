plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.statussaver"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.statussaver"  // CHANGED: completely generic
        minSdk = 23
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // CHANGED: Updated app name
        setProperty("archivesBaseName", "Status-Saver")
    }

    // Signing configuration for release builds
    signingConfigs {
        create("release") {
            storeFile = file("../release-key.jks")
            storePassword = "Hegira@005"
            keyAlias = "statussaver-key"
            keyPassword = "Hegira@005"
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }

        debug {
            applicationIdSuffix = ".debug"
        }
    }

    // CHANGED: Updated APK naming
    applicationVariants.all {
        outputs.all {
            val output = this as com.android.build.gradle.internal.api.BaseVariantOutputImpl
            val versionName = defaultConfig.versionName
            // Format: Status-Saver-v1.0.apk
            output.outputFileName = "Status-Saver-v${versionName}.apk"
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
