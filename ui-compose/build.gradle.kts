import java.time.Duration
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kmp.library)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.maven.publish)
}

// Android and JVM only, the same split the video chrome makes. Apple gets
// SwiftUI: a Compose surface on iOS fights the native app it would be embedded
// in, and an app already drawing its own chrome does not want a second toolkit
// in the process.
kotlin {
    explicitApi()
    applyDefaultHierarchyTemplate()

    androidLibrary {
        namespace = "tv.nomercy.player.music.ui"
        compileSdk = 36
        minSdk = 29

        // Robolectric reads the merged manifest and the resources with it.
        // Without them the Compose test host inflates against nothing and every
        // test fails on a missing theme rather than on what it measures.
        withHostTestBuilder {}.configure {
            isIncludeAndroidResources = true
        }

        compilations.configureEach {
            compileTaskProvider.configure {
                compilerOptions { jvmTarget.set(JvmTarget.JVM_21) }
            }
        }
    }

    jvm {
        compilations.configureEach {
            compileTaskProvider.configure {
                compilerOptions { jvmTarget.set(JvmTarget.JVM_21) }
            }
        }
    }

    sourceSets {
        commonMain.dependencies {
            api(project(":"))
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.ui)
            implementation(libs.kotlinx.coroutines.core)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.compose.ui.test)
        }
        jvmTest.dependencies {
            // Skiko's native runtime, without which the desktop Compose test
            // host dies in its static initializer. currentOs picks the right
            // platform artifact, which is what lets this run on a Windows dev
            // box and a Linux runner from the same line.
            implementation(compose.desktop.currentOs)
        }
        getByName("androidHostTest").dependencies {
            implementation(libs.robolectric)
            implementation(libs.androidx.compose.ui.test.manifest)
        }
    }
}

// Compose Desktop's tests render through Skiko, which reaches for a GPU and a
// display. A CI runner has neither, and the failure is not an error — it is a
// test task that sits there until the job's timeout, with the last line of the
// log being the task that started.
tasks.withType<Test>().configureEach {
    // A temp directory inside the build, not the machine's.
    //
    // Conscrypt extracts a native .so to `java.io.tmpdir` and loads it, and
    // the self-hosted runner executes jobs in a container whose /tmp will not
    // take one: every Android host test failed with
    // `UnsatisfiedLinkError: Failed creating temp file
    // (/tmp/libconscrypt_openjdk_jni-linux-x86_64....so)`.
    //
    // Set HERE as well as at the root, because a root `tasks.withType<Test>()`
    // configures the root project's tasks and this module's are its own — the
    // root-only fix reported green once on a build whose tests were UP-TO-DATE
    // from the runner's persistent cache, then failed the moment they ran.
    val temporary: java.io.File = layout.buildDirectory.dir("tmp/test-jvm").get().asFile
    systemProperty("java.io.tmpdir", temporary.absolutePath)
    doFirst { temporary.mkdirs() }

    systemProperty("skiko.renderApi", "SOFTWARE")
    systemProperty("java.awt.headless", "true")

    timeout.set(Duration.ofMinutes(8))
    testLogging {
        events("started", "passed", "failed", "skipped")
        showStandardStreams = false
    }
}

// Its own coordinate.
//
// Without this the chrome is a module in a repository rather than something
// an application can depend on: the engine published alone, and the drop-in
// view reachable only by checking the repository out. An application that
// draws its own is meant to be able to take the engine and leave this, which
// is the whole reason it is a second artifact and not a source set.
mavenPublishing {
    publishToMavenCentral()

    if (providers.gradleProperty("signingInMemoryKey").isPresent) {
        signAllPublications()
    }
}
