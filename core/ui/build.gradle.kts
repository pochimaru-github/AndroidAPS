plugins {
    alias(libs.plugins.android.library)
    id("kotlin-android")
    id("kotlin-kapt") // ←【追加】DataBinding の自動生成処理（kapt）を有効化
}

android {
    namespace = "app.aaps.core.ui"

    defaultConfig {
        minSdk = 26
    }

    // 【追加】DataBinding と ViewBinding の自動生成を有効化
    buildFeatures {
        dataBinding = true
        viewBinding = true
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
