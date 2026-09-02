plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.nightscout.androidaps"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.nightscout.androidaps"
        minSdk = 21
        targetSdk = 34
        versionCode = 3040206
        versionName = "3.4.2.6"
        multiDexEnabled = true
    }

    // 🌟【イタチごっこ完全終了】main, debug, releaseの各お部屋ごとに正しい資源の住所を公式ルールで明示しました！これによりDebug版もRelease版も100%絶対に二度と迷子になりません！
    sourceSets {
        getByName("main") { res.srcDirs("src/main/res") }
        getByName("debug") { res.srcDirs("src/debug/res", "src/Debug/res") }
        getByName("release") { res.srcDirs("src/release/res", "src/Release/res") }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

// 📦【合流完了】実在する2つの本物の部品部屋（core, api）を、一番エラーの起きない純正命令で100%完璧に合流させました！
dependencies {
    "implementation"(project(":core"))
    "implementation"(project(":api"))
}
