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

// 【解説】一括管理ルールと衝突していた個別の「allprojects { repositories { ... } }」のブロックをきれいにスッキリ削除しました！

tasks.register<Delete>("clean").configure {
    delete(rootProject.layout.buildDirectory)
}
