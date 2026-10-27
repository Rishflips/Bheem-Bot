import com.android.build.api.artifact.SingleArtifact
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "app.bheem.app"
    compileSdk = 36
    defaultConfig {
        applicationId = "app.bheem.bench"
        minSdk = 31
        targetSdk = 36
        // Release builds get both from the tag (scripts/release/version.py); a local build is the development version.
        // Release APKs are signed outside Gradle, by the release workflow, so no key is ever read here.
        val tagName = System.getenv("BHEEM_VERSION_NAME")?.takeIf { it.isNotEmpty() }
        val tagCode = System.getenv("BHEEM_VERSION_CODE")?.takeIf { it.isNotEmpty() }
        check((tagName == null) == (tagCode == null)) { "BHEEM_VERSION_NAME and BHEEM_VERSION_CODE must be set together" }
        versionCode = tagCode?.let { it.toIntOrNull() ?: error("BHEEM_VERSION_CODE must be a number, got '$it'") } ?: 1
        versionName = tagName ?: "0.0.1-dev"
        buildConfigField("String", "LITERT_LM_VERSION", "\"${libs.versions.litertlm.get()}\"")
    }
    // The git revision note AGP adds depends on whether a .git folder exists, so a build from a source archive would differ from one from a clone.
    buildTypes.getByName("release").vcsInfo.include = false
    // The Google dependency block is an encrypted blob added to the signing block; F-Droid rejects it and it varies between builds.
    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }
    flavorDimensions += "distribution"
    productFlavors {
        create("offline") { dimension = "distribution" }
        create("full") { dimension = "distribution" }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

abstract class CheckInternetPermission : DefaultTask() {
    @get:InputFile
    abstract val manifest: RegularFileProperty

    @get:Input
    abstract val expected: Property<Boolean>

    @TaskAction
    fun check() {
        val declared = manifest.get().asFile.readText().contains("android.permission.INTERNET")
        check(declared == expected.get()) {
            if (expected.get()) "the merged manifest ${manifest.get().asFile} must declare INTERNET" else "the merged manifest ${manifest.get().asFile} declares INTERNET, which the offline flavor must never have"
        }
    }
}

val verifyManifests = tasks.register("verifyManifests") {
    group = "verification"
    description = "Fails if the offline flavor gains INTERNET or the full flavor loses it"
}

androidComponents {
    onVariants { variant ->
        val offline = variant.flavorName == "offline"
        val task = tasks.register<CheckInternetPermission>("verify${variant.name.replaceFirstChar { it.uppercase() }}Manifest") {
            manifest.set(variant.artifacts.get(SingleArtifact.MERGED_MANIFEST))
            expected.set(!offline)
        }
        verifyManifests.configure { dependsOn(task) }
    }
}

kotlin {
    compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
}

dependencies {
    implementation(project(":agent-core"))
    implementation(project(":storage"))
    implementation(project(":models-litertlm"))
    "fullImplementation"(project(":downloader"))
    implementation(libs.coroutines.android)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    implementation(libs.activity.compose)
}
