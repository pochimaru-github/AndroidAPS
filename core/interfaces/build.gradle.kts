plugins {
    alias(libs.plugins.android.library)
    id("kotlin-android")
    id("kotlin-parcelize")
    id("kotlinx-serialization")
    id("android-module-dependencies")
    id("test-module-dependencies")
    id("jacoco-module-dependencies")
}

android {
    namespace = "app.aaps.core.interfaces"
    defaultConfig {
        minSdk = 26 // ← Versions 参照から直値 26 へ変更
    }
}

dependencies {
    implementation(project(":core:data"))
    implementation(project(":core:keys"))

    api(libs.androidx.appcompat)
    api(libs.androidx.preference)

    // バージョンを直接指定して参照エラーを解消
    api(platform("org.jetbrains.kotlinx:kotlinx-serialization-bom:1.5.1"))
    api("org.jetbrains.kotlinx:kotlinx-serialization-json:1.5.1")
    api("org.jetbrains.kotlinx:kotlinx-serialization-protobuf:1.5.1")

    api(libs.org.apache.commons.lang3)
    api(libs.net.danlew.android.joda)

    //RxBus
    api(libs.io.reactivex.rxjava3.rxkotlin)
    testImplementation(libs.io.reactivex.rxjava3.rxandroid)
}
