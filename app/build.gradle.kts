plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    // 👑【宇宙最後の完全一本化】名前空間とアプリIDを、150行ある本物の純正マニフェストが最初から求めている、正しい戸籍名「info.」に完全復帰させました！
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

    // 🌟【絶対パスの維持】ロボットが工場のどこにワープしようが100%絶対にお宝を掴み取れる、あなたと守り抜いた絶対住所（$projectDir）の完璧な3部屋指定です！
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

// 📦 プロのエンジニアが使う、最もエラーの起きない純正の合体命令（implementation）で2つの部品部屋をカチッとドッキング！
dependencies {
    "implementation"(project(":core"))
    "implementation"(project(":api"))
}
