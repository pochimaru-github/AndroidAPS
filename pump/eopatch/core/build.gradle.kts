plugins {
    id("com.android.library")
}

repositories {
    flatDir {
        dirs("libs")
    }
}

android {
    namespace = "app.aaps.pump.eopatch.core"
    compileSdk = 34

    defaultConfig {
        minSdk = 26
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    // AGP の AAR 直接参照制限を回避するため flatDir 経由で指定
    api(group = "", name = "eopatch_core", ext = "aar")
}
