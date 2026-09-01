plugins {
    id("com.android.application")
}

// 【ここに書き足し！】主役の部屋の奥底に眠っている、本物のアイコン画像やアプリ名を完璧に工場へ合流させました！
android {
    sourceSets {
        getByName("main") {
            res.srcDirs("src/main/res")
        }
    }
}
