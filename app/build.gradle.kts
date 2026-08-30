plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

android {
    compileSdk = 34

    defaultConfig {
        applicationId = "com.nightscout.androidaps"
        minSdk = 21
        targetSdk = 34
        versionCode = 3040206
        versionName = "3.4.2.6"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    kotlinOptions {
        jvmTarget = "11"
    }

    // 1分40秒台の壁を突破するための、画面表示（Compose）の有効化設定
    buildFeatures {
        compose = true
    }
}

dependencies {
    // 【解説】不足していた画面表示システム（Compose）と、通信・データ解析用の必須ライブラリを完全復元しました
    implementation(libs.kotlin.stdlib)
    implementation(libs.kotlin.reflect)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)
    
    implementation(libs.androidx.core)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.material)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.preference)
    implementation(libs.androidx.lifecycle.runtime)
    implementation(libs.androidx.lifecycle.livedata)
    implementation(libs.androidx.lifecycle.viewmodel)
    
    implementation(libs.okhttp.core)
    implementation(libs.retrofit.core)
    implementation(libs.moshi.core)
    implementation(libs.moshi.kotlin)
    implementation(libs.coil.core)
    implementation(libs.objectbox.kotlin)
    
    implementation(fileTree(mapOf("dir" to "libs", "include" to listOf("*.jar"))))
}
