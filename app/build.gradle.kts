import java.net.URI
import java.nio.file.Files
import java.nio.file.StandardCopyOption

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.origin.ffmpeg"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.origin.ffmpeg"
        minSdk = 24
        targetSdk = 35
        versionCode = 100000
        versionName = "1.0.0"

        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86_64")
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
        viewBinding = true
        buildConfig = true
    }

    packaging {
        jniLibs {
            useLegacyPackaging = true
        }
    }
}

// Task: download pre-built FFmpeg binaries from GitHub releases
task("downloadFFmpeg") {
    val ffmpegDir = file("src/main/jniLibs")
    val abis = mapOf(
        "arm64-v8a" to "arm64-v8a",
        "armeabi-v7a" to "armeabi-v7a",
        "x86_64" to "x86_64"
    )
    val baseUrl = "https://github.com/BtbN/FFmpeg-Builds/releases/download/latest"

    doLast {
        abis.forEach { (abi, _) ->
            val outputDir = file("$ffmpegDir/$abi")
            outputDir.mkdirs()

            val binFiles = listOf("ffmpeg", "ffprobe")
            binFiles.forEach { binary ->
                val outputFile = File(outputDir, "$binary.so")
                if (outputFile.exists()) {
                    println("$binary for $abi already exists, skipping")
                    return@forEach
                }

                val zipFileName = "ffmpeg-master-latest-linux64-gpl-shared.tar.xz"
                val downloadUrl = "$baseUrl/$zipFileName"

                println("NOTE: FFmpeg binaries must be pre-placed in src/main/jniLibs/\$abi/")
                println("  Download from: $downloadUrl")
                println("  Extract and rename ffmpeg -> ffmpeg.so, ffprobe -> ffprobe.so")
            }
        }
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.constraintlayout:constraintlayout:2.2.1")
    implementation("androidx.recyclerview:recyclerview:1.4.0")
    implementation("androidx.activity:activity-ktx:1.9.3")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.documentfile:documentfile:1.0.1")
    implementation("com.google.code.gson:gson:2.11.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
}
