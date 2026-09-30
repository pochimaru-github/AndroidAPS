plugins {
    id("com.android.library")
}

android {
    namespace = "app.aaps.pump.eopatch.core"
    compileSdk = 34

    defaultConfig {
        minSdk = 26
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    // fileTree を使用して api 宣言することで、上位モジュール (:pump:eopatch) へ
    // パス崩れを起こすことなく AAR 内のクラスパスを正しく伝播・公開します
    api(fileTree(mapOf("dir" to "libs", "include" to listOf("*.aar"))))
}
