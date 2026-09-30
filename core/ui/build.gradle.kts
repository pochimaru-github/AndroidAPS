plugins {
    alias(libs.plugins.android.library)
    id("kotlin-android")
    id("kotlin-kapt")
}

android {
    namespace = "app.aaps.core.ui"
    compileSdk = 33

    defaultConfig {
        minSdk = 26
    }

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

kapt {
    correctErrorTypes = true
}

dependencies {
    api(libs.androidx.core)
    api(libs.androidx.appcompat)
    api(libs.androidx.preference)
    api(libs.androidx.gridlayout)

    api(libs.com.google.android.material)

    api(libs.com.google.dagger.android)
    api(libs.com.google.dagger.android.support)
    implementation(project(":core:interfaces"))
}
