plugins {
    id("com.android.library")
}

android {
    namespace = "app.aaps.pump.eopatch.core"
    compileSdk = 34

    defaultConfig {
        minSdk = 26
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
}

dependencies {
    // project.file() を使用することで、親モジュール (:pump:eopatch) への伝播時にも
    // :pump:eopatch:core 内の正しい絶対パス (/pump/eopatch/core/libs/eopatch_core.aar) として解決させます
    api(files(project.file("libs/eopatch_core.aar")))
}
