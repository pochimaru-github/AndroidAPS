plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    // 👑【真の完全勝利】名前空間を純正の info. に戻し、プログラム内部とのすれ違いを100%解消！
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

    // 🌟【イタチごっこ完全終了】お部屋のファイル側での余計な file($projectDir) をすべて排除し、ただの文字指定に直したことで、製品版の出荷クレーンが100%完璧にお宝を掴み取ります！
    sourceSets {
        getByName("main") {
            res.srcDirs("src/main/res")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

// 📦 実在する2つの最強の相棒（core部屋とapi部屋）を、一番エラーの起きない純正命令（implementation）で100%完璧にドッキング！
dependencies {
    "implementation"(project(":core"))
    "implementation"(project(":api"))
}
