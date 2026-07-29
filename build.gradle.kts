import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.plugin.mpp.apple.XCFramework
import org.jetbrains.kotlin.gradle.plugin.mpp.apple.XCFrameworkConfig

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.android.kmp.library)
    alias(libs.plugins.skie)
    alias(libs.plugins.detekt)
    alias(libs.plugins.binary.compatibility.validator)
    alias(libs.plugins.maven.publish)
}

kotlin {
    explicitApi()
    applyDefaultHierarchyTemplate()

    androidLibrary {
        namespace = "tv.nomercy.player.music"
        compileSdk = 36
        minSdk = 29

        withHostTestBuilder {}.configure {}

        compilations.configureEach {
            compileTaskProvider.configure {
                compilerOptions { jvmTarget.set(JvmTarget.JVM_21) }
            }
        }
    }

    jvm {
        compilations.all {
            compileTaskProvider.configure {
                compilerOptions { jvmTarget.set(JvmTarget.JVM_21) }
            }
        }
    }

    val musicXcf: XCFrameworkConfig = XCFramework("NoMercyMusicPlayer")
    listOf(
        iosArm64(),
        iosSimulatorArm64(),
        iosX64(),
        tvosArm64(),
        tvosSimulatorArm64(),
    ).forEach { target ->
        target.binaries.framework {
            baseName = "NoMercyMusicPlayer"
            isStatic = true
            binaryOption("bundleId", "tv.nomercy.player.music")

            // Core's declarations are named all over this library's public API —
            // PlayState, the event registry, the error catalogue — so without
            // this the framework ships signatures a Swift file cannot write
            // down. It is linked either way; exporting is what puts it in the
            // headers.
            //
            // The video framework has done this since a tvOS view could see one
            // of its own types and not the key type its method took. This one
            // never did, and the Swift surface gate is what finally said so.
            export(libs.nomercy.player.core)

            musicXcf.add(this)
        }
    }

    sourceSets {
        commonMain.dependencies {
            // By coordinate, not by project path. settings.gradle.kts
            // substitutes the sibling checkout when there is one, so the same
            // line works against a published core and against a local edit.
            api(libs.nomercy.player.core)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.json)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)

            // The plugin harness. Without it a plugin here could only be tested
            // through its tracker, which is why scrobble and auto-advance had
            // rules under test and no proof either one was ever reachable
            // through addPlugin.
            implementation(libs.nomercy.player.core.testing)
        }
        jvmTest.dependencies {
            // The surface gate asks the class what it exposes, which is the only
            // way to notice a method renamed, never written, or invented here.
            // jvmTest-only: a reflection library in the shipped artifact would be
            // a megabyte every consumer carries so a test could ask a question at
            // build time.
            implementation(kotlin("reflect"))
        }
    }
}

skie {
    analytics {
        enabled.set(false)
    }
}

detekt {
    source.setFrom(
        "src/commonMain/kotlin",
        "src/commonTest/kotlin",
        "src/jvmTest/kotlin",
        "src/androidMain/kotlin",
        "src/appleMain/kotlin",
        "src/jvmMain/kotlin",
        // The Compose module is a separate Gradle project but the same codebase,
        // and a rule that only applies to part of a repo is a rule people learn
        // to route around.
        "ui-compose/src/commonMain/kotlin",
        "ui-compose/src/commonTest/kotlin",
        "ui-compose/src/androidHostTest/kotlin",
        "ui-compose/src/jvmTest/kotlin",
    )
    config.setFrom("config/detekt/detekt.yml")
    buildUponDefaultConfig = true
}

@OptIn(kotlinx.validation.ExperimentalBCVApi::class)
apiValidation {
    klib {
        enabled = true
        strictValidation = true
    }
}

mavenPublishing {
    publishToMavenCentral()

    // Signed where there is a key, which is CI and nowhere else.
    //
    // Unconditional signAllPublications() takes publishToMavenLocal down with
    // it — "no configured signatory" — and that is the one command that proves
    // the module metadata carries every target before anything reaches Central.
    // The signature is still mandatory where it matters: the workflow sets
    // signingInMemoryKey from the repository secret, so a release without one
    // is a release that never ran through CI.
    if (providers.gradleProperty("signingInMemoryKey").isPresent) {
        signAllPublications()
    }
}

// The conformance fixtures are inputs to the tests that read them.
//
// They are read off disk by path rather than loaded as test resources, so
// without this Gradle sees no change when one is edited and reports the whole
// suite UP-TO-DATE. A vendored scenario nudged until a port passes it would
// then never even be measured — the gate does not fail, it does not run, and
// the build is green either way.
//
// Found by editing one on purpose and watching nothing happen.
tasks.withType<Test>().configureEach {
    inputs.files(
        fileTree("contract") { include("*.json") },
        fileTree("scenarios") { include("*.json") },
    )
        .withPropertyName("conformanceFixtures")
        .withPathSensitivity(PathSensitivity.RELATIVE)
}


// The conformance gate, by name.
//
// These tests already run inside jvmTest, so a red gate blocks `build` whether
// or not this task exists. What it adds is time and a name: it answers "has this
// port drifted from the ecosystem contract" in seconds, before the multiplatform
// build spends ten minutes arriving at the same answer under a heading that says
// nothing.
tasks.register<Test>("parityConformance") {
    group = "verification"
    description = "Checks the port against the vendored contract: surface, fixtures and behaviour."

    val jvmTest: Test = tasks.named<Test>("jvmTest").get()
    testClassesDirs = jvmTest.testClassesDirs
    classpath = jvmTest.classpath

    filter {
        includeTestsMatching("tv.nomercy.player.music.conformance.*")
        includeTestsMatching("tv.nomercy.player.conformance.*")
    }

    testLogging {
        events("failed")
        showStandardStreams = false
    }
}
