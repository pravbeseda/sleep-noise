// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.detekt)
    alias(libs.plugins.spotless)
    id("com.google.gms.google-services") version "4.4.2" apply false
    id("com.google.firebase.crashlytics") version "3.0.2" apply false
}

// Spotless reads ktlint_code_style out of .editorconfig but not max_line_length,
// measured on this project: without the override below, ktlint joined an already
// wrapped class declaration into a 156-character line. Both places carry 140 —
// .editorconfig for the IDE, this for the check.
val lineLength = mapOf("max_line_length" to "140")

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
        ktlint(libs.versions.ktlint.get()).editorConfigOverride(lineLength)
    }
    kotlinGradle {
        target("**/*.gradle.kts")
        targetExclude("**/build/**")
        ktlint(libs.versions.ktlint.get()).editorConfigOverride(lineLength)
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

detekt {
    buildUponDefaultConfig = true
    config.setFrom("$rootDir/config/detekt/detekt.yml")
    baseline = file("$rootDir/config/detekt/baseline.xml")
    // Configured on the root project, next to Spotless, rather than inside
    // :app. Applying it there would mean editing app/build.gradle.kts, and the
    // Spotless ratchet then pulls that whole 300-line file into ktlint's scope
    // — a wholesale reformat riding along in an unrelated PR. Detekt runs
    // without type resolution, so it needs the source paths and nothing else
    // from the Android plugin.
    //
    // androidTest is listed because it is not one of detekt's default source
    // paths, and it is the source set that already shipped a test asserting the
    // wrong package name.
    source.setFrom(
        "app/src/main/java",
        "app/src/test/java",
        "app/src/androidTest/java",
    )
}
