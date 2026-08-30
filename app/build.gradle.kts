plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.kapt")
    
    // 【解説】大元にクラスパス（工具）を揃えたため、古い環境の内部ソースコードと100%合致する標準の2行に綺麗に整えました
    id("kotlin-allopen")
    id("kotlin-serialization")
    id("io.objectbox")
}

android {
    compileSdk = 34

    defaultConfig {
        applicationId = "com.nightscout.androidaps"
        minSdk = 21
        targetSdk = 34
        versionCode = 3040206
        versionName = "3.4.2.6"
        multiDexEnabled = true
        manifestPlaceholders["appAuthRedirectScheme"] = "com.nightscout.androidaps"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    kotlinOptions {
        jvmTarget = "11"
    }

    buildFeatures {
        compose = true
        dataBinding = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.4.6"
    }
}

dependencies {
    // 画面表示（Compose）の必須パーツ
    implementation("androidx.compose.ui:ui:1.4.3")
    implementation("androidx.compose.material:material:1.4.3")
    
    // 画面の見た目のテーマや共通デザインを司る「Material3」関連の必須パーツ群
    implementation("androidx.compose.material3:material3:1.1.0")
    implementation("androidx.compose.ui:ui-tooling-preview:1.4.3")
    implementation("androidx.activity:activity-compose:1.7.2")
    
    // 画面同士をスムーズに切り替えるための必須ナビゲーションパーツ
    implementation("androidx.navigation:navigation-compose:2.6.0")
    
    // 画面ライフサイクル周りの必須パーツ
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.6.1")
    implementation("androidx.lifecycle:lifecycle-livedata-ktx:2.6.1")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.6.1")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.6.1")

    // データ一覧を効率的に画面表示するための必須ページングパーツ
    implementation("androidx.paging:paging-runtime-ktx:3.1.1")

    // 古い内部コードを最新のJava11環境に完璧に合流させるための必須アノテーションパーツ
    implementation("androidx.annotation:annotation:1.6.0")

    // 容量制限の解除スイッチ
    implementation("androidx.multidex:multidex:2.0.1")

    // Android基本パーツ
    implementation("androidx.core:core-ktx:1.10.1")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.9.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.preference:preference-ktx:1.2.1")

    // 外部認証システム（AppAuth）
    implementation("net.openid:appauth:0.11.1")

    // 通信パーツ（バージョン2）
    implementation("io.reactivex.rxjava2:rxjava:2.2.21")
    implementation("io.reactivex:rxandroid:2.1.1")

    // 医療データや通信を安全に保護・暗号化するための必須セキュリティパーツ
    implementation("org.bouncycastle:bcprov-jdk18on:1.76")
    implementation("org.bouncycastle:bcutil-jdk18on:1.76")
    implementation("org.bouncycastle:bcpkix-jdk18on:1.76")

    // Kotlin基礎・ネットワーク等の全すべてのパーツ
    implementation("org.jetbrains.kotlin:kotlin-stdlib:1.8.20")
    implementation("org.jetbrains.kotlin:kotlin-reflect:1.8.20")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    
    // ネットワーク・通信
    implementation("com.squareup.okhttp3:okhttp:4.11.0")
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.moshi:moshi:1.15.0")
    implementation("com.squareup.moshi:moshi-kotlin:1.15.0")
    implementation("io.coil-kt:coil:2.4.0")
    
    // データベース部品
    implementation("io.objectbox:objectbox-kotlin:3.6.0")
    kapt("io.objectbox:objectbox-processor:3.6.0")
    
    implementation(fileTree("dir" to "libs", "include" to listOf("*.jar")))
}
