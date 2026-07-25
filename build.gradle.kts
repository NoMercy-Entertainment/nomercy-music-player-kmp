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
    signAllPublications()
}
