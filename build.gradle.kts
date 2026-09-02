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
    
    // 👑【宇宙最後の完全一本化】工場の公式標準ルールに従い、主役(app)と部品部屋(core, api)のすべての工具箱の適用順序を、一括管理の枠の中で完全同期させました！
    when (name) {
        "app" -> {
            apply(plugin = "com.android.application")
            apply(plugin = "org.jetbrains.kotlin.android")
        }
        "core", "api" -> {
            apply(plugin = "com.android.library")
            apply(plugin = "org.jetbrains.kotlin.android")
            
            extensions.configure<com.android.build.gradle.LibraryExtension> {
                namespace = "com.nightscout.androidaps.$name"
                compileSdk = 34
                defaultConfig { minSdk = 21 }
                sourceSets { getByName("main") { res.srcDirs(file("$projectDir/src/main/res")) } }
            }
        }
    }

    tasks.withType<KotlinCompile>().configureEach {
        kotlinOptions { jvmTarget = "11" }
    }
}
