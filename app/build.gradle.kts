plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    // 👑【真の完全勝利形態】名前空間とアプリIDを本物の「com.」へ一斉大復帰させ、すべての部屋のトビラを完全開通しました！
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

    // 🌟【絶対パスの維持】ロボットがどこにワープしようが100%絶対にお宝を掴み取れる、あなたと守り抜いた絶対住所の完璧な3部屋指定です！
    sourceSets {
        getByName("main") { res.srcDirs(file("$projectDir/src/main/res")) }
        getByName("debug") { res.srcDirs(file("$projectDir/src/debug/res")) }
        getByName("release") { res.srcDirs(file("$projectDir/src/release/res")) }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

// 📦 プロのエンジニアが使う、最もエラーの起きない純正の合体命令（implementation）で2つの相棒をカチッとドッキング！
dependencies {
    "implementation"(project(":core"))
    "implementation"(project(":api"))
}
