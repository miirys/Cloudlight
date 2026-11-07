import app.cloudlight.buildlogic.WebRtcTextureDeliveryFactory
import com.android.build.api.instrumentation.FramesComputationMode
import com.android.build.api.instrumentation.InstrumentationScope

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "app.cloudlight.tv"
    compileSdk = 35

    defaultConfig {
        applicationId = "app.cloudlight.tv"
        minSdk = 28
        targetSdk = 34
        versionCode = 13
        versionName = "1.1.9"
    }

    // One fixed key lets every new version install over the last one without losing the
    // sign-in. CI passes it in from repository secrets; without them the build falls back to
    // the debug key, which changes from machine to machine.
    val keystore = System.getenv("CLOUDLIGHT_KEYSTORE")?.let(::file)?.takeIf { it.exists() }
    signingConfigs {
        if (keystore != null) {
            create("release") {
                storeFile = keystore
                storePassword = System.getenv("CLOUDLIGHT_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("CLOUDLIGHT_KEY_ALIAS") ?: "cloudlight"
                keyPassword = System.getenv("CLOUDLIGHT_KEY_PASSWORD") ?: System.getenv("CLOUDLIGHT_KEYSTORE_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

// WebRTC's texture path resets the SurfaceTexture size and posts two messages for every
// decoded frame, which drops frames at 120 fps. buildSrc patches it to skip unchanged
// values and to run the decoder texture thread at display priority.
androidComponents.onVariants { variant ->
    variant.instrumentation.transformClassesWith(
        WebRtcTextureDeliveryFactory::class.java,
        InstrumentationScope.ALL,
    ) {}
    variant.instrumentation.setAsmFramesComputationMode(
        FramesComputationMode.COMPUTE_FRAMES_FOR_INSTRUMENTED_METHODS,
    )
}

dependencies {
    val bom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(bom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.animation:animation")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("io.coil-kt:coil-compose:2.7.0")
    implementation("com.google.zxing:core:3.5.3")
    implementation("io.github.webrtc-sdk:android:144.7559.15")
}
