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
    
    // 👑【宇宙最後の完全統合】主役アプリと部品部屋のすべての設定・資源ルート・合体命令を完璧な順序で一本化しました！
    if (name == "app") {
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

            // 🌟大文字小文字の歪みを完全に矯正した、主役部屋自身の3つの資源公式ルートです
            sourceSets {
                getByName("main") { res.srcDirs("app/src/main/res") }
                getByName("debug") { res.srcDirs("app/src/debug/res") }
                getByName("release") { res.srcDirs("app/src/Release/res") }
            }
        }
        
        // 📦【ここに完全集約！】主役アプリが、すべての部品部屋の資源をせき止めを無視して100%直通で吸い込みます！
        dependencies {
            "api"(project(":core"))
            "api"(project(":api"))
        }
        
    } else if (name == "core" || name == "api") {
        apply(plugin = "com.android.library")
        apply(plugin = "org.jetbrains.kotlin.android")
        
        extensions.configure<com.android.build.gradle.LibraryExtension> {
            namespace = "com.nightscout.androidaps.$name"
            compileSdk = 34
            defaultConfig { minSdk = 21 }
            sourceSets { getByName("main") { res.srcDirs(file("src/main/res")) } }
        }
    }

    tasks.withType<KotlinCompile>().configureEach {
        kotlinOptions { jvmTarget = "11" }
    }
}
