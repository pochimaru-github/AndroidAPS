plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.ksp)
    id("kotlin-android")
    id("kotlin-parcelize")
    id("android-module-dependencies")
    id("all-open-dependencies")
    id("test-module-dependencies")
    id("jacoco-module-dependencies")
}

android {
    namespace = "app.aaps.core.objects"

    defaultConfig {
        minSdk = 26
        // ベクター画像の互換処理（リソースマージ時の Workers クラッシュを防止）
        vectorDrawables.useSupportLibrary = true
    }
}

dependencies {
    implementation(project(":core:data"))
    implementation(project(":core:interfaces"))
    implementation(project(":core:keys"))
    implementation(project(":core:ui"))
    implementation(project(":core:utils"))

    testImplementation(project(":shared:tests"))
    testImplementation(project(":shared:impl"))

    api(libs.kotlin.stdlib.jdk8)
    api(libs.com.google.android.material)
    api(libs.com.google.guava)
    api(libs.androidx.activity)
    api(libs.androidx.appcompat)

    api(libs.com.google.dagger.android)
    api(libs.com.google.dagger.android.support)

    //WorkManager
    api(libs.androidx.work.runtime)  // DataWorkerStorage

    ksp(libs.com.google.dagger.compiler)
    ksp(libs.com.google.dagger.android.processor)
}
