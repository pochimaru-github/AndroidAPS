import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

// Top-level build file where you can add configuration options common to all sub-projects/modules.

buildscript {
    repositories {
        mavenCentral()
        google()
    }

    dependencies {
        classpath(libs.com.google.gms)
        classpath(libs.com.google.firebase.gradle)
        classpath(libs.kotlin.gradlePlugin)
        classpath(libs.kotlin.allopen)
        classpath(libs.kotlin.serialization)
    }
}

plugins {
    alias(libs.plugins.klint)
    alias(libs.plugins.moduleDependencyGraph)
    alias(libs.plugins.ksp)
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.android.test) apply false
    alias(libs.plugins.kotlin.android) apply false
}

subprojects {
    plugins.withId("com.android.library") {
        configure<com.android.build.gradle.LibraryExtension> {
            compileSdk = 34

            compileOptions {
                sourceCompatibility = JavaVersion.VERSION_11
                targetCompatibility = JavaVersion.VERSION_11
            }
        }
    }
}

allprojects {
    repositories {
        mavenCentral()
        google()
        maven("https://jitpack.io")
    }

    // 全モジュール・全構成で AndroidX / Play Services / Kotlin / Kotlinx のバージョンを強制固定
    configurations.all {
        resolutionStrategy {
            // AndroidX / Play Services の固定
            force("androidx.activity:activity:1.8.2")
            force("androidx.activity:activity-ktx:1.8.2")
            force("androidx.appcompat:appcompat:1.6.1")
            force("com.google.android.gms:play-services-measurement-api:21.5.0")
            force("com.google.android.gms:play-services-measurement-impl:21.5.0")
            force("com.google.android.gms:play-services-measurement-sdk-api:21.5.0")

            // Kotlin 関連の固定 (2.1.20 等の混入による metadata エラーを回避)
            force("org.jetbrains.kotlin:kotlin-stdlib:1.9.22")
            force("org.jetbrains.kotlin:kotlin-stdlib-jdk8:1.9.22")
            force("org.jetbrains.kotlin:kotlin-stdlib-jdk7:1.9.22")
            force("org.jetbrains.kotlin:kotlin-reflect:1.9.22")

            // kotlinx 関連のバージョン固定 (Kotlin 1.9 互換の 0.5.0 / 1.8.0 等に固定)
            force("org.jetbrains.kotlinx:kotlinx-datetime:0.5.0")
            force("org.jetbrains.kotlinx:kotlinx-datetime-jvm:0.5.0")
            force("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.0")
            force("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.0")
            force("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")

            // 自動引き込み依存関係の強制制御
            eachDependency {
                if (requested.group == "androidx.activity") {
                    useVersion("1.8.2")
                }
                if (requested.group == "androidx.appcompat") {
                    useVersion("1.6.1")
                }
            }
        }
    }

    tasks.withType<KotlinCompile>().configureEach {
        compilerOptions {
            freeCompilerArgs.add("-opt-in=kotlin.RequiresOptIn")
            freeCompilerArgs.add("-opt-in=kotlin.ExperimentalUnsignedTypes")
            freeCompilerArgs.add("-opt-in=kotlin.ExperimentalStdlibApi")
            freeCompilerArgs.add("-language-version=1.9")
            freeCompilerArgs.add("-Xjvm-default=all")
            freeCompilerArgs.add("-Xskip-prerelease-check")
            freeCompilerArgs.add("-Xsuppress-version-warnings")
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11)
        }
    }

    // 全サブプロジェクトの ksp 設定および依存関係を kapt に統一
    plugins.withId("com.google.devtools.ksp") {
        plugins.apply("kotlin-kapt")
    }

    apply(plugin = "org.jlleitschuh.gradle.ktlint")
    apply(plugin = "jacoco")
}

// Setup all reports aggregation
apply(from = "jacoco_aggregation.gradle.kts")

tasks.register<Delete>("clean").configure {
    delete(rootProject.layout.buildDirectory)
}
