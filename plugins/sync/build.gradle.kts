plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    id("kotlin-kapt")
}

android {
    namespace = "app.nightscout.android.plugins.sync"

    buildFeatures {
        viewBinding = true
        dataBinding = true
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
    implementation(project(":core:ui"))
    implementation(project(":core:interfaces"))

    // Dagger (Kapt 構成)
    implementation("com.google.dagger:dagger:2.50")
    implementation("com.google.dagger:dagger-android:2.50")
    implementation("com.google.dagger:dagger-android-support:2.50")
    kapt("com.google.dagger:dagger-compiler:2.50")
    kapt("com.google.dagger:dagger-android-processor:2.50")
}

kapt {
    correctErrorTypes = true
    mapDiagnosticLocations = true
}
