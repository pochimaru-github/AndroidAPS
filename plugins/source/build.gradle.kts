plugins {
    alias(libs.plugins.android.library)
    id("kotlin-android")
    id("kotlin-kapt")
    id("android-module-dependencies")
    id("test-module-dependencies")
    id("jacoco-module-dependencies")
}

android {
    namespace = "app.aaps.plugins.source"

    // DataBinding と ViewBinding を有効化
    buildFeatures {
        dataBinding = true
        viewBinding = true
    }
}

dependencies {
    implementation(project(":core:data"))
    implementation(project(":core:interfaces"))
    implementation(project(":core:keys"))
    implementation(project(":core:objects"))
    implementation(project(":core:nssdk"))
    implementation(project(":core:ui"))
    implementation(project(":core:utils"))
    implementation(project(":core:validators"))
    implementation(project(":shared:impl"))

    testImplementation(libs.androidx.work.testing)

    testImplementation(project(":shared:tests"))

    kapt(libs.com.google.dagger.compiler)
    kapt(libs.com.google.dagger.android.processor)
}
