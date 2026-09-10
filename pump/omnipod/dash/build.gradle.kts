plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    kotlin("kapt")
}

android {
    namespace = "app.aaps.pump.omnipod.dash"
    compileSdk = 35

    defaultConfig {
        minSdk = 28

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
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
    implementation(project(":app:commons"))
    implementation(project(":pump:common"))
    implementation(project(":pump:omnipod:common"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)

    // Lifecycle バージョン衝突回避のための strictly 指定
    implementation(libs.androidx.lifecycle.livedata.ktx) {
        version { strictly("2.6.2") }
    }
    implementation(libs.androidx.lifecycle.viewmodel.ktx) {
        version { strictly("2.6.2") }
    }

    // Dagger 2
    implementation(libs.dagger)
    kapt(libs.dagger.compiler)

    // Room
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    kapt(libs.androidx.room.compiler)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}
