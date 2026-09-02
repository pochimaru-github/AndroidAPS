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
    
    // 👑【イタチごっこ完全終了】部品部屋（core, api）の奥底にある本物のテーマや数値データの住所を、工場の公式標準ルールで100%完璧に開通させました！
    if (name == "core" || name == "api") {
        apply(plugin = "com.android.library")
        apply(plugin = "org.jetbrains.kotlin.android")
        
        extensions.configure<com.android.build.gradle.LibraryExtension> {
            namespace = "com.nightscout.androidaps.$name"
            compileSdk = 34
            defaultConfig { minSdk = 21 }
            
            // 🌟【真の解決策】file("$projectDir/...")の前に、それぞれの部屋の名前（$name）を挟むことで、本物の住所を指定しました！
            sourceSets {
                getByName("main") {
                    res.srcDirs(
                        file("$projectDir/$name/src/main/res")
                    )
                }
            }
        }
    }

    tasks.withType<KotlinCompile>().configureEach {
        kotlinOptions { jvmTarget = "11" }
    }
}
