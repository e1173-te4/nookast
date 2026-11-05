plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.compose.compiler) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.android.test) apply false
    alias(libs.plugins.baselineprofile) apply false
    alias(libs.plugins.spotless)
}

// Formatting (plan 8.5): ktlint with the rules of .editorconfig; CI runs spotlessCheck.
spotless {
    kotlin {
        target("app/src/**/*.kt", "baselineprofile/src/**/*.kt")
        ktlint(libs.versions.ktlint.get())
    }
    kotlinGradle {
        target("*.gradle.kts", "app/*.gradle.kts", "baselineprofile/*.gradle.kts")
        ktlint(libs.versions.ktlint.get())
    }
}
