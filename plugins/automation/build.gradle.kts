plugins {
    alias(libs.plugins.android.library)
    id("kotlin-android")
    id("kotlin-kapt")
    id("android-module-dependencies")
    id("test-module-dependencies")
    id("jacoco-module-dependencies")
}

android {
    namespace = "app.aaps.plugins.automation"

    buildFeatures {
        viewBinding = true
        dataBinding = true
    }
}

dependencies {
    implementation(project(":core:data"))
    implementation(project(":core:interfaces"))
    implementation(project(":core:keys"))
    implementation(project(":core:objects"))
    implementation(project(":core:utils"))
    implementation(project(":core:ui"))
    implementation(project(":core:validators"))

    testImplementation(project(":shared:tests"))
    testImplementation(project(":shared:impl"))
    testImplementation(project(":implementation"))
    testImplementation(project(":plugins:main"))
    testImplementation(project(":pump:virtual"))

    api(libs.androidx.constraintlayout)
    api(libs.com.google.android.gms.playservices.location)
    api(libs.kotlin.reflect)
    // Places SDK
    api(libs.com.google.android.places)
    api(libs.com.github.rtchagas.pingplacepicker)
    api(libs.com.google.firebase.config)

    // Dagger (Kapt 構成)
    implementation("com.google.dagger:dagger:2.50")
    implementation("com.google.dagger:dagger-android:2.50")
    implementation("com.google.dagger:dagger-android-support:2.50")
    kapt("com.google.dagger:dagger-compiler:2.50")
    kapt("com.google.dagger:dagger-android-processor:2.50")
}

kapt {
    correctErrorTypes = true
    keepJavacAnnotationProcessors = true
}
