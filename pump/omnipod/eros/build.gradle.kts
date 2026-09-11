plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    id("kotlin-kapt")
    id("android-module-dependencies")
    id("test-module-dependencies")
    id("jacoco-module-dependencies")
}

android {
    namespace = "app.aaps.pump.omnipod.eros"

    defaultConfig {
        vectorDrawables.useSupportLibrary = true
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

dependencies {
    implementation(project(":core:data"))
    implementation(project(":core:interfaces"))
    implementation(project(":core:keys"))
    implementation(project(":core:libraries"))
    implementation(project(":core:utils"))
    implementation(project(":core:ui"))
    implementation(project(":core:validators"))
    implementation(project(":pump:omnipod:common"))
    implementation(project(":pump:common"))

    implementation(libs.androidx.room.runtime)
    implementation("androidx.room:room-ktx:2.6.1")
    implementation("androidx.room:room-rxjava3:2.6.1")
    kapt(libs.androidx.room.compiler)

    api(libs.com.google.dagger.android.support)
    kapt(libs.com.google.dagger.compiler)
    kapt(libs.com.google.dagger.android.processor)

    androidTestImplementation(project(":shared:tests"))
    testImplementation(libs.androidx.room.testing)
    testImplementation(project(":implementation"))
    testImplementation(project(":shared:impl"))
    testImplementation(project(":shared:tests"))
}
