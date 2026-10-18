// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.music.conformance

import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse

class SwiftPackageReleaseTest {
    @Test
    fun manifestDoesNotAdvertiseAnUnpublishedBinary() {
        val manifest = File("apple/NoMercyMusicPlayer/Package.swift").readText()

        assertFalse(manifest.contains("REPLACED_BY_RELEASE_JOB"), "release checksum has not been stamped")
        assertFalse(
            manifest.contains("v2.0.0-rc.1/NoMercyMusicPlayer.xcframework.zip"),
            "release asset was never published",
        )
        assertFalse(manifest.contains("NOMERCY_SPM_RELEASE"), "release mode requires a published binary")
    }
}
