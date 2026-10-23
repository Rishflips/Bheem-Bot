import org.gradle.process.CommandLineArgumentProvider
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

tasks.register<JavaExec>("anonymize") {
    group = "bheem"
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("app.bheem.eval.cli.Anonymize")
}

tasks.register<JavaExec>("report") {
    group = "bheem"
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("app.bheem.eval.cli.Report")
    args(
        layout.projectDirectory.file("thresholds.json").asFile.absolutePath,
        layout.projectDirectory.dir("results").asFile.absolutePath,
        rootProject.layout.projectDirectory.file("docs/PHASE0-REPORT.md").asFile.absolutePath,
    )
}

tasks.register<JavaExec>("rescore") {
    group = "bheem"
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("app.bheem.eval.cli.Rescore")
    val input = providers.gradleProperty("rescoreIn")
    val output = providers.gradleProperty("rescoreOut")
    val promptSet = providers.gradleProperty("rescorePrompt")
    val dataDir = layout.projectDirectory.dir("data").asFile.absolutePath
    argumentProviders.add(CommandLineArgumentProvider { listOf(input.get(), dataDir, promptSet.get(), output.get()) })
}

tasks.register<JavaExec>("generateData") {
    group = "bheem"
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("app.bheem.eval.cli.GenerateData")
    args(layout.projectDirectory.dir("data").asFile.absolutePath)
}
