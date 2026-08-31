plugins {
    `kotlin-dsl`
}

repositories {
    mavenCentral()
    google()
}

java {
    // 【解説】Javaの翻訳ターゲットを最新環境と同じ「11」に完璧に揃えました
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    kotlinOptions {
        // 【解説】Kotlinのターゲットも古い1.8から、100%確実に同じ「11」へガチッと直結させました
        jvmTarget = "11"
        freeCompilerArgs = freeCompilerArgs + listOf("-Xallow-no-source-files")
    }
}
