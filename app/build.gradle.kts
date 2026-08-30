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
}

dependencies {
    // 【解説】画面パーツに加えて、データベース（ObjectBox等）や通信パーツを一式すべて完全に復元しました
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
    implementation(libs.objectbox.kotlin)
    
    implementation(fileTree(mapOf("dir" to "libs", "include" to listOf("*.jar"))))
}
