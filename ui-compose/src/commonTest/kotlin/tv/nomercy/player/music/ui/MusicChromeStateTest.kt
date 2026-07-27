// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.music.ui

import tv.nomercy.player.core.media.PlaylistItem
import tv.nomercy.player.core.player.PlayState
import tv.nomercy.player.core.player.PlayerState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

// The projection the row is drawn from.
//
// Worth its own tests because the failures are silent: a progress line drawn
// against a length nobody knows is a bar that is either always full or always
// empty, and neither looks like an error.
class MusicChromeStateTest {

    @Test
    fun aTrackWithNoLengthYetHasNoProgress() {
        // Every stream starts this way and a live one never leaves it. Dividing
        // anyway is a bar that reads full from the first second.
        val state: MusicChromeState = musicChromeStateOf(PlayerState(time = 30.0, duration = 0.0), null)

        assertEquals(0f, state.progress)
    }

    @Test
    fun andOneWithALengthIsDrawnAgainstIt() {
        val state: MusicChromeState = musicChromeStateOf(PlayerState(time = 30.0, duration = 120.0), null)

        assertEquals(A_QUARTER, state.progress)
    }

    @Test
    fun aPositionPastTheEndStillFitsOnTheBar() {
        // Engines overshoot at the end of a track, and a fraction above one is a
        // line drawn past the edge of what it is inside.
        val state: MusicChromeState = musicChromeStateOf(PlayerState(time = 200.0, duration = 120.0), null)

        assertEquals(1f, state.progress)
    }

    @Test
    fun theEndsOfAQueueKnowTheyAreTheEnds() {
        val first: MusicChromeState = musicChromeStateOf(PlayerState(index = 0, queueLength = 3), null)
        val last: MusicChromeState = musicChromeStateOf(PlayerState(index = 2, queueLength = 3), null)

        assertTrue(first.hasNext)
        assertTrue(!first.hasPrevious)
        assertTrue(!last.hasNext)
        assertTrue(last.hasPrevious)
    }

    @Test
    fun playingFollowsTheEngineRatherThanAFlag() {
        val state: MusicChromeState = musicChromeStateOf(PlayerState(playState = PlayState.PLAYING), null)

        assertTrue(state.playing)
    }

    @Test
    fun anItemWithNoTitleIsNotATrackWorthNaming() {
        // The row says there is nothing rather than drawing an empty line, which
        // a listener reads as something that failed to load.
        assertNull(musicTrackOf(BlankItem))
        assertNull(musicTrackOf(null))
    }

    @Test
    fun andOneWithATitleIs() {
        assertEquals(NAMED_TITLE, musicTrackOf(NamedItem)?.title)
    }

    private object BlankItem : PlaylistItem {
        override val id: String = "one"
        override val title: String = "   "
        override val url: String = "file://one.flac"
    }

    private object NamedItem : PlaylistItem {
        override val id: String = "two"
        override val title: String = NAMED_TITLE
        override val url: String = "file://two.flac"
    }
}

private const val A_QUARTER = 0.25f
private const val NAMED_TITLE = "Neon Sky"
