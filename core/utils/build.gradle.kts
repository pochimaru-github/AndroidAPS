plugins {
    alias(libs.plugins.android.library)
    id("kotlin-android")
    kotlin("plugin.allopen")
    id("android-module-dependencies")
    id("all-open-dependencies")
    id("test-module-dependencies")
    id("jacoco-module-dependencies")
}

android {
    namespace = "app.aaps.core.utils"
    defaultConfig {
        minSdk = 26 // ← 直値 26 に書き換え
    }
}

dependencies {

    api(libs.net.danlew.android.joda)

    //Firebase
    api(platform(libs.com.google.firebase.bom))
    api(libs.com.google.firebase.analytics)
    api(libs.com.google.firebase.crashlytics)

    //CryptoUtil
    api(libs.com.madgag.spongycastle)
    api(libs.com.google.crypto.tink)

    //WorkManager
    api(libs.androidx.work.runtime) // DataWorkerStorage

    api(libs.com.google.dagger.android)
    api(libs.com.google.dagger.android.support)
}
