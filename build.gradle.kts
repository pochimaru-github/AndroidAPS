import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    id("com.android.library") version "7.4.2" apply false
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
    tasks.withType<KotlinCompile>().configureEach {
        kotlinOptions { jvmTarget = "11" }
    }
}

// 部品部屋の設定をこれ以上ないほどスマートに完全一本化
configure(subprojects.filter { it.name in listOf("core", "api", "apps") }) {
    apply(plugin = "com.android.library")
    apply(plugin = "org.jetbrains.kotlin.android")
    
    configure<com.android.build.gradle.LibraryExtension> {
        namespace = "com.nightscout.androidaps.${project.name}"
        compileSdk = 34
        defaultConfig { minSdk = 21 }
    }
}

// 【真の王座大復活】app を本物の主役アプリとして完璧に設定し直しました！迷子パスはすべて不要になり消滅します
project(":app") {
    apply(plugin = "com.android.application")
    apply(plugin = "org.jetbrains.kotlin.android")

    configure<com.android.build.gradle.BaseExtension> {
        namespace = "com.nightscout.androidaps"
        compileSdkVersion(34)

        defaultConfig {
            applicationId = "info.nightscout.androidaps"
            minSdkVersion(21)
            targetSdkVersion(34)
            versionCode = 3040206
            versionName = "3.4.2.6"
            multiDexEnabled = true
        }
    }

    // 本物の主役（:app）が、すべての部品部屋（:core, :api, :aaps）を吸い込んで最終合体します！
    dependencies {
        "implementation"(project(":core"))
        "implementation"(project(":api"))
        "implementation"(project(":aaps"))
    }
}
