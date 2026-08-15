// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.music.queue

import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import tv.nomercy.player.core.events.CoreEvents
import tv.nomercy.player.music.NMMusicPlayer
import tv.nomercy.player.testing.FakeMediaBackend
import tv.nomercy.player.testing.TestItem
import kotlin.test.Test
import kotlin.test.assertEquals

// A generator that draws a DIFFERENT pick on every call given the same
// inputs — the shape SmartShuffleGenerator has in practice (random, plus its
// own mutable play history mutated inside next()). Proves the plugin resolves
// "what's next" once per transition rather than re-asking a stateful
// generator, which is the invariant resolveNextCached() exists for.
private class SequencedGenerator(private vararg val picks: Int) : PlaylistGenerator {
    override val id: String = "sequenced"

    var callCount: Int = 0
        private set

    override fun next(size: Int, currentIndex: Int): Int? {
        val pick: Int = picks.getOrNull(callCount) ?: return null
        callCount++
        return pick
    }

    override fun previous(size: Int, currentIndex: Int): Int? = null
}

class AutoAdvancePreloadConsistencyTest {

    private fun TestScope.rig(generator: PlaylistGenerator): Pair<NMMusicPlayer, AutoAdvancePlugin> {
        val engine = FakeMediaBackend()
        val player = NMMusicPlayer(engine, null, backgroundScope, id = "auto-advance-consistency")
        player.queue(listOf(TestItem("a"), TestItem("b"), TestItem("c")))
        val plugin = AutoAdvancePlugin(player, AutoAdvanceOptions(generator = generator))
        backgroundScope.launch {
            player.addPlugin(plugin)
            player.setup()
            player.playItem("a")
        }
        runCurrent()
        return player to plugin
    }

    @Test
    fun preloadAndTheEventualAdvanceShareOneGeneratorCallNotTwo() = runTest {
        // picks[0] = index 1 ("b"). If preload and advance each independently
        // ask the generator, advance would consume picks[1] = index 2 ("c")
        // instead — the coming track and the one that plays would disagree.
        val generator = SequencedGenerator(1, 2)
        val (player, plugin) = rig(generator)

        plugin.preloadNext()
        runCurrent()
        assertEquals(1, generator.callCount, "preload should have drawn exactly one pick")

        player.emit(CoreEvents.Ended, Unit)
        runCurrent()

        assertEquals(
            1,
            generator.callCount,
            "advance() re-asked the generator instead of reusing the pick preload already warmed",
        )
        assertEquals("b", player.item()?.id, "advance() played a different pick than the one that was warmed")
    }

    @Test
    fun aFreshTransitionDrawsAgainOnceTheItemActuallyChanges() = runTest {
        val generator = SequencedGenerator(1, 0) // "b", then back to "a"
        val (player, _) = rig(generator)

        player.emit(CoreEvents.Ended, Unit)
        runCurrent()
        assertEquals("b", player.item()?.id)
        assertEquals(1, generator.callCount)

        player.emit(CoreEvents.Ended, Unit)
        runCurrent()

        assertEquals("a", player.item()?.id, "the previous transition's cached pick leaked into this one")
        assertEquals(2, generator.callCount)
    }
}
