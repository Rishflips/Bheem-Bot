import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "app.bheem.bench"
    compileSdk = 36
    defaultConfig {
        applicationId = "app.bheem.bench"
        minSdk = 31
        targetSdk = 36
        versionCode = 1
        versionName = "0.0-phase0"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    sourceSets["main"].assets.srcDir("../eval/data")
}

kotlin {
    compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
}

dependencies {
    implementation(project(":eval"))
    implementation(project(":models-litertlm"))
    implementation(libs.coroutines.android)
    implementation(libs.serialization.json)
    implementation(libs.moonshine.voice)
}
