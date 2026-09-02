plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.nightscout.androidaps"
    compileSdk = 34

    // 🌟【これが必要でした！】主役部屋の奥底に眠る、本物のアイコン画像やアプリ名データを工場へ合流させます！
    sourceSets {
        getByName("main") {
            res.srcDirs(file("$projectDir/src/main/res"), file("$projectDir/src/debug/res"), file("$projectDir/src/release/res"))
        }
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
