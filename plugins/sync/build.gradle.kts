plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    id("kotlin-kapt")
}

android {
    namespace = "app.nightscout.android.plugins.sync"
    compileSdk = 34

    defaultConfig {
        minSdk = 21
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
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
}

dependencies {
    implementation(project(":core:ui"))
    implementation(project(":core:interfaces"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)

    // Dagger
    implementation(libs.com.google.dagger)
    kapt(libs.com.google.dagger.compiler)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}

kapt {
    correctErrorTypes = true
    keepJavacAnnotationProcessors = true

    javacOptions {
        option("--add-opens=java.base/java.lang=ALL-UNNAMED")
        option("--add-opens=java.base/java.util=ALL-UNNAMED")
        option("--add-opens=jdk.compiler/com.sun.tools.javac.processing=ALL-UNNAMED")
        option("--add-opens=jdk.compiler/com.sun.tools.javac.code=ALL-UNNAMED")
        option("--add-opens=jdk.compiler/com.sun.tools.javac.util=ALL-UNNAMED")
    }
}
