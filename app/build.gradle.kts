plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    kotlin("kapt") // 【追加】画面システムやデータベースを正常に結合するための必須プラグイン
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

    buildFeatures {
        compose = true
    }
}

dependencies {
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
    
    // 【解説】kaptに対応したObjectBox（データベース）の自動生成パーツを追加しました
    implementation(libs.objectbox.kotlin)
    kapt("io.objectbox:objectbox-processor:3.6.0")
    
    implementation(fileTree(mapOf("dir" to "libs", "include" to listOf("*.jar"))))
}
