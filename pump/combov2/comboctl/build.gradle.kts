plugins {
    alias(libs.plugins.android.library)
    id("kotlin-android")
    id("android-module-dependencies")
}

android {
    namespace = "info.nightscout.comboctl"
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
        freeCompilerArgs = freeCompilerArgs + listOf("-Xexpect-actual-classes")
    }

    sourceSets {
        getByName("main") {
            java.srcDirs("src/commonMain/kotlin", "src/androidMain/kotlin", "src/main/kotlin")
        }
        getByName("test") {
            java.srcDirs("src/commonTest/kotlin", "src/test/kotlin")
        }
    }
}

dependencies {
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.datetime)
    implementation(libs.androidx.core)
    testImplementation(kotlin("test"))
}
