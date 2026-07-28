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

        // Only under the flag, and only so the flag is usable at all. The point
        // of -PusePublishedPlayerCore is to build the way a consumer does, and
        // before a release the only place the published core exists is the local
        // Maven repository. Unconditional mavenLocal() is the shape that lets a
        // stale hand-installed artifact shadow the real one for months.
        if (providers.gradleProperty("usePublishedPlayerCore").isPresent) {
            mavenLocal()
        }
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
//
// Off on demand, because "it works here" has to be checkable. A developer with
// the sibling checkout always builds against it, which is what you want day to
// day and exactly what hides a release where the published core is missing a
// declaration the video surface uses. -PusePublishedPlayerCore forces the
// resolution a consumer gets.
//
// Opt-OUT rather than opt-in, which is the reverse of what the plan wrote. The
// composite already keys on the sibling being there, and CI clones one repo, so
// CI is on the published path with no flag at all. Making the local path opt-in
// instead would mean every ordinary build silently resolved a published core
// that may be older than the checkout sitting next to it.
val coreCheckout: java.io.File = file("../nomercy-player-core-kmp")
val preferPublishedCore: Boolean = providers.gradleProperty("usePublishedPlayerCore").isPresent
if (!preferPublishedCore && coreCheckout.resolve("settings.gradle.kts").exists()) {
    includeBuild(coreCheckout) {
        dependencySubstitution {
            substitute(module("tv.nomercy:nomercy-player-core-kmp")).using(project(":"))

            // The shipped fakes come from the same checkout as the engine.
            // Substituting one and not the other resolves the tests against a
            // published stand-in while the engine is local, which is two
            // versions of the same library in one compile.
            substitute(module("tv.nomercy:nomercy-player-core-kmp-testing")).using(project(":testing"))
        }
    }
}

// The Compose chrome. Its own module so a consumer that draws its own controls
// — or one on Apple, where the chrome is SwiftUI — takes the engine without
// pulling a UI toolkit into the build.
include(":ui-compose")
