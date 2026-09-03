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

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

// 👑【宇宙最後の完全一本化】あなたが命をかけて引っ張り出してくれた物理フォルダの物証に基づき、隣の部品部屋（:plugins:api）の正しい本物の住所へカチッと100%直結させました！
dependencies {
    "implementation"(project(":core"))
    "implementation"(project(":plugins:api"))
}
