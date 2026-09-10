plugins {
    id("com.android.library")
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
                implementation(libs.androidx.room.runtime)
                implementation(libs.androidx.room.ktx)
                implementation(libs.androidx.work.runtime.ktx)
                implementation(libs.hilt.android)
                implementation(libs.androidx.hilt.work)
            }
        }
    }
}

dependencies {
    add("kapt", libs.hilt.compiler)
    add("kapt", libs.androidx.hilt.compiler)
    add("kapt", libs.androidx.room.compiler)
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
