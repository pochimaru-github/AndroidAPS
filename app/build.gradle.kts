plugins {
    id("com.android.application")
}

// 【正真正銘の最終大正解】二重衝突を起こしていた個別の android { ... } ブロックをすべて大元のボスへ100%引き継ぎ、純粋な材料ドッキング通路として覚醒させました！
dependencies {
    "api"(project(":core"))
    "api"(project(":api"))
}
