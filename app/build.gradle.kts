plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.statussaver"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.statuskeeper.app"
        minSdk = 23
        targetSdk = 34
        versionCode = 2
        versionName = "2.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        setProperty("archivesBaseName", "Status-Saver-2026")
    }

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
    applicationVariants.all {
        outputs.all {
            val output = this as com.android.build.gradle.internal.api.BaseVariantOutputImpl
            val versionName = defaultConfig.versionName
            output.outputFileName = "Status-Saver-2026-v${versionName}.apk"
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

    // DocumentFile - For SAF (Storage Access Framework) - ADDED FOR ANDROID 11+ SUPPORT
    implementation("androidx.documentfile:documentfile:1.0.1")

    // Glide for image loading - Latest
    implementation("com.github.bumptech.glide:glide:4.16.0")

    // ========== YANDEX MOBILE ADS ONLY (For RuStore) ==========
    // Yandex Mobile Ads SDK 7.16.1 - Latest version
    implementation("com.yandex.android:mobileads:7.16.1")
                                                                           // NO Unity Ads adapter - Pure Yandex only!

    // Coroutines - Latest
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    // ExoPlayer - Latest
    implementation("androidx.media3:media3-exoplayer:1.5.0")
    implementation("androidx.media3:media3-ui:1.5.0")
}
