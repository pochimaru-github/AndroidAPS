plugins {
    alias(libs.plugins.android.library)
    kotlin("multiplatform")
    kotlin("kapt")
}

kotlin {
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
                implementation(libs.androidx.core)
                implementation(libs.androidx.room.runtime)
                implementation(libs.androidx.room)
                implementation(libs.androidx.work.runtime)
                
                // Dagger2 (Hilt ではなく Dagger を使用)
                implementation(libs.com.google.dagger.android)
                implementation(libs.com.google.dagger.android.support)
                kapt(libs.com.google.dagger.compiler)
                kapt(libs.com.google.dagger.android.processor)
                kapt(libs.androidx.room.compiler)
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
