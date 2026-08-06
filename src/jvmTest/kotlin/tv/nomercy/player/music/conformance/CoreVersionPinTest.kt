// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.music.conformance

import tv.nomercy.player.core.KIT_VERSION
import kotlin.test.Test
import kotlin.test.assertEquals

// The core this library builds against is the core it ships with.
//
// The trio releases as one version — core, video and music always carry the
// same number — so what this library IS and what it RESOLVED must agree. They
// are written in two different files by hand, and they drifted through an
// entire renumber without anything noticing: gradle.properties went to 0.1.0
// while libs.versions.toml kept asking for 2.0.0-rc.1.
//
// Nothing failed. The library compiled, the whole suite passed, and every fix
// made in core was simply absent from the artifact a consumer resolved — which
// is how the desktop testbed came to run a months-old core and report defects
// that had already been fixed. A stale pin does not break a build; it makes the
// build measure the wrong thing.
class CoreVersionPinTest {

    @Test
    fun theResolvedCoreIsTheVersionThisLibraryShipsAs() {
        val mine: String = System.getProperty("nomercy.library.version")
            ?: error("the build did not pass nomercy.library.version")

        assertEquals(
            mine,
            KIT_VERSION,
            "this library is $mine but resolved core $KIT_VERSION — " +
                "update nomercyPlayerCore in gradle/libs.versions.toml",
        )
    }
}
