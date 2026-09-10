plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.ksp)
    id("kotlin-android")
    id("kotlin-kapt")
    id("android-module-dependencies")
    id("test-module-dependencies")
    id("jacoco-module-dependencies")
}

android {
    namespace = "app.aaps.pump.common"

    buildFeatures {
        dataBinding = true
        viewBinding = true
    }
}

dependencies {
    implementation(project(":core:data"))
    implementation(project(":core:interfaces"))
    implementation(project(":core:keys"))
    implementation(project(":core:ui"))
    implementation(project(":core:utils"))
    implementation(project(":shared:impl"))

    // XStream (AnyTypePermission 用)
    implementation(libs.com.thoughtworks.xstream)

    // Logger
    api(libs.org.slf4j.api)
    api(libs.com.github.tony19.logback.android)

    api(libs.io.reactivex.rxjava3.rxandroid)

    api(libs.com.google.dagger.android.support)
    kapt(libs.com.google.dagger.compiler)
    kapt(libs.com.google.dagger.android.processor)
}
