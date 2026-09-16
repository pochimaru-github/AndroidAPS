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
    }

    sourceSets {
        getByName("main") {
            java.srcDirs("src/commonMain/kotlin", "src/androidMain/kotlin", "src/main/kotlin")
            kotlin.srcDirs("src/commonMain/kotlin", "src/androidMain/kotlin", "src/main/kotlin")
            manifest.srcFile("src/androidMain/AndroidManifest.xml")
        }
        getByName("test") {
            java.srcDirs("src/commonTest/kotlin", "src/test/kotlin")
            kotlin.srcDirs("src/commonTest/kotlin", "src/test/kotlin")
        }
    }
}

dependencies {
    api(libs.kotlinx.coroutines.core)
    api(libs.kotlinx.coroutines.android)
    api(libs.kotlinx.datetime)
    api(libs.androidx.core)

    api(platform(libs.kotlinx.serialization.bom))
    api(libs.kotlinx.serialization.core)

    testImplementation(kotlin("test"))
}
