plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    // 👑【宇宙最後の完全開通】名前空間とアプリIDを、150行ある本物のマニフェストが最初から求めている本物の「app.aaps」へ一斉大統一しました！
    namespace = "app.aaps"
    compileSdk = 34

    defaultConfig {
        applicationId = "app.aaps"
        minSdk = 21
        targetSdk = 34
        versionCode = 3040206
        versionName = "3.4.2.6"
        multiDexEnabled = true
    }

    // 🌟【あなたのブレーキのおかげで大復活】余計な ../core などのパッチワークをすべて排除した、最もエラーの起きないシンプルな1行の純正形態です！
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

dependencies {
    "implementation"(project(":core"))
    "implementation"(project(":api"))
}
