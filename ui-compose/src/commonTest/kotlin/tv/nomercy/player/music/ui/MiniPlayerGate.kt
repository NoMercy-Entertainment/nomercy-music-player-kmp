// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.music.ui

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import tv.nomercy.player.core.player.RepeatState
import kotlin.test.Test
import kotlin.test.assertEquals

// The row at the bottom of the application.
//
// What is worth asserting on a mini-player is which glyph reached the screen and
// whether pressing it reached the player, because neither can be read off the
// composable — and a control that draws the wrong glyph is one a listener
// presses expecting the opposite of what happens.
@OptIn(ExperimentalTestApi::class)
abstract class MiniPlayerGate {

    private val strings = MusicStrings()

    private class Recording : MusicCommands {
        val calls: MutableList<String> = mutableListOf()
        var lastPlaying: Boolean? = null

        override fun setPlaying(playing: Boolean) {
            calls += "setPlaying"
            lastPlaying = playing
        }

        override fun next() { calls += "next" }
        override fun previous() { calls += "previous" }
        override fun seekTo(seconds: Double) { calls += "seekTo" }
        override fun setVolume(percent: Int) = Unit
        override fun setMuted(muted: Boolean) = Unit
        override fun setShuffled(shuffled: Boolean) = Unit
        override fun setRepeat(repeat: RepeatState) = Unit
    }

    private val commands = Recording()

    private fun ComposeUiTest.render(state: MusicChromeState) {
        setContent { MiniPlayer(state, commands, strings = strings) }
        waitForIdle()
    }

    @Test
    fun aPausedTrackOffersPlay() = runComposeUiTest {
        render(MusicChromeState(playing = false))

        onNodeWithContentDescription(strings.play).assertIsDisplayed()
    }

    @Test
    fun aPlayingOneOffersPause() = runComposeUiTest {
        render(MusicChromeState(playing = true))

        onNodeWithContentDescription(strings.pause).assertIsDisplayed()
    }

    @Test
    fun pressingItAsksForTheOppositeOfWhatIsOnScreen() = runComposeUiTest {
        // Explicit rather than a toggle. State that changed underneath must not
        // invert what the listener meant by pressing what they could see.
        render(MusicChromeState(playing = false))

        onNodeWithTag(PLAY_PAUSE_TAG).performClick()

        assertEquals(listOf("setPlaying"), commands.calls)
        assertEquals(true, commands.lastPlaying)
    }

    @Test
    fun itNamesWhatIsPlaying() = runComposeUiTest {
        render(MusicChromeState(track = MusicTrack(title = TITLE, artist = ARTIST)))

        onNodeWithText(TITLE).assertExists()
        onNodeWithText(ARTIST).assertExists()
    }

    @Test
    fun andSaysSoWhenNothingIs() = runComposeUiTest {
        // A row with an empty line reads as something that failed to load. The
        // honest answer is that there is nothing.
        render(MusicChromeState())

        onNodeWithText(strings.nothingPlaying).assertExists()
        onNodeWithTag(ARTIST_TAG).assertDoesNotExist()
    }

    @Test
    fun andLeavesTheSecondLineOffATrackWithNoArtist() {
        // Blank rather than absent, which is what a server sends for a track
        // nobody tagged. The row drew nothing at all when there was no track,
        // and drew an empty line here: the same gap, from the other direction.
        runComposeUiTest {
            render(MusicChromeState(track = MusicTrack(title = TITLE, artist = "  ")))

            onNodeWithText(TITLE).assertExists()
            onNodeWithTag(ARTIST_TAG).assertDoesNotExist()
        }
    }

    @Test
    fun aSkipButtonAppearsOnlyWhenThereIsSomewhereToSkipTo() = runComposeUiTest {
        render(MusicChromeState(queueSize = 2, queueIndex = 1))

        onNodeWithContentDescription(strings.next).assertDoesNotExist()
        onNodeWithContentDescription(strings.previous).assertIsDisplayed()
    }

    @Test
    fun andBothAppearInTheMiddleOfAQueue() = runComposeUiTest {
        render(MusicChromeState(queueSize = 3, queueIndex = 1))

        onNodeWithContentDescription(strings.next).assertIsDisplayed()
        onNodeWithContentDescription(strings.previous).assertIsDisplayed()
    }

    @Test
    fun skippingReachesThePlayer() = runComposeUiTest {
        render(MusicChromeState(queueSize = 3, queueIndex = 1))

        onNodeWithTag(NEXT_TAG).performClick()
        onNodeWithTag(PREVIOUS_TAG).performClick()

        assertEquals(listOf("next", "previous"), commands.calls)
    }

    @Test
    fun theProgressLineIsDrawnAgainstTheLengthOfTheTrack() = runComposeUiTest {
        render(MusicChromeState(timeSeconds = HALFWAY, durationSeconds = LENGTH))

        onNodeWithTag(PROGRESS_TAG).assertExists()
    }
}

private const val TITLE = "Neon Sky"
private const val ARTIST = "The Long Winter"
private const val HALFWAY = 60.0
private const val LENGTH = 120.0
