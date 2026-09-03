plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "info.nightscout.androidaps"
    compileSdk = 34

    defaultConfig {
        applicationId = "info.nightscout.androidaps"
        minSdk = 21
        targetSdk = 34
        versionCode = 3040206
        versionName = "3.4.2.6"
        multiDexEnabled = true
    }

    // 👑【隔離解除】自動合流を邪魔していた sourceSets ブロックを完全に撤去し、工場の純正マージパワーを解放しました！

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    "implementation"(project(":core"))
    "implementation"(project(":api"))
}
