plugins {
    id("com.android.application")
}

android {
    namespace = "com.nightscout.androidaps"

    sourceSets {
        getByName("main") {
            res.srcDirs("src/main/res")
        }
    }
}

// 【ここに書き足し！】core部屋とapi部屋に眠るすべてのテーマ（AppTheme）やアイコンを一滴残らず主役へ直通ドッキングさせました！
dependencies {
        "api"(project(":core"))
        "api"(project(":api"))
}
