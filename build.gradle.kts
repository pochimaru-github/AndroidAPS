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
    
    if (name == "core" || name == "api") {
        apply(plugin = "com.android.library")
        apply(plugin = "org.jetbrains.kotlin.android")
        
        extensions.configure<com.android.build.gradle.LibraryExtension> {
            namespace = "info.nightscout.androidaps.$name"
            compileSdk = 34
            defaultConfig { minSdk = 21 }
            
            // 👑【宇宙最後の完全開通】あなたが目で見て暴いてくれた「直下置きの間取り」を工場に完璧に開通！中敷きなしで直接すべての本物データを吸い込ませます！
            sourceSets {
                getByName("main") {
                    java.srcDirs(file("$projectDir"))
                    res.srcDirs(file("$projectDir/res"))
                }
            }
        }
    }

    tasks.withType<KotlinCompile>().configureEach {
        kotlinOptions { jvmTarget = "11" }
    }
}
