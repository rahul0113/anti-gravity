plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.serialization")
}

android {
    namespace = "com.antigravity.vibecoder"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.antigravity.vibecoder"
        minSdk = 21
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    buildTypes {
        release {
            // O-2 FIX: Enable minification and resource shrinking — reduces APK size by ~50%
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
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
    buildFeatures {
        compose = true
    }
    composeOptions {
        // O-9 FIX: Updated to match Compose BOM 2024.06.00
        kotlinCompilerExtensionVersion = "1.5.14"
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("androidx.activity:activity-compose:1.8.2")
    
    // O-9 FIX: Updated Compose BOM from 2023.08.00 → 2024.06.00
    // Includes Compose 1.6.x perf improvements: ~20% faster first frame, LazyColumn fixes
    implementation(platform("androidx.compose:compose-bom:2024.06.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    // HTTP & SSH & JSON Integration
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.github.mwiede:jsch:0.2.17")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.2")
    // FIX: Replace the empty guava stub (9999.0-empty) with the real concurrent-futures library.
    // The empty stub contained zero class bytes, so profileinstaller crashed at runtime
    // trying to load ListenableFuture and AbstractResolvableFuture from base.apk.
    implementation("androidx.concurrent:concurrent-futures:1.1.0")
    implementation("androidx.concurrent:concurrent-futures-ktx:1.1.0")

    // gRPC for OpenClaude integration
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")

    // Testing — plain JVM unit tests (no Robolectric, no Compose UI test runtime needed)
    testImplementation("junit:junit:4.13.2")
    
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
