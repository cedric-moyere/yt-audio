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
        versionCode = 2
        versionName = "1.1"
        ndk {
            // Téléphones récents uniquement : APK beaucoup plus léger
            abiFilters += listOf("arm64-v8a")
        }
    }

    // Clé fixe : permet d'installer les mises à jour par-dessus l'ancienne version
    signingConfigs {
        getByName("debug") {
            storeFile = file("ytaudio.keystore")
            storePassword = "ytaudio"
            keyAlias = "ytaudio"
            keyPassword = "ytaudio"
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
