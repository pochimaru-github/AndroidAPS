plugins {
    alias(libs.plugins.android.library)
    id("kotlin-android")
    id("kotlin-kapt")
    id("android-module-dependencies")
    id("test-module-dependencies")
    id("jacoco-module-dependencies")
}

android {
    namespace = "app.aaps.pump.omnipod.common"

    defaultConfig {
        vectorDrawables.useSupportLibrary = true
    }

    buildFeatures {
        dataBinding = false
        viewBinding = true
    }
}

dependencies {
    implementation(project(":core:data"))
    implementation(project(":core:interfaces"))
    implementation(project(":core:keys"))
    implementation(project(":core:libraries"))
    implementation(project(":core:ui"))
    implementation(project(":core:utils"))

    // Lifecycleのバージョン競合(2.9.0)を回避し、AGP 7.4.2と互換のある2.6.2に固定
    implementation("androidx.lifecycle:lifecycle-runtime-ktx") {
        version {
            strictly("2.6.2")
        }
    }

    api(libs.androidx.constraintlayout)
    api(libs.androidx.fragment)
    api(libs.androidx.navigation.fragment)
    api(libs.com.google.android.material)

    testImplementation(project(":shared:tests"))

    api(libs.com.google.dagger.android.support)
    kapt(libs.com.google.dagger.compiler)
    kapt(libs.com.google.dagger.android.processor)
}
