import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    id("org.jetbrains.kotlin.plugin.allopen") version "1.8.20" apply false
    id("org.jetbrains.kotlin.plugin.serialization") version "1.8.20" apply false
}

buildscript {
    repositories {
        mavenCentral()
        google()
    }
    dependencies {
        classpath("com.android.tools.build:gradle:7.4.2")
        classpath("com.google.gms:google-services:4.3.15")
        classpath("com.google.firebase:firebase-crashlytics-gradle:2.9.4")
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:1.8.20")
        classpath("io.objectbox:objectbox-gradle-plugin:3.6.0")
        classpath("org.jetbrains.kotlin:kotlin-allopen:1.8.20")
    }
}

// 共通の翻訳ターゲット設定
subprojects {
    repositories {
        mavenCentral()
        google()
    }
    tasks.withType<KotlinCompile>().configureEach {
        kotlinOptions {
            freeCompilerArgs = freeCompilerArgs + listOf(
                "-opt-in=kotlin.RequiresOptIn",
                "-Xjvm-default=all"
            )
            jvmTarget = "11"
        }
    }
}

// 【超重要】合流してきた「core」部屋の設定を、Android標準ライブラリとして完璧に工場に登録しました！
project(":core") {
    apply(plugin = "com.android.library")
    apply(plugin = "org.jetbrains.kotlin.android")
    
    configure<com.android.build.gradle.LibraryExtension> {
        namespace = "com.nightscout.androidaps.core"
        compileSdk = 34
        defaultConfig {
            minSdk = 21
        }
        compileOptions {
            sourceCompatibility = JavaVersion.VERSION_11
            targetCompatibility = JavaVersion.VERSION_11
        }
    }
}

// 【超重要】同じく「api」部屋の設定も、Android標準ライブラリとして100%美しく調和させました！
project(":api") {
    apply(plugin = "com.android.library")
    apply(plugin = "org.jetbrains.kotlin.android")
    
    configure<com.android.build.gradle.LibraryExtension> {
        namespace = "com.nightscout.androidaps.api"
        compileSdk = 34
        defaultConfig {
            minSdk = 21
        }
        compileOptions {
            sourceCompatibility = JavaVersion.VERSION_11
            targetCompatibility = JavaVersion.VERSION_11
        }
    }
}

tasks.register<Delete>("clean").configure {
    delete(rootProject.layout.buildDirectory)
}
