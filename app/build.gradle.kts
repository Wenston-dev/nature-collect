plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

android {
    namespace = "io.github.wgd753.naturecollect"
    compileSdk = 36

    defaultConfig {
        applicationId = "io.github.wgd753.naturecollect"
        minSdk = 26
        targetSdk = 36
        versionCode = 10
        versionName = "0.7.1"

        ndk {
            // Phones from the last several years are all 64-bit ARM; dropping the
            // other ABIs roughly halves the APK (ONNX Runtime and MapLibre are native).
            abiFilters += "arm64-v8a"
        }
    }

    signingConfigs {
        getByName("debug") {
            // CI creates one debug-style key (standard debug passwords) and caches
            // it, so each new APK installs over the previous one.
            System.getenv("SIGNING_KEYSTORE")?.let { storeFile = file(it) }
        }
    }

    buildTypes {
        release {
            // Signed with the debug key. Personal use, not for a store.
            signingConfig = signingConfigs.getByName("debug")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
    }
    androidResources {
        // The SAM models are memory-mapped by ONNX Runtime; keep them uncompressed.
        noCompress += "onnx"
    }
}

kotlin {
    jvmToolchain(17)
}

ksp {
    arg("room.generateKotlin", "true")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.exifinterface)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.onnxruntime.android)
    implementation(libs.maplibre.android)
    implementation(libs.okhttp)
    implementation(libs.coil.compose)
    debugImplementation(libs.androidx.compose.ui.tooling)
    testImplementation(libs.junit)
}
