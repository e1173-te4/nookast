import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.ksp)
    alias(libs.plugins.baselineprofile)
    alias(libs.plugins.licensee)
}

// Local signing material lives in the gitignored .secrets directory. CI and fresh
// clones have no such file and fall back to the default debug key.
val secretsDir = rootProject.file(".secrets")
val signingProperties = Properties().apply {
    val file = secretsDir.resolve("keystore.properties")
    if (file.isFile) file.inputStream().use(::load)
}

// Traceability (plan 8.3): the commit a build came from and whether the tree had changes. Without
// git (a source archive) both read as unknown.
fun gitOutput(vararg args: String): String = runCatching {
    providers.exec {
        commandLine("git", *args)
        isIgnoreExitValue = true
    }.standardOutput.asText.get().trim()
}.getOrDefault("")
val gitSha = gitOutput("rev-parse", "--short=10", "HEAD").ifBlank { "unknown" }
val gitDirty = gitOutput("status", "--porcelain", "--untracked-files=no").isNotBlank()

android {
    namespace = "ru.e1173.nookast"
    compileSdk = 37

    defaultConfig {
        applicationId = "ru.e1173.nookast"
        minSdk = 26
        targetSdk = 37
        versionCode = 69
        versionName = "1.2.4"
        // The APK name the user sees in chat (APK-3.12); shown in Settings → About.
        buildConfigField("String", "DELIVERY", "\"1.2.4\"")
        buildConfigField("String", "GIT_SHA", "\"$gitSha\"")
        buildConfigField("boolean", "GIT_DIRTY", gitDirty.toString())
        // Over-the-air updates from the public releases (user, 2.11.2026): release builds only. A local
        // test build may point them elsewhere with -PupdaterTestUrl (and raise its own version code
        // with -PupdaterTestVersionCode); published builds never pass either.
        buildConfigField("boolean", "UPDATER_ENABLED", "false")
        buildConfigField(
            "String",
            "UPDATE_LATEST_URL",
            "\"${providers.gradleProperty("updaterTestUrl").getOrElse("https://api.github.com/repos/e1173-te4/nookast/releases/latest")}\"",
        )
        providers.gradleProperty("updaterTestVersionCode").orNull?.toIntOrNull()?.let { versionCode = it }

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    signingConfigs {
        // Same key as the 0.6.x debug builds, so updates install over existing data.
        if (signingProperties.containsKey("legacyDebug.storeFile")) {
            create("legacyDebug") {
                storeFile = secretsDir.resolve(signingProperties.getProperty("legacyDebug.storeFile"))
                storePassword = signingProperties.getProperty("legacyDebug.storePassword")
                keyAlias = signingProperties.getProperty("legacyDebug.keyAlias")
                keyPassword = signingProperties.getProperty("legacyDebug.keyPassword")
            }
        }
        if (signingProperties.containsKey("release.storeFile")) {
            create("release") {
                storeFile = secretsDir.resolve(signingProperties.getProperty("release.storeFile"))
                storePassword = signingProperties.getProperty("release.storePassword")
                keyAlias = signingProperties.getProperty("release.keyAlias")
                keyPassword = signingProperties.getProperty("release.keyPassword")
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
            // Not debuggable (user, 29.10.2026: lists did not scroll smoothly): ART then compiles the
            // app ahead of time with the baseline profiles of Compose, and StrictMode logging is off.
            // Same package and key, so it installs over the earlier builds with all data kept.
            isDebuggable = false
            signingConfigs.findByName("legacyDebug")?.let { signingConfig = it }
        }
        release {
            signingConfigs.findByName("release")?.let { signingConfig = it }
            buildConfigField("boolean", "UPDATER_ENABLED", "true")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    packaging {
        resources {
            excludes += setOf("/META-INF/{AL2.0,LGPL2.1}")
        }
    }

    lint {
        // Pre-existing warnings from the 0.6.14 port; new issues are reported on top.
        baseline = rootProject.file("config/lint-baseline.xml")
        warningsAsErrors = false
        abortOnError = true
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
    }

    // Robolectric uses the debug APK's AssetManager. Keep exported Room schemas in
    // debug only so migration validation can run without shipping them in release.
    sourceSets.getByName("debug").assets.directories.add("$projectDir/schemas")
}

// Licences of the release dependencies (DEC-053): any licence not allowed here fails the build, and
// the list goes into the APK as app/cash/licensee/artifacts.json for Settings → About → Licenses.
licensee {
    allow("Apache-2.0")
    bundleAndroidAsset.set(true)
}

// Immutable UI models (plan 5.2) let Compose skip unchanged screens.
composeCompiler {
    stabilityConfigurationFiles.add(project.layout.projectDirectory.file("compose-stability.conf"))
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.haze)
    implementation(libs.androidx.navigation3.runtime)
    implementation(libs.androidx.navigation3.ui)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.coil.compose)
    implementation(libs.androidx.profileinstaller)
    // Plan 5.6: startup and scrolling compiled ahead of time from the generated profile.
    "baselineProfile"(project(":baselineprofile"))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.window)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui.tooling.preview)

    implementation(libs.okhttp)
    implementation(libs.okhttp.coroutines)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.session)
    implementation(libs.androidx.media3.datasource.okhttp)
    implementation(libs.androidx.work.runtime.ktx)
    ksp(libs.androidx.room.compiler)

    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    testImplementation(libs.junit4)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.androidx.room.testing)
    testImplementation(libs.robolectric)
    testImplementation(libs.okhttp.mockwebserver)
    testImplementation(libs.turbine)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
    arg("room.generateKotlin", "true")
}
