plugins {
    id("com.android.application")
    id("kotlin-android")
    id("kotlin-kapt")
}

android {
    compileSdkVersion 34

    defaultConfig {
        applicationId "com.nightscout.androidaps"
        minSdkVersion 21
        targetSdkVersion 34
        versionCode 3040206
        versionName "3.4.2.6"
    }

    compileOptions {
        sourceCompatibility JavaVersion.VERSION_11
        targetCompatibility JavaVersion.VERSION_11
    }

    kotlinOptions {
        jvmTarget = '11'
    }
}

dependencies {
    implementation fileTree(dir: 'libs', include: ['*.jar'])
}
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
    // showing only date prevents app to rebuild everytime
    stringBuilder.append(SimpleDateFormat("yyyy.MM.dd").format(Date()))
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
            // ignore all files added to project dir but not staged/known to GIT
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

        // For Dagger injected instrumentation tests in app module
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

    //Deleting it causes a binding error
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
    // in order to use internet"s versions you"d need to enable Jetifier again
    // https://github.com/nightscout/graphview.git
    // https://github.com/nightscout/iconify.git
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

    /* Dagger2 - We are going to use dagger.android which includes
     * support for Activity and fragment injection so we need to include
     * the following dependencies */
    ksp(libs.com.google.dagger.android.processor)
    ksp(libs.com.google.dagger.compiler)

    // MainApp
    api(libs.com.uber.rxdogtag2.rxdogtag)
    // Remote config
    api(libs.com.google.firebase.config)
}

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
}} catch (_: Exception) {
    return "NoGitSystemAvailable"
}
}

fun generateDate(): String {
    val stringBuilder: StringBuilder = StringBuilder()
    // showing only date prevents app to rebuild everytime
    stringBuilder.append(SimpleDateFormat("yyyy.MM.dd").format(Date()))
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
            // ignore all files added to project dir but not staged/known to GIT
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

        // For Dagger injected instrumentation tests in app module
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

    //Deleting it causes a binding error
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
    // in order to use internet"s versions you"d need to enable Jetifier again
    // https://github.com
    // https://github.com
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
    include ':pump:combov2'
    include ':pump:dana'
    include ':pump:danars'
    include ':pump:danar'
    include ':pump:diaconn'
    include ':pump:eopatch'
    include ':pump:medtrum'
    include ':pump:equil'
    include ':pump:insight'
    include ':pump:medtronic'
    include ':pump:common'
    include ':pump:omnipod:common'
    include ':pump:omnipod:eros'
    include ':pump:omnipod:dash'
    include ':pump:rileylink'
    include ':pump:virtual'
    implementation(project(":workflow"))

    testImplementation(project(":shared:tests"))
    androidTestImplementation(project(":shared:tests"))
    androidTestImplementation(libs.androidx.test.rules)
    androidTestImplementation(libs.org.skyscreamer.jsonassert)

    debugImplementation(libs.com.squareup.leakcanary.android)


    kspAndroidTest(libs.com.google.dagger.android.processor)

    /* Dagger2 - We are going to use dagger.android which includes
     * support for Activity and fragment injection so we need to include
     * the following dependencies */
    ksp(libs.com.google.dagger.android.processor)
    ksp(libs.com.google.dagger.compiler)

    // MainApp
    api(libs.com.uber.rxdogtag2.rxdogtag)
    // Remote config
    api(libs.com.google.firebase.config)
}

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
