plugins {
    alias(libs.plugins.android.library)
    id("kotlin-android")
    id("android-module-dependencies")
}

android {
    namespace = "com.jjoe64.graphview" // 【変更】Javaコードの import/パッケージ名と一致させる
    compileSdk = 33

    defaultConfig {
        minSdk = 26
    }

    // 【追加】Release ビルド時の R クラス生成を有効化
    buildFeatures {
        buildConfig = true
        resValues = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    api(libs.androidx.core)
}
