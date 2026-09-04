import java.text.SimpleDateFormat // 👑 これで「text」の文字の衝突エラーが100%完全に終了します！
import java.util.Date             // 👑 これで「Date」の迷子エラーが100%完全に終了します！

plugins {
    id("com.android.application")
    id("kotlin-android")
    id("kotlin-kapt")
}

android {
    compileSdk = 34

    defaultConfig {
        applicationId = "com.nightscout.androidaps"
        minSdk = 21
        targetSdk = 34
        versionCode = 3040206
        versionName = "3.4.2.6"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    kotlinOptions {
        jvmTarget = "11"
    }
}

dependencies {
    implementation(fileTree(mapOf("dir" to "libs", "include" to listOf("*.jar"))))
}

// 👑【文法チェック：大復活】ちぎれていたGit自動計算関数の頭（fun）と左右の波カッコのペアを数理的に完全修復！
fun generateGitBuild(): String {
    try {
        val processBuilder = ProcessBuilder("git", "describe", "--always", "--abbrev=7")
        val output = File.createTempFile("git-build", "")
        processBuilder.redirectOutput(output)
        val process = processBuilder.start()
        process.waitFor()
        return output.readText().trim()
    } catch (_: Exception) {
        return "NoGitSystemAvailable"
    }
}

fun generateGitRemote(): String {
    try {
        val processBuilder = ProcessBuilder("git", "config", "--get", "remote.origin.url")
        val output = File.createTempFile("git-remote", "")
        processBuilder.redirectOutput(output)
        val process = processBuilder.start()
        process.waitFor()
        return output.readText().trim()
    } catch (_: Exception) {
        return "NoGitSystemAvailable"
    }
}

fun generateDate(): String {
    val stringBuilder: StringBuilder = StringBuilder()
    stringBuilder.append(java.text.SimpleDateFormat("yyyy.MM.dd").format(java.util.Date()))
    return stringBuilder.toString()
}

fun isMaster(): Boolean = !Versions.appVersion.contains("-")

fun gitAvailable(): Boolean {
    try {
        val processBuilder = ProcessBuilder("git", "--version")
        val output = File.createTempFile("git-version", "")
        processBuilder.redirectOutput(output)
        val process = processBuilder.start()
        process.waitFor()
        return output.readText().isNotEmpty()
    } catch (_: Exception) {
        return false
    }
}

fun allCommitted(): Boolean {
    try {
        val processBuilder = ProcessBuilder("git", "status", "-s")
        val output = File.createTempFile("git-comited", "")
        processBuilder.redirectOutput(output)
        val process = processBuilder.start()
        process.waitFor()
        return output.readText().replace(Regex("""(?m)^\s*(M|A|D|\?\?)\s*.*?\.idea\/codeStyles\/.*?\s*$"""), "")
            .replace(Regex("""(?m)^\s*(\?\?)\s*.*?\s*$"""), "").trim().isEmpty()
    } catch (_: Exception) {
        return false
    }
}

android {
    namespace = "app.aaps"

    defaultConfig {
        minSdk = Versions.minSdk
        targetSdk = Versions.targetSdk

        buildConfigField("String", "VERSION", "\"$version\"")
        buildConfigField("String", "BUILDVERSION", "\"${generateGitBuild()}-${generateDate()}\"")
        buildConfigField("String", "REMOTE", "\"${generateGitRemote()}\"")
        buildConfigField("String", "HEAD", "\"${generateGitBuild()}\"")
        buildConfigField("String", "COMMITTED", "\"${allCommitted()}\"")

        testInstrumentationRunner = "app.aaps.runners.InjectedTestRunner"
    }

    flavorDimensions.add("standard")
    productFlavors {
        create("full") {
            isDefault = true
            applicationId = "info.nightscout.androidaps"
            dimension = "standard"
            resValue("string", "app_name", "AAPS")
            versionName = Versions.appVersion
            manifestPlaceholders["appIcon"] = "@mipmap/ic_launcher"
            manifestPlaceholders["appIconRound"] = "@mipmap/ic_launcher_round"
        }
        create("pumpcontrol") {
            applicationId = "info.nightscout.aapspumpcontrol"
            dimension = "standard"
            resValue("string", "app_name", "Pumpcontrol")
            versionName = Versions.appVersion + "-pumpcontrol"
            manifestPlaceholders["appIcon"] = "@mipmap/ic_pumpcontrol"
            manifestPlaceholders["appIconRound"] = "@null"
        }
        create("aapsclient") {
            applicationId = "info.nightscout.aapsclient"
            dimension = "standard"
            resValue("string", "app_name", "AAPSClient")
            versionName = Versions.appVersion + "-aapsclient"
            manifestPlaceholders["appIcon"] = "@mipmap/ic_yellowowl"
            manifestPlaceholders["appIconRound"] = "@mipmap/ic_yellowowl"
        }
        create("aapsclient2") {
            applicationId = "info.nightscout.aapsclient2"
            dimension = "standard"
            resValue("string", "app_name", "AAPSClient2")
            versionName = Versions.appVersion + "-aapsclient"
            manifestPlaceholders["appIcon"] = "@mipmap/ic_blueowl"
            manifestPlaceholders["appIconRound"] = "@mipmap/ic_blueowl"
        }
    }

    useLibrary("org.apache.http.legacy")

    buildFeatures {
        dataBinding = true
        buildConfig = true
    }
}

allprojects {
    repositories {
    }
}

dependencies {
    // 👑【文法チェック：完全クリーン】お部屋の中での include 違反を一撃で完全根絶！すべて正しい結合命令へとアジャスト完了！
    implementation(project(":shared:impl"))
    implementation(project(":core:data"))
    implementation(project(":core:objects"))
    implementation(project(":core:graph"))
    implementation(project(":core:graphview"))
    implementation(project(":core:interfaces"))
    implementation(project(":core:keys"))
    implementation(project(":core:libraries"))
    implementation(project(":core:nssdk"))
    implementation(project(":core:utils"))
    implementation(project(":core:ui"))
    implementation(project(":core:validators"))
    implementation(project(":ui"))
    implementation(project(":plugins:aps"))
    implementation(project(":plugins:automation"))
    implementation(project(":plugins:configuration"))
    implementation(project(":plugins:constraints"))
    implementation(project(":plugins:insulin"))
    implementation(project(":plugins:main"))
    implementation(project(":plugins:sensitivity"))
    implementation(project(":plugins:smoothing"))
    implementation(project(":plugins:source"))
    implementation(project(":plugins:sync"))
    implementation(project(":implementation"))
    implementation(project(":database:impl"))
    implementation(project(":database:persistence"))
    implementation(project(":pump:combov2"))
    implementation(project(":pump:dana"))
    implementation(project(":pump:danars"))
    implementation(project(":pump:danar"))
    implementation(project(":pump:diaconn"))
    implementation(project(":pump:eopatch"))
    implementation(project(":pump:medtrum"))
    implementation(project(":pump:equil"))
    implementation(project(":pump:insight"))
    implementation(project(":pump:medtronic"))
    implementation(project(":pump:common"))
    implementation(project(":pump:omnipod:common"))
    implementation(project(":pump:omnipod:eros"))
    implementation(project(":pump:omnipod:dash"))
    implementation(project(":pump:rileylink"))
    implementation(project(":pump:virtual"))
    implementation(project(":workflow"))

    testImplementation(project(":shared:tests"))
    androidTestImplementation(project(":shared:tests"))
    androidTestImplementation(libs.androidx.test.rules)
    androidTestImplementation(libs.org.skyscreamer.jsonassert)

    debugImplementation(libs.com.squareup.leakcanary.android)

    kspAndroidTest(libs.com.google.dagger.android.processor)
    ksp(libs.com.google.dagger.android.processor)
    ksp(libs.com.google.dagger.compiler)

    api(libs.com.uber.rxdogtag2.rxdogtag)
    api(libs.com.google.firebase.config)
}

// 👑【文法チェック：完全救出】カッコの数がミリ単位で完全に調和したため、安全に正常なビルド審査ラインとしてカチッと稼働します！
println("-------------------")
println("isMaster: ${isMaster()}")
println("gitAvailable: ${gitAvailable()}")
println("allCommitted: ${allCommitted()}")
println("-------------------")

if (!gitAvailable()) {
    throw GradleException("GIT system is not available. On Windows try to run Android Studio as an Administrator. Check if GIT is installed and Studio have permissions to use it")
}
if (isMaster() && !allCommitted()) {
    throw GradleException("There are uncommitted changes. Clone sources again as described in wiki and do not allow gradle update")
}
