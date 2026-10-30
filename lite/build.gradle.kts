plugins { id("com.android.application") }
android {
    namespace = "app.rayclient.lite"
    compileSdk = 35
    defaultConfig {
        applicationId = "app.rayclient.lite"
        minSdk = 23
        targetSdk = 28
        versionCode = 1
        versionName = "0.1.0-arm32-test"
        ndk { abiFilters += listOf("armeabi-v7a") }
    }
    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    packaging { jniLibs { useLegacyPackaging = true } }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
}
dependencies { testImplementation("junit:junit:4.13.2") }

