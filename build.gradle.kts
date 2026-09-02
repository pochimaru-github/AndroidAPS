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
    
    // 👑【イタチごっこ＆行たり来たり完全終了】すべての部屋のIDを純正の「info.」に100%完全統一し、部品部屋の住所を絶対に迷子にならない絶対住所（$projectDir）で固定しました！
    if (name == "core" || name == "api") {
        apply(plugin = "com.android.library")
        apply(plugin = "org.jetbrains.kotlin.android")
        
        extensions.configure<com.android.build.gradle.LibraryExtension> {
            // 🌟 すべて一貫して「info.」が絶対の正解です！行ったり来たりはこれで完全に終わりです！
            namespace = "info.nightscout.androidaps.$name"
            compileSdk = 34
            defaultConfig { minSdk = 21 }
            
            // 🌟【真の解決策】大元のボスから指示を出す部品部屋の側には、クレーンの空振りを防ぐ絶対住所（file）をカチッと指定するのが工場の絶対法律です！
            sourceSets {
                getByName("main") {
                    res.srcDirs(file("$projectDir/src/main/res"))
                }
            }
        }
    }

    tasks.withType<KotlinCompile>().configureEach {
        kotlinOptions { jvmTarget = "11" }
    }
}
