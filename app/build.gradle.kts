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

    // 🌟【イタチごっこ完全終了】小部屋に分けず、工場の公式標準ルール（main）の1箇所に、実在するフォルダ（src/main/res）の住所を完璧にドッキングしました！
    sourceSets {
        getByName("main") {
            res.srcDirs("src/main/res")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

// 📦【合流完了】主役アプリ自身が、core部屋とapi部屋の資源をせき止めを無視して100%直通で吸い込みます！
dependencies {
    "api"(project(":core"))
    "api"(project(":api"))
}
