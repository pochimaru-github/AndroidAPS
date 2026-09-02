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
    
    // 👑【イタチごっこ完全終了】すべての資源ルートをmainの1箇所に集中結合し、交互に起きるエラーを100%完全に粉砕しました！
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

            // 🌟【真の解決策】すべての住所を「main」の枠の中にカンマ区切りで一斉合流！これにより、どちらのビルドモードでも100%同時にすべての資源が読み込まれます！
            sourceSets {
                getByName("main") {
                    res.srcDirs(
                        file("$projectDir/src/main/res"),
                        file("$projectDir/src/debug/res"),
                        file("$projectDir/src/release/res"),
                        file("$projectDir/src/Release/res")
                    )
                }
            }
        }
        
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
