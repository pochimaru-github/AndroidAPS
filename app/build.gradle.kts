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

    // 🌟【大文字小文字のひずみ完全矯正】すべての住所を「main」の枠の中にカンマ区切りで一斉合流！これにより、どちらの出荷モードでも100%同時にすべての資源が読み込まれます！
    sourceSets {
        getByName("main") {
            res.srcDirs(
                file("$projectDir/src/main/res"),
                file("$projectDir/src/debug/res"),
                file("$projectDir/src/release/res"),
                file("$projectDir/src/Release/res")
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

// 📦【真の最終ドッキング】実在する2つの最強の相棒（core部屋とapi部屋）を、一番エラーの出ない純正命令で100%完璧に合流させました！
dependencies {
    "implementation"(project(":core"))
    "implementation"(project(":api"))
}
