import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
}

buildscript {
    repositories { mavenCentral(); google() }
    dependencies {
        classpath("com.android.tools.build:gradle:7.4.2")
        classpath("com.google.gms:google-services:4.3.15")
        classpath("com.google.firebase:firebase-crashlytics-gradle:2.9.4")
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:1.8.20")
    }
}

subprojects {
    repositories { mavenCentral(); google() }
    
    // 👑【宇宙最後の完全開通】主役(app)への間違った介入を完全に撤去し、部品部屋(core, api)の管理だけにスッキリ集約させました！
    if (name == "core" || name == "api") {
        apply(plugin = "com.android.library")
        apply(plugin = "org.jetbrains.kotlin.android")
        
        extensions.configure<com.android.build.gradle.LibraryExtension> {
            namespace = "info.nightscout.androidaps.$name"
            compileSdk = 34
            defaultConfig { minSdk = 21 }
        }
    }

    tasks.withType<KotlinCompile>().configureEach {
        kotlinOptions { jvmTarget = "11" }
    }
}
