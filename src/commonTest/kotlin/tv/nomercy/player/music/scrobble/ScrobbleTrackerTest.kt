// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.music.scrobble

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ScrobbleTrackerTest {

    // Plays a track second by second, which is what a real tick looks like.
    private fun ScrobbleTracker.play(seconds: Int, from: Double = 0.0) {
        var position = from
        repeat(seconds) {
            position += 1.0
            advanceTo(position)
        }
    }

    @Test
    fun aTrackNobodyListenedToIsNotReported() {
        val tracker = ScrobbleTracker()
        tracker.startTrack("a")

        assertFalse(tracker.shouldScrobble(durationSeconds = 200.0))
    }

    // Half the track.
    @Test
    fun halfATrackIsEnough() {
        val tracker = ScrobbleTracker()
        tracker.startTrack("a")
        tracker.play(99)

        assertFalse(tracker.shouldScrobble(durationSeconds = 200.0))

        tracker.play(2, from = 99.0)
        assertTrue(tracker.shouldScrobble(durationSeconds = 200.0))
    }

    // Or four minutes, whichever comes first — so an hour-long mix scrobbles
    // long before its midpoint.
    @Test
    fun fourMinutesIsEnoughHoweverLongTheTrackIs() {
        val tracker = ScrobbleTracker()
        tracker.startTrack("a")
        tracker.play(240)

        assertTrue(tracker.shouldScrobble(durationSeconds = 3_600.0))
    }

    @Test
    fun nothingUnderThirtySecondsIsEverReported() {
        val tracker = ScrobbleTracker()
        tracker.startTrack("jingle")
        tracker.play(29)

        assertFalse(tracker.shouldScrobble(durationSeconds = 29.0))
    }

    // Once per track, however many times it is asked — which is what makes it
    // safe to ask on every tick and again on ended.
    @Test
    fun aTrackIsReportedExactlyOnce() {
        val tracker = ScrobbleTracker()
        tracker.startTrack("a")
        tracker.play(120)

        assertTrue(tracker.shouldScrobble(durationSeconds = 200.0))
        assertFalse(tracker.shouldScrobble(durationSeconds = 200.0))
        assertTrue(tracker.hasScrobbled())
    }

    // The rule that stops somebody scrobbling an album by dragging the
    // scrubber across it.
    @Test
    fun seekingDoesNotCountAsListening() {
        val tracker = ScrobbleTracker()
        tracker.startTrack("a")

        tracker.advanceTo(0.5)
        tracker.advanceTo(150.0)
        tracker.advanceTo(199.0)

        assertTrue(tracker.listenedSeconds() < 2.0, "a seek was counted: ${tracker.listenedSeconds()}")
        assertFalse(tracker.shouldScrobble(durationSeconds = 200.0))
    }

    // A seek back is not un-listening to what was already heard.
    @Test
    fun seekingBackwardsDoesNotSubtract() {
        val tracker = ScrobbleTracker()
        tracker.startTrack("a")
        tracker.play(60)
        val heard = tracker.listenedSeconds()

        tracker.advanceTo(5.0)

        assertEquals(heard, tracker.listenedSeconds())
    }

    // ...and listening again after seeking back keeps accumulating, so
    // replaying a chorus twice really is more listening.
    @Test
    fun listeningAgainAfterASeekKeepsCounting() {
        val tracker = ScrobbleTracker()
        tracker.startTrack("a")
        tracker.play(60)
        tracker.advanceTo(5.0)
        tracker.play(60, from = 5.0)

        assertTrue(tracker.listenedSeconds() >= 119.0, "only ${tracker.listenedSeconds()}")
    }

    @Test
    fun aNewTrackClearsEverythingIncludingTheLatch() {
        val tracker = ScrobbleTracker()
        tracker.startTrack("a")
        tracker.play(120)
        tracker.shouldScrobble(durationSeconds = 200.0)

        tracker.startTrack("b")

        assertEquals("b", tracker.currentTrack())
        assertEquals(0.0, tracker.listenedSeconds())
        assertFalse(tracker.hasScrobbled())
    }

    // A duration the engine has not reported yet must not scrobble on a
    // threshold computed from nothing.
    @Test
    fun anUnknownDurationNeverScrobbles() {
        val tracker = ScrobbleTracker()
        tracker.startTrack("a")
        tracker.play(240)

        assertFalse(tracker.shouldScrobble(durationSeconds = 0.0))
        assertFalse(tracker.shouldScrobble(durationSeconds = Double.NaN))
        assertFalse(tracker.shouldScrobble(durationSeconds = Double.POSITIVE_INFINITY))
    }

    @Test
    fun theThresholdsAreOverridable() {
        val tracker = ScrobbleTracker(
            ScrobbleRules(thresholdRatio = 0.1, minDurationSeconds = 5.0),
        )
        tracker.startTrack("a")
        tracker.play(6)

        assertTrue(tracker.shouldScrobble(durationSeconds = 50.0))
    }
}
