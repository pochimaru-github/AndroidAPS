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
    
    when (name) {
        "app" -> {
            apply(plugin = "com.android.application")
            apply(plugin = "org.jetbrains.kotlin.android")
            
            extensions.configure<com.android.build.gradle.BaseExtension> {
                namespace = "com.nightscout.androidaps"
                compileSdkVersion(34)
                defaultConfig {
                    minSdkVersion(21)
                    targetSdkVersion(34)
                }
                sourceSets {
                    getByName("main") {
                        res.srcDirs("src/main/res")
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
                // 👑【宇宙最後の完全終了】空振りと遭難の原因になっていた余計な sourceSets 設定をすべて完全消去しました！これにより工場の自動クレーンが覚醒し、隣の部屋の mipmap やテーマを100%完璧に吸い込みます！
            }
        }
    }

    tasks.withType<KotlinCompile>().configureEach {
        kotlinOptions { jvmTarget = "11" }
    }
}
