// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.music.queue

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PlaylistGeneratorTest {

    @Test
    fun linearWalksForwardAndStopsAtTheEnd() {
        val generator = LinearPlaylistGenerator()

        assertEquals(1, generator.next(size = 3, currentIndex = 0))
        assertEquals(2, generator.next(size = 3, currentIndex = 1))
        assertNull(generator.next(size = 3, currentIndex = 2))
    }

    @Test
    fun linearWalksBackAndStopsAtTheStart() {
        val generator = LinearPlaylistGenerator()

        assertEquals(1, generator.previous(size = 3, currentIndex = 2))
        assertNull(generator.previous(size = 3, currentIndex = 0))
    }

    // -1 is "nothing playing", so the first next is the first track.
    @Test
    fun nothingPlayingStartsAtTheTop() {
        assertEquals(0, LinearPlaylistGenerator().next(size = 3, currentIndex = -1))
    }

    @Test
    fun anEmptyQueueHasNoNext() {
        assertNull(LinearPlaylistGenerator().next(size = 0, currentIndex = -1))
        assertNull(NonRepeatingShuffleGenerator().next(size = 0, currentIndex = -1))
    }

    // The whole point of the shuffle: a uniform random one repeats within a
    // short window often enough that people call it broken.
    @Test
    fun theShuffleVisitsEveryTrackBeforeRepeatingAny() {
        // Always the first remaining candidate, so the walk is deterministic
        // and the property under test is the exclusion, not the randomness.
        val generator = NonRepeatingShuffleGenerator(random = { 0 })
        val size = 5
        val visited = mutableListOf<Int>()

        var current = 0
        visited += current
        repeat(size - 1) {
            val next = generator.next(size, current)
            assertTrue(next != null, "the shuffle ran out after ${visited.size} of $size")
            visited += next
            current = next
        }

        assertEquals(size, visited.toSet().size, "a track repeated: $visited")
    }

    // A repeating playlist should start a fresh shuffle rather than replay the
    // same order, which is what a listener expects the second time round.
    @Test
    fun anExhaustedShuffleResetsRatherThanStayingExhausted() {
        val generator = NonRepeatingShuffleGenerator(random = { 0 })
        val size = 3

        var current = 0
        repeat(size - 1) { current = generator.next(size, current)!! }

        assertNull(generator.next(size, current), "the queue should report exhausted once")
        assertTrue(generator.next(size, current) != null, "the next round never started")
    }

    // Pressing previous on a shuffled queue and getting the track before it in
    // the LIST plays something the listener has never heard.
    @Test
    fun previousWalksBackThroughWhatWasPlayed() {
        val generator = NonRepeatingShuffleGenerator(random = { 0 })
        val size = 4

        val first = 2
        val second = generator.next(size, first)!!
        val third = generator.next(size, second)!!

        assertEquals(second, generator.previous(size, third))
    }

    @Test
    fun previousBeforeAnythingPlayedIsNothing() {
        assertNull(NonRepeatingShuffleGenerator().previous(size = 4, currentIndex = -1))
    }

    // Both generators are named, because the name shows up in a log and in the
    // testbed's plugin tree.
    @Test
    fun bothGeneratorsAreNamed() {
        assertEquals("linear", LinearPlaylistGenerator().id)
        assertEquals("shuffle", NonRepeatingShuffleGenerator().id)
    }
}
