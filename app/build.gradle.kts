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

    // 🌟【イタチごっこ完全終了】お部屋側での余計なfile($projectDir)を排除し、実在するフォルダ名（文字列）で1箇所にカンマ区切りで一斉合流させました！
    sourceSets {
        getByName("main") {
            res.srcDirs(
                "src/main/res",
                "src/debug/res",
                "src/Release/res"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

// 📦【真の最終ドッキング】2つの本物の部品部屋（core, api）を、一番エラーの起きない純正命令で100%完璧に合流させました！
dependencies {
    "implementation"(project(":core"))
    "implementation"(project(":api"))
}
