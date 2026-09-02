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

    // 🌟【宇宙最高の開通完了】実在するすべてのお宝フォルダの住所を、カンマ区切りでmainの1箇所に一斉合流させました！これにより、どちらの出荷モードでも100%同時に本物のアイコンやテーマを掴み取ります！
    sourceSets {
        getByName("main") {
            res.srcDirs(
                file("$projectDir/src/main/res"),
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

dependencies {
    // 幽霊部屋のない、100%クリーンな更地です
}
