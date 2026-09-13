plugins {
    alias(libs.plugins.android.library)
    // 【修正箇所 1】 alias(libs.plugins.ksp) を削除し、id("kotlin-kapt") を追加
    id("kotlin-kapt")
    id("kotlin-android")
    id("android-module-dependencies")
    id("all-open-dependencies")
    id("test-module-dependencies")
    id("jacoco-module-dependencies")
}

android {
    namespace = "app.aaps.implementation"
    compileSdk = 34 // ← この1行を追加

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
    implementation(project(":core:interfaces"))
    implementation(project(":core:keys"))
    implementation(project(":core:objects"))
    implementation(project(":core:ui"))
    implementation(project(":core:utils"))

    testImplementation(project(":shared:tests"))
    testImplementation(project(":plugins:aps"))
    testImplementation(project(":pump:virtual"))
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.androidx.work.testing)

    api(libs.androidx.datastore.preferences)
    // Protection
    api(libs.androidx.biometric)
    //Logger
    api(libs.org.slf4j.api)
    api(libs.com.github.tony19.logback.android)

    // 【修正箇所 2】 ksp(...) を kapt(...) に変更
    kapt(libs.com.google.dagger.compiler)
    kapt(libs.com.google.dagger.android.processor)
}
