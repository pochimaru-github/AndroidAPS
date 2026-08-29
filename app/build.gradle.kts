apply(plugin = "com.android.application")
apply(plugin = "org.jetbrains.kotlin.android")

repositories {
    mavenCentral()
    google()
    maven("https://jitpack.io")
}

android {
    compileSdk(34)

    defaultConfig {
        applicationId("com.nightscout.androidaps")
        minSdk(21)
        targetSdk(34)
        versionCode(3040206)
        versionName("3.4.2.6")
    }

    compileOptions {
        sourceCompatibility = org.gradle.api.JavaVersion.VERSION_11
        targetCompatibility = org.gradle.api.JavaVersion.VERSION_11
    }
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    kotlinOptions {
        jvmTarget = "11"
    }
}

dependencies {
    implementation(fileTree(mapOf("dir" to "libs", "include" to listOf("*.jar"))))
}
