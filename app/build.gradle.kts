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

    // 🌟【宇宙最後の完全矯正】大文字（Values/Mipmap）の物理フォルダのひずみを完全に吸収し、どちらの出荷モードでも100%同時にお宝を掴み取ります！
    sourceSets {
        getByName("main") {
            res.srcDirs(
                "src/main/res",
                "src/main/Values",
                "src/main/Mipmap"
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
