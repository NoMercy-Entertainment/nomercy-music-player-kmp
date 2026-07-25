pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "nomercy-music-player-kmp"

// The video library depends on core by its published coordinate, and Gradle
// substitutes this checkout for it when the group and name match. That means one
// dependency declaration works three ways: against a published core, against a
// sibling checkout while both are being changed together, and in CI where the
// core repo is checked out beside this one.
//
// Without it, every core change would need a publish before this repo could see
// it, which is how two libraries that ship together drift apart.
// The substitution is spelled out rather than left to Gradle's automatic
// group:name matching. The core build sets its group from a Gradle property
// through the publish plugin, which is too late for automatic substitution to
// see it, and the failure mode is a "could not find tv.nomercy:..." that looks
// like a missing artifact rather than a composite that did not engage.
val coreCheckout: java.io.File = file("../nomercy-player-core-kmp")
if (coreCheckout.resolve("settings.gradle.kts").exists()) {
    includeBuild(coreCheckout) {
        dependencySubstitution {
            substitute(module("tv.nomercy:nomercy-player-core-kmp")).using(project(":"))
        }
    }
}
