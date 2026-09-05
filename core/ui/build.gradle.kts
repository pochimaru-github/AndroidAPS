plugins {
    alias(libs.plugins.android.library)
    id("kotlin-android")
    // ... 他のプラグイン
}

android {
    namespace = "app.aaps.core.ui"

    defaultConfig {
        minSdk = 26 // ← min(Versions.minSdk, Versions.wearMinSdk) から変更
    }
}

dependencies {
    api(libs.androidx.core)
    api(libs.androidx.appcompat)
    api(libs.androidx.preference)
    api(libs.androidx.gridlayout)


    api(libs.com.google.android.material)

    api(libs.com.google.dagger.android)
    api(libs.com.google.dagger.android.support)
    implementation(project(":core:interfaces"))
}
