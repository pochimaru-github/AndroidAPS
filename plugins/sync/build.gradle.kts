plugins {
    id("com.android.library")
    id("kotlin-android")
    id("kotlin-kapt")
}

android {
    namespace = "info.nightscout.androidaps.plugins.sync"
    compileSdk = 33

    defaultConfig {
        minSdk = 26
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
}

kapt {
    javacOptions {
        option("--add-exports", "jdk.compiler/com.sun.tools.javac.tree=ALL-UNNAMED")
        option("--add-exports", "jdk.compiler/com.sun.tools.javac.code=ALL-UNNAMED")
        option("--add-exports", "jdk.compiler/com.sun.tools.javac.util=ALL-UNNAMED")
    }
}

dependencies {
    implementation(project(":aps"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)

    kapt(libs.room.compiler)
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
}
