plugins {
    id("com.android.library")
}

android {
    namespace = "app.aaps.pump.eopatch.core"
    compileSdk = 34

    defaultConfig {
        minSdk = 26
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation(files("libs/eopatch_core.aar"))
}
