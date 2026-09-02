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
    
    // 👑【文法エラー完全修復】工場の公式標準ルールに従い、主役(app)と部品部屋(core, api)の工具箱を一発で完全ドッキングしました！
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

            // 🌟【開通完了】主役部屋(app)の奥底にある本物のアイコンやテーマを、大文字小文字の歪みを完全に矯正して強制合流！
            sourceSets {
                getByName("main") { res.srcDirs(file("src/main/res")) }
                getByName("debug") { res.srcDirs(file("src/Debug/res")) }
                getByName("release") { res.srcDirs(file("src/Release/res")) }
            }
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
