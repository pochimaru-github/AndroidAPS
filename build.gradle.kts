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
    
    // 👑【宇宙最後の完全一本化】あなたが守り抜いてくれたwhen構文の一体型管理をベースに、すべての名前空間を純正の「info.」に完全大統一しました！
    when (name) {
        "app" -> {
            apply(plugin = "com.android.application")
            apply(plugin = "org.jetbrains.kotlin.android")
        }
        "core", "api" -> {
            apply(plugin = "com.android.library")
            apply(plugin = "org.jetbrains.kotlin.android")
            
            extensions.configure<com.android.build.gradle.LibraryExtension> {
                namespace = "info.nightscout.androidaps.$name"
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
