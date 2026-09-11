plugins {
    alias(libs.plugins.android.library)
    kotlin("multiplatform")
    kotlin("kapt")
}

kotlin {
    androidTarget()

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(project(":core:interfaces"))
                implementation(project(":core:data"))
                implementation(project(":core:objects"))
            }
        }

        val androidMain by getting {
            dependencies {
                implementation(project(":core:interfaces"))
                implementation(project(":core:data"))
                implementation(project(":core:objects"))

                implementation(libs.androidx.core)
                implementation(libs.androidx.room.runtime)
                implementation(libs.androidx.room)
                implementation(libs.androidx.work.runtime)
                
                // Gson
                implementation(libs.com.google.code.gson)
                
                // Dagger2
                implementation(libs.com.google.dagger.android)
                implementation(libs.com.google.dagger.android.support)
            }
        }
    }
}

dependencies {
    implementation(project(":core:interfaces"))
    implementation(project(":core:data"))
    implementation(project(":core:objects"))

    add("kapt", libs.com.google.dagger.compiler)
    add("kapt", libs.com.google.dagger.android.processor)
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
