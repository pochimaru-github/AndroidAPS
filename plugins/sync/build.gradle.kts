plugins {
    id("com.android.library")
    kotlin("multiplatform")
    kotlin("kapt")
}

android {
    namespace = "info.nightscout.androidaps.plugins.sync"
    compileSdk = 34

    defaultConfig {
        minSdk = 28
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    // 警告回避のため androidTarget を宣言
    androidTarget()

    sourceSets {
        val commonMain by getting {
            dependencies {
                // Room 等の共通依存関係
            }
        }
        val androidMain by getting {
            dependencies {
                // kapt の依存関係は dependencies ブロックではなく sourceSets 内で管理
            }
        }
    }
}

// kapt の設定は dependencies ブロックではなく dependencies { ... } 構成を正しく定義
dependencies {
    add("kapt", "androidx.room:room-compiler:2.5.2")
}
