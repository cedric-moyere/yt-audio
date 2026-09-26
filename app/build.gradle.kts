plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "dev.moyere.ytaudio"
    compileSdk = 34

    defaultConfig {
        applicationId = "dev.moyere.ytaudio"
        minSdk = 29          // Android 10+
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
        ndk {
            // Téléphones récents uniquement : APK beaucoup plus léger
            abiFilters += listOf("arm64-v8a")
        }
    }

    buildTypes {
        debug { isMinifyEnabled = false }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }

    // Requis par youtubedl-android (python + ffmpeg natifs)
    packaging {
        jniLibs.useLegacyPackaging = true
    }
}

val youtubedlAndroid = "0.18.1"

dependencies {
    implementation("io.github.junkfood02.youtubedl-android:library:$youtubedlAndroid")
    implementation("io.github.junkfood02.youtubedl-android:ffmpeg:$youtubedlAndroid")
}
