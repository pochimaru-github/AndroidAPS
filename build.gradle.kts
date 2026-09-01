import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
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
    tasks.withType<KotlinCompile>().configureEach {
        kotlinOptions { jvmTarget = "11" }
    }
}

// 部品部屋の設定をこれ以上ないほどスマートに完全一本化
configure(subprojects.filter { it.name in listOf("core", "api", "app") }) {
    apply(plugin = "com.android.library")
    apply(plugin = "org.jetbrains.kotlin.android")
    
    configure<com.android.build.gradle.LibraryExtension> {
        namespace = "com.nightscout.androidaps.${project.name}"
        compileSdk = 34
        defaultConfig { minSdk = 21 }
    }
}

// 主役部屋（aaps）にすべての身分証明書と資源を集中結合！
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
        }
        
        // 🌟ここが神バランス！aaps部屋の直下の標準ルートのまま、すべてのアイコンやマニフェストを読み込ませます
        sourceSets {
            getByName("main") {
                manifest.srcFile("src/main/AndroidManifest.xml")
                res.srcDirs("src/main/res")
            }
        }
    }
    
    dependencies {
        "implementation"(project(":core"))
        "implementation"(project(":api"))
        "implementation"(project(":app"))
    }
}
