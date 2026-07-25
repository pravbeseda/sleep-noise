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
// append-only, and on CI checking out with fetch-depth: 0. A shallow clone makes
// `git rev-list --count HEAD` return 1, which would ship a lower code than the
// one already on Play. The floor is the last manually assigned versionCode: the
// count must never legitimately fall below it. Release builds fail loudly rather
// than silently publishing a stale code; debug builds fall back to the floor so
// that building outside a git checkout still works.
val versionCodeFloor = 5

val isReleaseBuild = gradle.startParameter.taskNames.any {
    it.contains("release", ignoreCase = true)
}

val appVersionName: String = Properties().apply {
    val propsFile = file("version.properties")
    if (propsFile.exists()) {
        propsFile.inputStream().use { load(it) }
    }
}.getProperty("versionName", "0.0.0")

val appVersionCode: Int = run {
    val count = try {
        providers.exec {
            commandLine("git", "rev-list", "--count", "HEAD")
        }.standardOutput.asText.get().trim().toInt()
    } catch (e: Exception) {
        if (isReleaseBuild) {
            throw GradleException(
                "Cannot derive versionCode from git for a release build: ${e.message}",
                e
            )
        }
        versionCodeFloor
    }

    if (count < versionCodeFloor) {
        if (isReleaseBuild) {
            throw GradleException(
                "versionCode from git commit count ($count) is below the floor " +
                    "($versionCodeFloor) — most likely a shallow clone. " +
                    "CI must check out with fetch-depth: 0."
            )
        }
        versionCodeFloor
    } else {
        count
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