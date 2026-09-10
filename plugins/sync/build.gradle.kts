plugins {
    id("com.android.library")
    kotlin("multiplatform")
    kotlin("kapt")
}

kotlin {
    // 警告回避のための明示的登録
    androidTarget {
        compilations.all {
            kotlinOptions {
                jvmTarget = "17"
            }
        }
    }

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(project(":core:model"))
                implementation(project(":core:interfaces"))
            }
        }

        val androidMain by getting {
            dependencies {
                // Version Catalogアクセサのミスを防止するため直接指定
                implementation("androidx.room:room-runtime:2.5.2")
                implementation("androidx.room:room-ktx:2.5.2")
                implementation("androidx.work:work-runtime-ktx:2.8.1")
                
                // Hilt
                implementation("com.google.dagger:hilt-android:2.48")
                kapt("com.google.dagger:hilt-compiler:2.48")
                implementation("androidx.hilt:hilt-work:1.0.0")
                kapt("androidx.hilt:hilt-compiler:1.0.0")
            }
        }
    }
}

android {
    namespace = "app.aaps.plugins.sync"
    compileSdk = 34

    defaultConfig {
        minSdk = 28
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
