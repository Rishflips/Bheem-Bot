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
        versionCode = System.getenv("BHEEM_VERSION_CODE")?.toInt() ?: 1
        versionName = System.getenv("BHEEM_VERSION_NAME") ?: "0.0.1-dev"
        buildConfigField("String", "LITERT_LM_VERSION", "\"${libs.versions.litertlm.get()}\"")
    }
    // The release key is the owner's and reaches the build only through these variables. Without them the release APK is unsigned.
    val releaseKey = listOf("BHEEM_KEYSTORE_FILE", "BHEEM_KEYSTORE_PASSWORD", "BHEEM_KEY_ALIAS", "BHEEM_KEY_PASSWORD").map { System.getenv(it) }
    if (releaseKey.all { !it.isNullOrEmpty() }) {
        signingConfigs {
            create("release") {
                storeFile = file(releaseKey[0]!!)
                storePassword = releaseKey[1]
                keyAlias = releaseKey[2]
                keyPassword = releaseKey[3]
            }
        }
        buildTypes.getByName("release").signingConfig = signingConfigs.getByName("release")
    }
    // The git revision note AGP adds depends on whether a .git folder exists, so a build from a source archive would differ from one from a clone.
    buildTypes.getByName("release").vcsInfo.include = false
    // The Google dependency block in the APK is a signing blob that F-Droid rejects and that varies between builds.
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
