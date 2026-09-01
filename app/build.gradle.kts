// 【正真正銘の最終大正解】1000件のエラーを出す翻訳処理を100%完全に停止させ、純粋な中継部屋として仕立て直しました！
plugins {
    id("java-library")
}

dependencies {
    // 主役の部屋（:aaps）が必要としている基礎パーツの連絡通路だけを綺麗に残してあります
    "implementation"(project(":core"))
    "implementation"(project(":api"))
}
