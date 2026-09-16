plugins {
    alias(libs.plugins.android.library)
    id("kotlin-android")
    id("kotlin-kapt")
    id("android-module-dependencies")
    id("test-module-dependencies")
    id("jacoco-module-dependencies")
}

android {
    namespace = "info.nightscout.pump.combov2"
    compileSdk = 33

    defaultConfig {
        minSdk = 26
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        dataBinding = true
    }
}

kapt {
    correctErrorTypes = true
}

dependencies {
    api(project(":core:data"))
    api(project(":core:interfaces"))
    api(project(":core:keys"))
    api(project(":core:libraries"))
    api(project(":core:objects"))
    api(project(":core:ui"))
    api(project(":core:utils"))
    api(project(":core:validators"))

    api(project(":pump:combov2:comboctl"))

    api(libs.androidx.lifecycle.viewmodel)
    api(libs.kotlinx.datetime)

    api(platform(libs.kotlinx.serialization.bom))
    api(libs.kotlinx.serialization.core)

    kapt(libs.com.google.dagger.compiler)
    kapt(libs.com.google.dagger.android.processor)
}
