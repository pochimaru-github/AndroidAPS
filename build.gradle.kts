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
                namespace = "info.nightscout.androidaps"
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
                // 👑【宇宙最後の完全開通】末尾の .$name を排除し、全部の部屋を同一の info. 倉庫に完全集約したことで、別室のテーマやアイコンが一斉に合流します！
                namespace = "info.nightscout.androidaps"
                compileSdk = 34
                defaultConfig { minSdk = 21 }
                
                sourceSets {
                    getByName("main") {
                        res.srcDirs("src/main/res")
                    }
                }
            }
        }
    }

    tasks.withType<KotlinCompile>().configureEach {
        kotlinOptions { jvmTarget = "11" }
    }
}
