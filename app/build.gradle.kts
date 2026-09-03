plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    // 👑【完全純正復帰】識別IDを本物純正の「info.」に戻し、余計な namespace 設定はバサッと全消去して更地に戻しました！
    applicationId = "info.nightscout.androidaps"
    compileSdk = 34

    defaultConfig {
        applicationId = "info.nightscout.androidaps"
        minSdk = 21
        targetSdk = 34
        versionCode = 3040206
        versionName = "3.4.2.6"
        multiDexEnabled = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    "implementation"(project(":core"))
    "implementation"(project(":api"))
}
