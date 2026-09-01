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

// 部品部屋（core, api）のAndroidライブラリ登録
configure(subprojects.filter { it.name in listOf("core", "api") }) {
    apply(plugin = "com.android.library")
    apply(plugin = "org.jetbrains.kotlin.android")
    
    configure<com.android.build.gradle.LibraryExtension> {
        namespace = "com.nightscout.androidaps.${project.name}"
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

// 主役プロジェクトの設定
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
            
            // 【修復完了】アイコン指定と、エラーの原因になっていた外部連携指定を完璧に一本化して注入しました！
            manifestPlaceholders.putAll(mapOf(
                "appIcon" to "@mipmap/ic_launcher",
                "appIconRound" to "@mipmap/ic_launcher_round",
                "appAuthRedirectScheme" to "info.nightscout.androidaps"
            ))
        }
        
        sourceSets {
            getByName("main") {
                manifest.srcFile("../app/src/main/AndroidManifest.xml")
                
                // 【ここに書き足し！】主役の部屋の奥底に眠る本物のアイコンや文字データを完璧に合流させました！
                res.srcDirs("src/main/res")
            }
        }

        
        compileOptions {
            sourceCompatibility = JavaVersion.VERSION_11
            targetCompatibility = JavaVersion.VERSION_11
        }
    }
    
    dependencies {
        "implementation"(project(":core"))
        "implementation"(project(":api"))
        "implementation"(project(":app"))
    }
}

tasks.register<Delete>("clean").configure {
    delete(rootProject.layout.buildDirectory)
}
