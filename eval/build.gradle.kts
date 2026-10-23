import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
}

dependencies {
    implementation(project(":models"))
    implementation(libs.serialization.json)
    implementation(libs.coroutines.core)
    testImplementation(kotlin("test"))
}

tasks.test { useJUnitPlatform() }

tasks.register<JavaExec>("generateData") {
    group = "bheem"
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("app.bheem.eval.cli.GenerateData")
    args(layout.projectDirectory.dir("data").asFile.absolutePath)
}
