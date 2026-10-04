import com.github.triplet.gradle.play.PlayPublisherExtension
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kover)
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
    // Copied into a local before doLast closes over it. A top-level `val` in a
    // Kotlin build script is a field of the script object, so referring to
    // versioningProblems directly from the action would make the action hold a
    // reference to the script — which the configuration cache cannot serialize.
    val problems = versioningProblems
    doLast {
        if (problems.isNotEmpty()) {
            throw GradleException(
                problems.joinToString(
                    prefix = "Refusing to package a release:\n  - ",
                    separator = "\n  - ",
                    postfix = "\nCI must check out with fetch-depth: 0 and keep " +
                        "app/version.properties in place.",
                ),
            )
        }
    }
}

val gatedPackagingTasks = mutableSetOf<String>()

androidComponents {
    onVariants { variant ->
        if (variant.buildType != "release") return@onVariants
        val name = variant.name.replaceFirstChar(Char::uppercase)
        // packageX builds the APK, packageXBundle the AAB — the two tasks that
        // turn a versionCode into something publishable.
        setOf("package$name", "package${name}Bundle").forEach { taskName ->
            gatedPackagingTasks += taskName
            tasks.matching { it.name == taskName }.configureEach {
                dependsOn(verifyReleaseVersioning)
            }
        }
    }
}

// tasks.matching is lenient: a name AGP no longer uses matches nothing and says
// nothing, so the gate would disappear while the build stayed green and releases
// kept packaging. Since this block exists to stop a bad release, a missing name
// has to be loud. tasks.names reads the registered names without realizing the
// tasks, so the assertion costs nothing and does not undo the laziness above.
afterEvaluate {
    val missing = gatedPackagingTasks - tasks.names
    check(missing.isEmpty()) {
        "verifyReleaseVersioning is wired to $missing, which no longer exist — " +
            "AGP has renamed or split the packaging tasks, and the release gate " +
            "is no longer attached to anything. Update the names in the " +
            "versioning block of app/build.gradle.kts."
    }
}

android {
    namespace = "ru.pravbeseda.sleepnoise"
    compileSdk = 37

    buildFeatures {
        buildConfig = true
    }

    // Android 13+ lists the app's languages in the system settings, derived from the values-XX
    // directories; with AppCompat holding the choice, that list is also the only way back to the
    // system language once one has been picked. res/resources.properties names the default bucket.
    androidResources {
        generateLocaleConfig = true
    }

    defaultConfig {
        applicationId = "ru.pravbeseda.sleepnoise"
        minSdk = 26
        targetSdk = 36
        versionCode = appVersionCode
        versionName = appVersionName

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // The store screenshots are an errand, not a test: they drive every locale through three states
        // with a foreground service playing real audio, and no pull request is any the wiser for it. So
        // connectedAndroidTest leaves them out and -PstoreScreenshots runs them and nothing else — one
        // property, both filters, rather than a flag each caller has to remember. See StoreScreenshot.
        val screenshotFilter = if (project.hasProperty("storeScreenshots")) "annotation" else "notAnnotation"
        testInstrumentationRunnerArguments[screenshotFilter] = "ru.pravbeseda.sleepnoise.store.StoreScreenshot"
    }

    // Credentials come from -PSN_* project properties; CI passes them as
    // ORG_GRADLE_PROJECT_SN_* environment variables, which Gradle maps onto
    // properties of the same name.
    signingConfigs {
        create("release") {
            keyAlias = project.findProperty("SN_KEY_ALIAS")?.toString()
            keyPassword = project.findProperty("SN_KEY_PASSWORD")?.toString()
            // storeFile must never be null: file(null) throws at configuration time
            // and takes the whole project down, including the CI jobs that sign
            // nothing. The default keeps configuration valid when SN_STORE_FILE is
            // absent; the path is gitignored, not secret.
            storeFile = file(project.findProperty("SN_STORE_FILE") ?: "../.key/Drevo.Keystore")
            storePassword = project.findProperty("SN_STORE_PASSWORD")?.toString()
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            signingConfig = signingConfigs.getByName("release")
        }
    }

    lint {
        warningsAsErrors = true
        abortOnError = true

        // These answer "is something newer available?", which depends on the day
        // and the machine rather than on the commit under test. Left as errors
        // they fail untouched code as soon as Google ships a release.
        //
        // The first three drift because their messages carry the versions being
        // compared ("a newer version than 8.13 is available: 8.14.5"), and
        // baseline matching is on message text — so a runner with a fresher
        // index reports the same finding as a new one. A locally recorded
        // baseline listed 11 that the runner then could not match.
        //
        // OldTargetApi is here for a different reason, and it is the weaker
        // case of the four: its message is generic, so a baseline would hold it
        // fine. It differs because lint compares targetSdk against the newest
        // API level it knows about, and compileSdk 37 is ahead of targetSdk 36,
        // so it fires on every machine. Unlike the other three it tracks a Play
        // deadline, so muting it loses a signal worth keeping: that is why the
        // yearly targetSdk check is written into AGENTS.md instead, where nothing
        // in the build can quietly drop it.
        //
        // informational, not disable: all four stay in the uploaded report, they
        // just cannot break the build.
        informational += setOf(
            "AndroidGradlePluginVersion",
            "GradleDependency",
            "NewerVersionAvailable",
            "OldTargetApi",
        )
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

// --- Google Play Publisher --------------------------------------------------
// Applied only when asked for: `./gradlew -PplayPublish publishReleaseBundle`.
// Without the property the plugin sits on the classpath (root build script) and
// configures nothing, so it can neither slow an ordinary build nor break one
// when a new AGP lands before a GPP that knows it. Publishing is deliberate — a
// Gradle run someone asks for, never a side effect of assemble.
//
// GPP reads src/main/play, the tree the store texts already live in. The three
// workflows under .github/workflows use two of its tasks, publishReleaseBundle
// and promoteReleaseArtifact, and both carry the artifact and release-notes/
// only. The store page is a fourth task, publishReleaseListing, run when a
// release reaches everyone (screenshots.yml, called by promote and rollout) and
// by hand through .github/workflows/publish-listing.yml.
if (project.hasProperty("playPublish")) {
    apply(plugin = "com.github.triplet.play")

    configure<PlayPublisherExtension> {
        // Same shape as the signing credentials above: the JSON key lives
        // outside the repository and its path arrives as an SN_* property —
        // ORG_GRADLE_PROJECT_SN_PLAY_JSON on CI, ~/.gradle/gradle.properties
        // by hand. Unset, the plugin falls back to Application Default
        // Credentials and fails at the task, not at configuration.
        project.findProperty("SN_PLAY_JSON")?.let { serviceAccountCredentials.set(file(it)) }
        // A bundle, never an APK: the listing postdates August 2021, so Play
        // accepts nothing else from it. Play App Signing signs what it
        // distributes with the key Drevo.Keystore also holds.
        defaultToAppBundles.set(true)
        // Dry by default: a publish task opens an edit in Play and abandons it
        // unless the run passes --commit. Forgetting the flag publishes
        // nothing; the opposite default would let a forgotten --no-commit
        // publish everything.
        commit.set(false)
    }
}

dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.lifecycle.runtime)
    implementation(libs.androidx.lifecycle.viewmodel)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.material)
    implementation(libs.play.review.ktx)
    implementation(libs.play.app.update.ktx)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.analytics)
    implementation(libs.firebase.crashlytics)
    implementation(libs.androidx.core.splashscreen)
}

