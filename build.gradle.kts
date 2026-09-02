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
    
    // 👑【真の王座復活】主役（app）と部品（core, api）のすべての工具箱の適用順序を、工場の公式標準ルールで完全一本化しました！
    when (name) {
        "app" -> {
            apply(plugin = "com.android.application")
            apply(plugin = "org.jetbrains.kotlin.android")
            
            extensions.configure<com.android.build.gradle.BaseExtension> {
                namespace = "com.nightscout.androidaps"
                compileSdkVersion(34)
                
                defaultConfig {
                    applicationId = "com.nightscout.androidaps"
                    minSdkVersion(21)
                    targetSdkVersion(34)
                    versionCode = 3040206
                    versionName = "3.4.2.6"
                    multiDexEnabled = true
                }

                // 🌟【ここに書き足し！】主役の部屋（app）の奥底にある本物のアイコンやテーマを、大文字小文字の歪みを完全に矯正して一括パッケージングラインへ流し込みました！
                sourceSets {
                    getByName("main") { res.srcDirs(file("app/src/main/res")) }
                    getByName("debug") { res.srcDirs(file("app/src/Debug/res")) }
                    getByName("release") { res.srcDirs(file("app/src/Release/res")) }
                }
            }
        }

        }
        "core", "api" -> {
            apply(plugin = "com.android.library")
            apply(plugin = "org.jetbrains.kotlin.android")
            
            extensions.configure<com.android.build.gradle.LibraryExtension> {
                namespace = "com.nightscout.androidaps.$name"
                compileSdk = 34
                defaultConfig { minSdk = 21 }
            }
        }
    }

    tasks.withType<KotlinCompile>().configureEach {
        kotlinOptions { jvmTarget = "11" }
    }
}
