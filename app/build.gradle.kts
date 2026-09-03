plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    // 👑【完璧な info. 死守！】あなたが守り抜いてくれた純正の info. アドレスを 100% 完全に維持しています！
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

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

// 👑【宇宙最後の完全開通！】すべての門前払いの原因になっていた幻の :api の1行を完全にゴミ箱へ撤去し、本物の相棒である :core 部屋だけを正しい文法で直結させました！
dependencies {
    "implementation"(project(":core"))
}
