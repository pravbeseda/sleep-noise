import com.android.build.gradle.internal.api.BaseVariantOutputImpl
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    id("com.google.gms.google-services")
    id("com.google.firebase.crashlytics")
}

// --- Versioning -------------------------------------------------------------
// versionName: managed in version.properties, bumped by hand on release.
// versionCode: derived from the git commit count, so it is monotonic and never
//   edited manually.
//
// Monotonicity is NOT enforced here — it relies on main and release staying
// append-only, and on CI checking out with fetch-depth: 0. Any shallow clone
// undercounts, shipping a lower code than the one already on Play, so a shallow
// checkout is rejected outright: a partial depth larger than the floor
// (fetch-depth: 20, say) would otherwise sail past a numeric threshold while
// still producing a stale code. The floor guards the remaining case of a history
// that is not the one this app is released from — it is the last manually
// assigned versionCode, which the count must never legitimately fall below.
//
// A build that cannot derive either half of the version falls back to a
// placeholder, and the fallback is then blocked from reaching a release artifact
// by verifyReleaseVersioning, wired below into the tasks that package a release
// APK or AAB. Anchoring the gate to those tasks rather than to the requested
// task name means `gradle build` and `gradle bundle` are covered even though
// neither names a release, while `lintRelease` and `testReleaseUnitTest` — which
// publish nothing — still run on a shallow clone, as does any debug build.
//
// Both halves are gated, not just the code. Play orders updates by versionCode
// alone, so a placeholder name blocks nothing on its own — but a release built
// without version.properties is a release nobody can identify afterwards, and it
// is a mistake worth catching at the same moment as the other one.
val versionCodeFloor = 5
val versionNamePlaceholder = "0.0.0"

// Captured so the exec spec below carries an explicit directory rather than
// relying on what providers.exec defaults to. It does resolve against the project
// on Gradle 8.13 — verified with a cold daemon launched from a non-repository
// directory — but the wrong default would silently count some other repository's
// commits, and the failure mode is worth one line to rule out for good.
val repoDir = rootDir

// Each half below is either a trustworthy value or the reason it is not one.
// Failures are held rather than thrown so that configuration still succeeds for
// every build that publishes nothing.
val derivedVersionCode: Result<Int> = runCatching {
    fun git(vararg args: String): String = providers.exec {
        workingDir = repoDir
        commandLine("git", *args)
    }.standardOutput.asText.get().trim()

    check(git("rev-parse", "--is-shallow-repository") != "true") {
        "the checkout is shallow, so the commit count is truncated"
    }

    val count = git("rev-list", "--count", "HEAD").toInt()
    check(count >= versionCodeFloor) {
        "the commit count ($count) is below the floor ($versionCodeFloor)"
    }
    count
}

val derivedVersionName: Result<String> = runCatching {
    val propsFile = file("version.properties")
    check(propsFile.exists()) { "${propsFile.name} does not exist" }

    val name = Properties()
        .apply { propsFile.inputStream().use { load(it) } }
        .getProperty("versionName")
    check(!name.isNullOrBlank()) { "${propsFile.name} defines no versionName" }
    name.trim()
}

val appVersionCode: Int = derivedVersionCode.getOrDefault(versionCodeFloor)
val appVersionName: String = derivedVersionName.getOrDefault(versionNamePlaceholder)

val versioningProblems: List<String> = listOfNotNull(
    derivedVersionCode.exceptionOrNull()?.let {
        "versionCode fell back to $versionCodeFloor because ${it.message}"
    },
    derivedVersionName.exceptionOrNull()?.let {
        "versionName fell back to $versionNamePlaceholder because ${it.message}"
    },
)

val verifyReleaseVersioning = tasks.register("verifyReleaseVersioning") {
    description = "Fails a release build whose version cannot be derived from the repository."
    doLast {
        if (versioningProblems.isNotEmpty()) {
            throw GradleException(
                versioningProblems.joinToString(
                    prefix = "Refusing to package a release:\n  - ",
                    separator = "\n  - ",
                    postfix = "\nCI must check out with fetch-depth: 0 and keep " +
                        "app/version.properties in place.",
                )
            )
        }
    }
}

androidComponents {
    onVariants { variant ->
        if (variant.buildType != "release") return@onVariants
        val name = variant.name.replaceFirstChar(Char::uppercase)
        // packageX builds the APK, packageXBundle the AAB — the two tasks that
        // turn a versionCode into something publishable.
        setOf("package$name", "package${name}Bundle").forEach { taskName ->
            tasks.matching { it.name == taskName }.configureEach {
                dependsOn(verifyReleaseVersioning)
            }
        }
    }
}

android {
    namespace = "ru.pravbeseda.sleepnoise"
    compileSdk = 36

    buildFeatures {
        buildConfig = true
    }

    defaultConfig {
        applicationId = "ru.pravbeseda.sleepnoise"
        minSdk = 24
        targetSdk = 36
        versionCode = appVersionCode
        versionName = appVersionName

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        compose = true
    }

    applicationVariants.all {
        val variant = this
        outputs.forEach { output ->
            if (output is BaseVariantOutputImpl) {
                val appName = "SleepNoise"
                val versionName = variant.versionName
                val versionCode = variant.versionCode
                val buildTypeName = variant.buildType.name

                // No parentheses: the filename ends up in shell globs and CI
                // artifact paths, where they need quoting to survive.
                val newApkName = "$appName-$versionName-$versionCode-$buildTypeName.apk"
                output.outputFileName = newApkName
            }
        }
    }
}

dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.analytics)
    implementation(libs.firebase.crashlytics)
    implementation(libs.androidx.core.splashscreen)
}