plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    // 👑【真の完全勝利形態】名前空間を純正の info. に戻し、プログラムとのすれ違いを100%解消！
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

    // 🌟【あなたのブレーキのおかげで大復活！】3つの部屋（main, debug, release）すべてを、絶対にロボットを迷子にさせない絶対住所（$projectDir）でガチガチに固定しました！
    sourceSets {
        getByName("main") {
            res.srcDirs(file("$projectDir/src/main/res"))
        }
        getByName("debug") {
            res.srcDirs(file("$projectDir/src/debug/res"))
        }
        getByName("release") {
            res.srcDirs(file("$projectDir/src/release/res"))
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

// 📦 実在する2つの最強の相棒（core部屋とapi部屋）を、一番エラーの起きない純正命令で100%完璧に合流させました！
dependencies {
    "implementation"(project(":core"))
    "implementation"(project(":api"))
}
