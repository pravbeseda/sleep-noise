// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.spotless)
    id("com.google.gms.google-services") version "4.4.2" apply false
    id("com.google.firebase.crashlytics") version "3.0.2" apply false
}

spotless {
    // Ratchet: only files that differ from origin/main are formatted or checked.
    // Reformatting the whole tree in one commit would rewrite every blame line
    // in the project to buy nothing, so the existing code stays as it is and
    // the rule applies to whatever a branch touches. The cost is a hard
    // dependency on the origin/main ref: a shallow or single-branch clone
    // cannot resolve it and every spotless task fails there — CI checks out
    // with fetch-depth: 0 for this reason.
    ratchetFrom("origin/main")

    kotlin {
        target("**/*.kt")
        targetExclude("**/build/**")
        ktlint(libs.versions.ktlint.get())
    }
    kotlinGradle {
        target("**/*.gradle.kts")
        targetExclude("**/build/**")
        ktlint(libs.versions.ktlint.get())
    }
    format("xml") {
        // Whitespace only. Layouts and strings.xml carry attribute ordering and
        // line breaks that a real XML formatter would rewrite wholesale, and an
        // RTL or translation diff is hard enough to read without that noise.
        target("app/src/main/res/**/*.xml")
        trimTrailingWhitespace()
        endWithNewline()
    }
}
