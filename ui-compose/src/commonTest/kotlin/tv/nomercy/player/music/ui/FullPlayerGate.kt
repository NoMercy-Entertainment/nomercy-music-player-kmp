// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.music.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.test.swipeRight
import tv.nomercy.player.core.player.RepeatState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

// The player somebody opens to choose something.
//
// The row and this are drawn from one state and one set of commands, so what is
// worth asserting here is the part that is only true of this one: it can be
// reached and left, the scrub actually moves the track, and the queue plays the
// row that was tapped rather than the one that was current.
@OptIn(ExperimentalTestApi::class)
abstract class FullPlayerGate {

    private val strings = MusicStrings()

    private class Recording : MusicCommands {
        var lastSeek: Double? = null
        var lastQueueIndex: Int? = null
        var lastShuffled: Boolean? = null
        var lastRepeat: RepeatState? = null

        override fun setPlaying(playing: Boolean) = Unit
        override fun next() = Unit
        override fun previous() = Unit

        override fun seekTo(seconds: Double) {
            lastSeek = seconds
        }

        override fun setVolume(percent: Int) = Unit
        override fun setMuted(muted: Boolean) = Unit

        override fun setShuffled(shuffled: Boolean) {
            lastShuffled = shuffled
        }

        override fun setRepeat(repeat: RepeatState) {
            lastRepeat = repeat
        }

        override fun playQueueIndex(index: Int) {
            lastQueueIndex = index
        }
    }

    private val commands = Recording()

    private fun queued(): MusicChromeState = MusicChromeState(
        track = MusicTrack(title = "Neon Sky"),
        durationSeconds = LENGTH,
        queueSize = 3,
        queueIndex = 0,
        queue = listOf(
            MusicTrack(title = "Neon Sky"),
            MusicTrack(title = "The Long Winter"),
            MusicTrack(title = "Salt Flats"),
        ),
    )

    private fun ComposeUiTest.renderFull(state: MusicChromeState = queued()) {
        setContent { FullPlayer(state, commands, strings = strings) }
        waitForIdle()
    }

    @Test
    fun draggingTheScrubberMovesTheTrackOnRelease() {
        // On release, not during. Seeking on every pixel is the seek storm the
        // engine answers by stalling, and the track a listener is hunting for
        // never settles long enough to be recognised.
        runComposeUiTest {
            renderFull()

            onNodeWithTag(SCRUBBER_TAG).performTouchInput { swipeRight() }
            waitForIdle()

            assertTrue(commands.lastSeek != null && commands.lastSeek!! > 0.0)
        }
    }

    @Test
    fun andTheFigureNeverLeavesTheTrack() {
        // A drag that leaves the strip reports a position outside it, and an
        // unclamped answer is a seek past the end.
        assertEquals(LENGTH, secondsAt(WIDE_OF_THE_END, WIDTH, LENGTH))
        assertEquals(0.0, secondsAt(-WIDE_OF_THE_END, WIDTH, LENGTH))
    }

    @Test
    fun tappingAQueueRowPlaysThatRowRatherThanTheCurrentOne() = runComposeUiTest {
        renderFull()

        onNodeWithTag(queueRowTag(2)).performClick()
        waitForIdle()

        assertEquals(2, commands.lastQueueIndex)
    }

    @Test
    fun theQueueNamesWhatIsComing() = runComposeUiTest {
        renderFull()

        onNodeWithText("Salt Flats").assertExists()
    }

    @Test
    fun shuffleAsksForTheOppositeOfWhatIsSet() = runComposeUiTest {
        renderFull(queued().copy(shuffled = true))

        onNodeWithTag(SHUFFLE_TAG).performClick()

        assertEquals(false, commands.lastShuffled)
    }

    @Test
    fun repeatWalksOffThenTheQueueThenTheTrack() = runComposeUiTest {
        // Three states rather than a boolean, because "repeat" without saying
        // what is being repeated is a button whose second press surprises
        // somebody.
        renderFull()

        onNodeWithTag(REPEAT_TAG).performClick()

        assertEquals(RepeatState.ALL, commands.lastRepeat)
        assertEquals(RepeatState.ONE, nextRepeat(RepeatState.ALL))
        assertEquals(RepeatState.OFF, nextRepeat(RepeatState.ONE))
    }

    @Test
    fun aControlIsLabelledWithWhatItWillDo() = runComposeUiTest {
        // Not with what is currently set. A screen reader announcing "repeat
        // off" on the button that turns repeating on is worse than silence.
        renderFull()

        onNodeWithContentDescription(strings.repeatAll).assertExists()
        onNodeWithContentDescription(strings.shuffleOn).assertExists()
    }

    @Test
    fun theRowOpensTheFullPlayerAndTheFullPlayerCloses() = runComposeUiTest {
        var expanded: Boolean by mutableStateOf(false)
        setContent {
            if (expanded) {
                FullPlayer(queued(), commands, strings = strings, onCollapse = { expanded = false })
            } else {
                MiniPlayer(queued(), commands, strings = strings, onExpand = { expanded = true })
            }
        }
        waitForIdle()

        onNodeWithTag(MINI_PLAYER_TAG).performClick()
        waitForIdle()
        onNodeWithTag(FULL_PLAYER_TAG).assertExists()

        onNodeWithTag(COLLAPSE_TAG).performClick()
        waitForIdle()
        onNodeWithTag(MINI_PLAYER_TAG).assertExists()
        onNodeWithTag(FULL_PLAYER_TAG).assertDoesNotExist()
    }
}

private const val LENGTH = 240.0
private const val WIDTH = 100f
private const val WIDE_OF_THE_END = 400f
