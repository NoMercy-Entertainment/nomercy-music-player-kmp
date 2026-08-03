// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.music.queue

import tv.nomercy.player.core.media.PlaylistItem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

private data class Track(
    override val id: String,
    override val genres: List<String> = emptyList(),
    override val decade: String? = null,
    override val url: String = "https://media.example.test/$id.flac",
    override val title: String? = null,
) : TaggedMusicItem

private data class PlainTrack(
    override val id: String,
    override val url: String = "https://media.example.test/$id.flac",
    override val title: String? = null,
) : PlaylistItem

// A fixed sequence, so the pick is a decision rather than a coin toss. The
// generator asks for a random per candidate scored and one more to pick from
// the pool, and a test that could not say which is a test that asserts nothing.
private fun sequence(vararg values: Double): () -> Double {
    var index = 0
    return { values[index++ % values.size] }
}

class SmartShuffleGeneratorTest {

    @Test
    fun aCandidateSharingBothTagsLosesToOneSharingNeither() {
        // The whole point of the generator. With no jitter at all the scores are
        // -2 and 0, and only the untagged-against-current track can win.
        val items = listOf(
            Track("current", genres = listOf("jazz"), decade = "1960s"),
            Track("same", genres = listOf("jazz"), decade = "1960s"),
            Track("different", genres = listOf("punk"), decade = "1980s"),
        )
        val generator = SmartShuffleGenerator(random = sequence(0.0))

        assertEquals(2, generator.next(items, currentIndex = 0))
    }

    @Test
    fun sharingOneTagIsPenalisedLessThanSharingBoth() {
        val items = listOf(
            Track("current", genres = listOf("jazz"), decade = "1960s"),
            Track("both", genres = listOf("jazz"), decade = "1960s"),
            Track("genreOnly", genres = listOf("jazz"), decade = "1980s"),
        )
        val generator = SmartShuffleGenerator(random = sequence(0.0))

        assertEquals(2, generator.next(items, currentIndex = 0), "the doubly-penalised track was picked")
    }

    @Test
    fun theJitterCanNeverLiftAPenalisedCandidateOverAnUnpenalisedOne() {
        // The reason the jitter is 0.5 and a penalty is 1. At the largest jitter
        // a shared-genre track can reach, it is still below an unpenalised track
        // at the smallest.
        val items = listOf(
            Track("current", genres = listOf("jazz")),
            Track("shares", genres = listOf("jazz")),
            Track("fresh", genres = listOf("punk")),
        )
        // 0.999 for the penalised candidate, 0.0 for the fresh one, 0.0 to pick.
        val generator = SmartShuffleGenerator(random = sequence(0.999, 0.0, 0.0))

        assertEquals(2, generator.next(items, currentIndex = 0))
    }

    @Test
    fun aQueueWithNoTagsStillShufflesRatherThanRefusing() {
        // The reference falls back to uniform random when no tag fields are
        // present, and a host with no genre data is the ordinary case.
        val items = listOf(PlainTrack("a"), PlainTrack("b"), PlainTrack("c"))
        val generator = SmartShuffleGenerator(random = sequence(0.0, 0.0, 0.99))

        val picked: Int? = generator.next(items, currentIndex = 0)

        assertNotEquals(0, picked, "the current track was picked as its own successor")
        assertTrue(picked in listOf(1, 2))
    }

    @Test
    fun theCurrentTrackIsNeverItsOwnSuccessor() {
        val items = listOf(Track("a"), Track("b"), Track("c"))
        val generator = SmartShuffleGenerator(random = sequence(0.0))

        for (current in items.indices) {
            assertNotEquals(current, SmartShuffleGenerator(random = sequence(0.0)).next(items, current))
        }
    }

    @Test
    fun aSingleTrackQueueAnswersItselfAndAnEmptyOneAnswersNothing() {
        val generator = SmartShuffleGenerator(random = sequence(0.0))

        assertEquals(0, generator.next(listOf(Track("only")), currentIndex = 0))
        assertNull(generator.next(emptyList(), currentIndex = -1))
    }

    @Test
    fun previousWalksBackThroughWhatWasActuallyPlayed() {
        // On a shuffled queue the track before this one in the LIST is one the
        // listener has never heard, which is the most confusing thing a shuffle
        // can do.
        val items = listOf(Track("a"), Track("b"), Track("c"), Track("d"))
        val generator = SmartShuffleGenerator(random = sequence(0.0))

        generator.next(items, currentIndex = 0)
        generator.next(items, currentIndex = 2)

        assertEquals(2, generator.previous(items, currentIndex = 3))
        assertEquals(0, generator.previous(items, currentIndex = 2))
    }

    @Test
    fun theCountOnlyOverloadStillShufflesForACallerThatHasNoItems() {
        // The older pair on the interface. A generator reached through it cannot
        // score anything, and answering null would break a consumer that had
        // been calling it.
        val generator = SmartShuffleGenerator(random = sequence(0.0))

        assertEquals(1, generator.next(size = 3, currentIndex = 0))
    }
}
