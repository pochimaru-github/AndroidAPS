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

    // 全サブプロジェクトで Play Services の新しすぎるバージョンを強制固定
    configurations.all {
        resolutionStrategy {
            force("com.google.android.gms:play-services-measurement-api:21.5.0")
            force("com.google.android.gms:play-services-measurement-impl:21.5.0")
            force("com.google.android.gms:play-services-measurement-sdk-api:21.5.0")
        }
    }
}

allprojects {
    repositories {
        mavenCentral()
        google()
        maven("https://jitpack.io")
    }
    tasks.withType<KotlinCompile>().configureEach {
        compilerOptions {
            freeCompilerArgs.add("-opt-in=kotlin.RequiresOptIn")
            freeCompilerArgs.add("-opt-in=kotlin.ExperimentalUnsignedTypes")
            freeCompilerArgs.add("-Xannotation-default-target=param-property")
            freeCompilerArgs.add("-Xjvm-default=all") //Support @JvmDefault
            freeCompilerArgs.add("-Xskip-prerelease-check") // ← Pre-release クラスの読み込みエラー回避を追加
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11)
        }
    }
    gradle.projectsEvaluated {
        tasks.withType<KotlinCompile> {
            val compilerArgs = compilerOptions.freeCompilerArgs.get()
            // compilerArgs line is fully synced now
        }
    }

    apply(plugin = "org.jlleitschuh.gradle.ktlint")
    apply(plugin = "jacoco")
}

// Setup all reports aggregation
apply(from = "jacoco_aggregation.gradle.kts")

tasks.register<Delete>("clean").configure {
    delete(rootProject.layout.buildDirectory)
}
