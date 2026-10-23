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

tasks.register<JavaExec>("recordingSheets") {
    group = "bheem"
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("app.bheem.eval.cli.GenerateRecordingSheets")
    val speakers = providers.gradleProperty("recordingSpeakers").orElse("6")
    val readers = providers.gradleProperty("recordingReaders").orElse("2")
    val dataDir = layout.projectDirectory.dir("data").asFile.absolutePath
    val outDir = layout.projectDirectory.dir("recordings").asFile.absolutePath
    argumentProviders.add(CommandLineArgumentProvider { listOf("$dataDir/commands.jsonl", outDir, speakers.get(), readers.get()) })
}

tasks.register<JavaExec>("speechReport") {
    group = "bheem"
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("app.bheem.eval.cli.ScoreSpeech")
    val input = providers.gradleProperty("speechResult")
    val output = providers.gradleProperty("speechReportOut")
    val dataDir = layout.projectDirectory.dir("data").asFile.absolutePath
    argumentProviders.add(CommandLineArgumentProvider { listOf(input.get(), "$dataDir/commands.jsonl", output.get()) })
}

tasks.register<JavaExec>("checkRecordings") {
    group = "bheem"
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("app.bheem.eval.cli.CheckRecordings")
    val folder = providers.gradleProperty("recordingsDir").orElse(layout.projectDirectory.dir("recordings").asFile.absolutePath)
    argumentProviders.add(CommandLineArgumentProvider { listOf(folder.get()) })
}
