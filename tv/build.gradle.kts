plugins {
    id("com.android.application")
}
android {
    ndkVersion = "27.2.12479018"
    namespace = "app.rayclient.tv"
    compileSdk = 37
    compileSdkMinor = 0

    defaultConfig {
        applicationId = "app.rayclient.tv"
        minSdk = 26
        targetSdk = 35
        versionCode = 2
        versionName = "0.1.1-dev"
        ndk { abiFilters += listOf("armeabi-v7a") }
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            // Stage 1 has no official signing identity or release pipeline.
            // CI distributes the debug-signed developer APK only.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }
    packaging { jniLibs { useLegacyPackaging = true } }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
dependencies {
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test:core:1.6.1")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
}
