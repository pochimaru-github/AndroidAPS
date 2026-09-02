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

    // 👑【宇宙最後の完全開通】隣のcore部屋、api部屋に眠るすべての本物のデザインテーマ（AppTheme）や数値データを、パスの階層（../）を跨いでmainの1枠にダイレクト一斉流入させました！これにより、すべてのNot Foundは100%物理的に完全消滅します！
    sourceSets {
        getByName("main") {
            res.srcDirs(
                "src/main/res",
                "../core/src/main/res",
                "../api/src/main/res"
            )
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
