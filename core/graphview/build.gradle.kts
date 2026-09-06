plugins {
    alias(libs.plugins.android.library)
    id("kotlin-android")
    id("android-module-dependencies")
}

android {
    namespace = "com.jjoe64.graphview" // 【変更】Javaコードの import/パッケージ名と一致させる
    compileSdk = 34

    defaultConfig {
        minSdk = 26
    }

    // 【追加】Release ビルド時の R クラス生成を有効化
    buildFeatures {
        buildConfig = true
        resValues = true
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
    api(libs.androidx.core)
}
