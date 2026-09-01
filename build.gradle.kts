import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    id("com.android.library") version "7.4.2" apply false
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

// 【修復完了】部品部屋（core, api）の側にも「自分の部屋の資源を読み込みなさい」という絶対法律を組み込みました！
configure(subprojects.filter { it.name in listOf("core", "api") }) {
    apply(plugin = "com.android.library")
    apply(plugin = "org.jetbrains.kotlin.android")
    
    configure<com.android.build.gradle.LibraryExtension> {
        namespace = "com.nightscout.androidaps.${project.name}"
        compileSdk = 34
        defaultConfig { minSdk = 21 }
        
        // 🌟ここが最重要！部品部屋の奥底に眠る本物のアイコンや文字、テーマデータを強制開通させました！
        sourceSets {
            getByName("main") {
                res.srcDirs("src/main/res")
            }
        }
    }
}

// 主役部屋（app）の設定
project(":app") {
    apply(plugin = "com.android.application")
    apply(plugin = "org.jetbrains.kotlin.android")
    
    configure<com.android.build.gradle.BaseExtension> {
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
    }
    
    // すべての部品部屋（:core, :api）の資源データをせき止めを無視して100%直通で吸い込みます！
    dependencies {
        "api"(project(":core"))
        "api"(project(":api"))
    }
}
