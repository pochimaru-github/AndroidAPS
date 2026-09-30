import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    alias(libs.plugins.android.library)
    kotlin("android")
    kotlin("kapt")
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

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        viewBinding = true
        dataBinding = false
    }
}

tasks.withType<KotlinCompile>().configureEach {
    kotlinOptions {
        jvmTarget = "17"
    }
}

tasks.withType<JavaCompile>().configureEach {
    sourceCompatibility = JavaVersion.VERSION_17.toString()
    targetCompatibility = JavaVersion.VERSION_17.toString()
}

kapt {
    correctErrorTypes = true
    keepJavacAnnotationProcessors = true
    javacOptions {
        option("-Xlint:-processing")
    }
    arguments {
        arg("kotlin.suppress.metadata.version.check", "true")
        arg("room.schemaLocation", "$projectDir/schemas")
    }
}

dependencies {
    // AndroidX Browser (OHLoginActivity 用)
    implementation("androidx.browser:browser:1.8.0")
    
    // AppAuth (AuthFlowIn / Tidepool 用)
    implementation("net.openid:appauth:0.11.1")

    // 追加：不足していた core 配下のモジュール依存
    implementation(project(":core:nssdk"))
    implementation(project(":core:utils"))
    implementation(project(":core:validators"))

    // 既存のモジュール依存
    implementation(project(":core:interfaces"))
    implementation(project(":core:data"))
    implementation(project(":core:objects"))
    implementation(project(":core:keys"))
    implementation(project(":core:ui"))

    implementation(libs.androidx.core)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room)
    implementation(libs.androidx.work.runtime)

    // Gson & Network & Socket.io
    implementation(libs.com.google.code.gson)
    implementation("com.google.android.gms:play-services-wearable:18.1.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("io.socket:socket.io-client:2.0.1")

    // Dagger2
    implementation(libs.com.google.dagger.android)
    implementation(libs.com.google.dagger.android.support)

    implementation(project(":core:interfaces")) // ★ AapsLogger を含むコアインターフェースモジュール

    // Annotation Processors (KAPT)
    kapt(libs.com.google.dagger.compiler)
    kapt(libs.com.google.dagger.android.processor)
    kapt(libs.androidx.room.compiler)
}