// --- Coverage ---------------------------------------------------------------
// The denominator is cut down to the classes a JVM test can actually reach:
// media/ minus the audio engine, plus the pure classes listed below. Everything else in the
// app imports android.*, so no unit test can execute a line of it, and leaving
// it in would make the figure track the Activity/Service line count rather than
// how well the logic is tested.
//
// The trailing * on the include is what catches SleepTimer's companion object,
// which the Kotlin compiler emits as a separate SleepTimer$Companion class
// carrying the eight lines of forDuration and formatRemaining. It is the only
// companion the filters have to think about: the others hold constants, compile
// to nothing executable, and are dropped from the report whatever the patterns
// say.
//
// The bound sits on the debug variant, not on `total`: total merges debug and
// release, so a rule there would measure a variant CI never builds and drag
// testReleaseUnitTest into the graph behind it.
//
// 80 rather than the 97.561 % measured today: the floor has to survive a new
// class landing with its edge cases covered a commit later, and it is raised
// when the figure settles higher — never lowered to turn a red run green.
kover {
    reports {
        filters {
            includes {
                classes(
                    "ru.pravbeseda.sleepnoise.media.*",
                    "ru.pravbeseda.sleepnoise.timer.SleepTimer*",
                    "ru.pravbeseda.sleepnoise.playback.PlaybackState*",
                    "ru.pravbeseda.sleepnoise.settings.SettingsRepository*",
                    "ru.pravbeseda.sleepnoise.settings.NoiseSetting",
                    "ru.pravbeseda.sleepnoise.models.AppTheme*",
                    "ru.pravbeseda.sleepnoise.review.ReviewPolicy*",
                    "ru.pravbeseda.sleepnoise.update.UpdatePolicy*",
                )
            }
            excludes {
                classes("ru.pravbeseda.sleepnoise.media.NoiseEngine*")
            }
        }

        variant("debug") {
            verify {
                rule("Line coverage of the Android-free classes") {
                    minBound(80)
                }
            }
        }
    }
}

// PlayMetadataTest reads the store texts and the app's own locale buckets
// straight off disk, and Gradle can see neither on its own: without these lines
// the unit tests report UP-TO-DATE when the only thing that changed is the very
// thing they guard. Both were measured on this project, not feared. A
// 32-character German title, two over Play's limit, left testDebugUnitTest
// green; so did a new values-fr/strings.xml declaring a locale with no listing,
// which is the very step the README tells a translator to take.
//
// The compile chain does not cover res, which is the half that looks as though
// it should: a bucket carrying only a string that already exists adds no R
// field, so processDebugResources re-runs while the R jar on the test classpath
// stays byte-identical and the test task is left up to date.
tasks.withType<Test>().configureEach {
    inputs.dir(layout.projectDirectory.dir("src/main/play"))
        .withPropertyName("playMetadata")
        .withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.dir(layout.projectDirectory.dir("src/main/res"))
        .withPropertyName("appResources")
        .withPathSensitivity(PathSensitivity.RELATIVE)
}
