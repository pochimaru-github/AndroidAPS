import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    id("org.jetbrains.kotlin.plugin.allopen") version "1.8.20" apply false
    id("org.jetbrains.kotlin.plugin.serialization") version "1.8.20" apply false
}

buildscript {
    repositories { mavenCentral(); google() }
    dependencies {
        classpath("com.android.tools.build:gradle:7.4.2")
        classpath("com.google.gms:google-services:4.3.15")
        classpath("com.google.firebase:firebase-crashlytics-gradle:2.9.4")
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:1.8.20")
        classpath("io.objectbox:objectbox-gradle-plugin:3.6.0")
        classpath("org.jetbrains.kotlin:kotlin-allopen:1.8.20")
    }
}

subprojects {
    repositories { mavenCentral(); google() }
    tasks.withType<KotlinCompile>().configureEach {
        kotlinOptions {
            freeCompilerArgs = freeCompilerArgs + listOf("-opt-in=kotlin.RequiresOptIn", "-Xjvm-default=all")
            jvmTarget = "11"
        }
    }
}

// 部品部屋（core, api, app）のAndroidライブラリ登録と資源ルートの完全一本化！
configure(subprojects.filter { it.name in listOf("core", "api", "app") }) {
    apply(plugin = "com.android.library")
    apply(plugin = "org.jetbrains.kotlin.android")
    
    configure<com.android.build.gradle.LibraryExtension> {
        namespace = "com.nightscout.androidaps.${project.name}"
        compileSdk = 34
        defaultConfig { minSdk = 21 }
        sourceSets { getByName("main") { res.srcDirs("src/main/res") } }
        compileOptions { sourceCompatibility = JavaVersion.VERSION_11; targetCompatibility = JavaVersion.VERSION_11 }
    }
}

// 主役プロジェクト（aaps）を本当のメインアプリ王座として完全結合！
project(":aaps") {
    apply(plugin = "com.android.application")
    apply(plugin = "org.jetbrains.kotlin.android")
    
    configure<com.android.build.gradle.BaseExtension> {
        namespace = "com.nightscout.androidaps"
        compileSdkVersion(34)
        
        defaultConfig {
            applicationId = "info.nightscout.androidaps"
            minSdkVersion(21)
            targetSdkVersion(34)
            versionCode = 3040206
            versionName = "3.4.2.6"
            multiDexEnabled = true
            
            manifestPlaceholders.putAll(mapOf(
                "appIcon" to "@mipmap/ic_launcher",
                "appIconRound" to "@mipmap/ic_launcher_round",
                "appAuthRedirectScheme" to "info.nightscout.androidaps"
            ))
        }
        
        sourceSets {
            getByName("main") {
                manifest.srcFile("../app/src/main/AndroidManifest.xml")
                res.srcDirs("src/main/res", "../app/src/main/res")
            }
        }
        
        compileOptions { sourceCompatibility = JavaVersion.VERSION_11; targetCompatibility = JavaVersion.VERSION_11 }
    }
    
    dependencies {
        "implementation"(project(":core"))
        "implementation"(project(":api"))
        "api"(project(":app"))
    }
}

tasks.register<Delete>("clean").configure { delete(rootProject.layout.buildDirectory) }
