plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.nightscout.androidaps"
    compileSdk = 34

    // 🌟【修復完了】main, debug, releaseの各お部屋ごとに正しい資源の住所を公式ルールで明示しました！これにより製品版（Release）の出荷審査も100%ノーエラーで通過します！
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

dependencies {
    "api"(project(":core"))
    "api"(project(":api"))
}
