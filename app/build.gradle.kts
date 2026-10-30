plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// CI sets GITHUB_RUN_NUMBER, so every build has a higher versionCode and installs as an update.
val buildNumber = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: 1

android {
    namespace = "com.example.linkgrab"
    compileSdk = 35

    signingConfigs {
        // Fixed key so every build is signed the same way and updates install over older versions.
        getByName("debug") {
            storeFile = file("link.keystore")
            storePassword = "linkgrab123"
            keyAlias = "link"
            keyPassword = "linkgrab123"
        }
    }

    defaultConfig {
        applicationId = "com.example.linkgrab"
        minSdk = 29
        targetSdk = 35
        versionCode = buildNumber
        versionName = "1.0.$buildNumber"

        // yt-dlp + ffmpeg ship native binaries; limit to the ABIs phones actually use
        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a")
        }
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

    buildFeatures {
        compose = true
    }

    packaging {
        // Required so the bundled yt-dlp/ffmpeg binaries can be executed
        jniLibs {
            useLegacyPackaging = true
        }
    }
}

dependencies {
    val ytdlp = "0.17.2" // check Maven Central for the newest io.github.junkfood02.youtubedl-android release

    implementation("io.github.junkfood02.youtubedl-android:library:$ytdlp")
    implementation("io.github.junkfood02.youtubedl-android:ffmpeg:$ytdlp")

    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("io.coil-kt:coil-compose:2.7.0") // thumbnails

    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.fragment:fragment-ktx:1.8.5") // BiometricPrompt needs a FragmentActivity
    implementation("androidx.biometric:biometric:1.1.0")

    implementation(platform("androidx.compose:compose-bom:2024.10.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")
}
