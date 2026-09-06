plugins {
    alias(libs.plugins.android.library)
    // 【修正箇所 1】 alias(libs.plugins.ksp) を削除（またはコメントアウト）し、id("kotlin-kapt") を追加
    id("kotlin-kapt")
    id("kotlin-android")
    id("android-module-dependencies")
    id("test-module-dependencies")
    id("jacoco-module-dependencies")
}

android {
    namespace = "app.aaps.core.validators"
}


dependencies {
    implementation(project(":core:data"))
    implementation(project(":core:interfaces"))
    implementation(project(":core:keys"))

    api(libs.com.google.dagger.android)
    api(libs.com.google.dagger.android.support)
    api(libs.com.google.android.material)

    // 【修正箇所 2】 ksp(...) を kapt(...) に変更
    kapt(libs.com.google.dagger.compiler)
    kapt(libs.com.google.dagger.android.processor)
}
