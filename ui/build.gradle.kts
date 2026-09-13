plugins {
    alias(libs.plugins.android.library)
    id("kotlin-kapt") // ← 【変更】alias(libs.plugins.ksp) から kapt へ変更
    id("kotlin-android")
    id("android-module-dependencies")
    id("test-module-dependencies")
    id("jacoco-module-dependencies")
}

android {
    namespace = "app.aaps.ui"
    compileSdk = 34

    // 【追加】vectorDrawables 設定を入れるため defaultConfig を定義
    defaultConfig {
        minSdk = 26
        vectorDrawables.useSupportLibrary = true
    }

    // 【追加】DataBinding / ViewBinding を有効化
    buildFeatures {
        dataBinding = true
        viewBinding = true
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
    implementation(project(":core:data"))
    implementation(project(":core:graph"))
    implementation(project(":core:graphview"))
    implementation(project(":core:interfaces"))
    implementation(project(":core:keys"))
    implementation(project(":core:libraries"))
    implementation(project(":core:objects"))
    implementation(project(":core:ui"))
    implementation(project(":core:utils"))

    testImplementation(project(":shared:tests"))

    api(libs.androidx.core)
    
    // 【変更】Dagger アノテーションプロセッサを ksp から kapt へ変更
    kapt(libs.com.google.dagger.compiler)
    kapt(libs.com.google.dagger.android.processor)
}
