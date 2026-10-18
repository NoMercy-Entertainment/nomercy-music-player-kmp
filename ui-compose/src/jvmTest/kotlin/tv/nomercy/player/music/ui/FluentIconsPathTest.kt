// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.music.ui

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

class FluentIconsPathTest {
    @Test
    fun shareHoverContainsTheCompleteFilledGlyph() {
        val source = File("src/commonMain/kotlin/tv/nomercy/player/music/ui/FluentIcons.kt").readText()
        val path = Regex("private const val HOVER_SHARE: String =\\s*\"([^\"]+)\"")
            .find(source)?.groupValues?.get(1) ?: error("share hover path missing")

        assertTrue(path.endsWith("Z"), "share hover path stops before the glyph closes")
        assertTrue(path.contains("L14.107 6.78874"), "share hover path has no upper connecting arm")
    }
}
