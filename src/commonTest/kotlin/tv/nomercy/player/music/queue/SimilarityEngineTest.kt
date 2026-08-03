// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.music.queue

import kotlinx.coroutines.test.runTest
import tv.nomercy.player.core.media.PlaylistItem
import kotlin.test.Test
import kotlin.test.assertEquals

// A port with no shipped adapter is still a contract, and the thing worth
// asserting is that a consumer can satisfy it — the query's defaults reach the
// implementation, and the exclusions a caller passes arrive intact.
class SimilarityEngineTest {

    @Test
    fun aConsumersEngineIsAskedWithTheDefaultsWhenNothingIsTuned() = runTest {
        val engine = RecordingEngine()

        engine.findSimilar(track("seed"))

        assertEquals(SimilarityQuery(), engine.asked)
    }

    @Test
    fun andTheQueueItAlreadyHasIsPassedThroughToBeExcluded() = runTest {
        // The knob that stops a radio mode handing back the three songs already
        // queued, which is the failure a consumer notices first.
        val engine = RecordingEngine()

        engine.findSimilar(track("seed"), SimilarityQuery(limit = 5, excludeIds = listOf("a", "b")))

        assertEquals(listOf("a", "b"), engine.asked?.excludeIds)
        assertEquals(5, engine.asked?.limit)
    }

    @Test
    fun anEngineWithNothingToOfferAnswersEmptyRatherThanFailing() = runTest {
        assertEquals(emptyList(), RecordingEngine().findSimilar(track("seed")))
    }
}

private class RecordingEngine : SimilarityEngine<TestTrack> {
    var asked: SimilarityQuery? = null

    override val id: String = "recording"

    override suspend fun findSimilar(seed: TestTrack, options: SimilarityQuery): List<TestTrack> {
        asked = options
        return emptyList()
    }
}

private fun track(id: String): TestTrack = TestTrack(id)

private data class TestTrack(override val id: String) : PlaylistItem {
    override val title: String = id
    override val url: String = "file://$id.flac"
}
