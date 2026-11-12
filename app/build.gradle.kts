import java.time.Duration

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.google.devtools.ksp)
    alias(libs.plugins.jetbrains.kotlin.plugin.serialization)
}

android {
    namespace = "io.github.michael3dprint.mybambuddy"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "io.github.michael3dprint.mybambuddy"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            optimization {
                enable = true
                packageScope = setOf("androidx.**", "kotlin.**", "kotlinx.**")
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    testOptions {
        unitTests {
            isReturnDefaultValues = true
            all {
                it.maxHeapSize = "2048m"
            }
        }
    }
    buildFeatures {
        compose = true
    }
}

kotlin {
    compilerOptions {
        // Standards: no compiler warning is allowed to pile up (see docs/ENGINEERING_STANDARDS.md).
        allWarningsAsErrors.set(true)
        // Adopt the future Kotlin behaviour now: an annotation like @Json applies to the parameter AND the property.
        freeCompilerArgs.add("-Xannotation-default-target=param-property")
    }
}

dependencies {
    constraints {
        // Guava arrives through androidx.media3 (camera). Versions before 33.7.2 have a deserialization memory flaw (GHSA-xxph-c9ww-hj94).
        implementation("com.google.guava:guava:33.7.2-android") { because("GHSA-xxph-c9ww-hj94 is fixed in 33.7.2") }
    }
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.accompanist.permissions)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)
    implementation(libs.androidx.compose.adaptive)
    implementation(libs.androidx.compose.adaptive.layout)
    implementation(libs.androidx.compose.adaptive.navigation3)
    implementation(libs.androidx.compose.material.icons.core)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.viewmodel.navigation3)
    implementation(libs.androidx.navigation3.runtime)
    implementation(libs.androidx.navigation3.ui)
    implementation(libs.androidx.room.ktx)
    implementation(libs.androidx.room.runtime)
    implementation(libs.coil.compose)
    implementation(libs.converter.moshi)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.core)
    implementation(libs.logging.interceptor)
    implementation(libs.moshi.kotlin)
    implementation(libs.okhttp)
    implementation(libs.play.services.code.scanner)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.retrofit)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.security.crypto)
    testImplementation(libs.androidx.core)
    testImplementation(libs.androidx.junit)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.mockwebserver)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.compose.ui.test.junit4.accessibility)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.runner)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
    "ksp"(libs.androidx.room.compiler)
    "ksp"(libs.moshi.kotlin.codegen)
}

tasks.withType<Test>().configureEach {
    timeout.set(Duration.ofMinutes(5))
    testLogging {
        events("failed")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.SHORT
        showStandardStreams = false
    }
}

// ---- Style gate -------------------------------------------------------------------------------
// ktlint (Android Studio code style, see /.editorconfig) run straight over src/. The Gradle ktlint plugin is
// not used: with AGP 9 it only sees build scripts, not the Kotlin source sets.
val ktlint: Configuration by configurations.creating

dependencies {
    ktlint("com.pinterest.ktlint:ktlint-cli:1.5.0:all")
}

fun registerKtlint(taskName: String, description: String, vararg extraArgs: String) =
    tasks.register<JavaExec>(taskName) {
        group = "verification"
        this.description = description
        classpath = ktlint
        mainClass.set("com.pinterest.ktlint.Main")
        args("src/**/*.kt", "--relative", *extraArgs)
    }

val ktlintSourcesCheck = registerKtlint("ktlintSourcesCheck", "Fails on any Kotlin style violation")
registerKtlint("ktlintSourcesFormat", "Auto-fixes Kotlin style violations", "--format")

tasks.named("check") { dependsOn(ktlintSourcesCheck) }

// AGP's lint tasks cannot be stored in the Gradle configuration cache; run them uncached instead of failing.
tasks.matching { it.name.startsWith("lint") }.configureEach {
    notCompatibleWithConfigurationCache("Android Lint is not configuration-cache compatible with this AGP version")
}
