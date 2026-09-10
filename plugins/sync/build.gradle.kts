plugins {
    id("com.android.library")
    kotlin("multiplatform")
    kotlin("kapt")
}

kotlin {
    // 1. 【警告への対応】Android ターゲットの明示的な登録
    androidTarget()

    sourceSets {
        val commonMain by getting {
            dependencies {
                // common の依存関係
            }
        }
        val androidMain by getting {
            dependencies {
                // android の依存関係
            }
        }
    }
}

// 2. 【エラーへの対応】 dependencies ブロック内に kapt を記述する
dependencies {
    kapt("androidx.room:room-compiler:2.5.2")
}
