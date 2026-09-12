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
                implementation(project(":core:keys"))
                implementation(project(":core:ui"))
            }
        }

        val androidMain by getting {
            dependencies {
                implementation(project(":core:interfaces"))
                implementation(project(":core:data"))
                implementation(project(":core:objects"))
                implementation(project(":core:keys"))
                implementation(project(":core:ui"))

                implementation(libs.androidx.core)
                implementation(libs.androidx.room.runtime)
                implementation(libs.androidx.room)
                implementation(libs.androidx.work.runtime)
                
                // Gson & Network & Socket.io (明確なGAV指定)
                implementation(libs.com.google.code.gson)
                implementation("com.google.android.gms:play-services-wearable:18.1.0")
                implementation("com.squareup.okhttp3:okhttp:4.12.0")
                implementation("com.squareup.retrofit2:retrofit:2.9.0")
                implementation("io.socket:socket.io-client:2.0.1")
                
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
    implementation(project(":core:keys"))
    implementation(project(":core:ui"))

    implementation("com.google.android.gms:play-services-wearable:18.1.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("io.socket:socket.io-client:2.0.1")

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
