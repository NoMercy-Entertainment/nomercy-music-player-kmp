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
import tv.nomercy.player.core.events.ItemEndingSoon
import tv.nomercy.player.core.media.PlaylistItem
import tv.nomercy.player.core.ports.CrossfadeCurve
import tv.nomercy.player.core.ports.TransitionBackend
import tv.nomercy.player.music.MusicEvents
import tv.nomercy.player.music.NMMusicPlayer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import tv.nomercy.player.testing.FakeMediaBackend
import tv.nomercy.player.testing.TestItem

// An engine that can hold two tracks at once, so the hand-off is observable.
private class TwoSlotBackend : TransitionBackend, tv.nomercy.player.core.ports.MediaBackend by FakeMediaBackend() {
    val secondaryLoads: MutableList<String> = mutableListOf()
    var crossfades: Int = 0
        private set

    override fun supportsCrossfade(): Boolean = true

    override suspend fun loadSecondary(url: String) {
        secondaryLoads += url
    }

    override suspend fun primeSecondary(seekMs: Long): Unit = Unit

    override suspend fun crossfade(durationMs: Long, curve: CrossfadeCurve) {
        crossfades += 1
    }

    override fun disposeSecondary(): Unit = Unit

    override fun secondaryGain(): Float = 1.0f

    override fun secondaryGain(value: Float): Unit = Unit
}

// itemEndingSoon had a producer and no consumer at all: use() subscribed only to
// `ended`, so the hand-off window the plugin exists to use went by untouched.
class AutoAdvanceEndingSoonTest {

    private fun TestScope.rig(opts: AutoAdvanceOptions): Pair<NMMusicPlayer, TwoSlotBackend> {
        val engine = TwoSlotBackend()
        val player = NMMusicPlayer(engine, engine, backgroundScope, id = "auto-advance-ending-soon")
        player.queue(listOf(TestItem("a"), TestItem("b")))
        backgroundScope.launch {
            player.addPlugin(AutoAdvancePlugin(player, opts))
            player.setup()
            player.playItem("a")
        }
        runCurrent()
        return player to engine
    }

    private suspend fun warn(player: NMMusicPlayer) {
        player.emit(CoreEvents.ItemEndingSoon, ItemEndingSoon(player.item(), remaining = 4.0))
    }

    @Test
    fun theWindowHandsOffToTheCrossfade() = runTest {
        val opts = AutoAdvanceOptions(crossfade = true, crossfadeDuration = 2.0)
        val (player, engine) = rig(opts)

        warn(player)
        runCurrent()

        assertEquals(1, engine.crossfades, "the event fired into nothing, so every track change was a hard cut")
    }

    @Test
    fun theComingTrackIsLoadedIntoTheSecondSlotBeforeTheFade() = runTest {
        val opts = AutoAdvanceOptions(crossfade = true, crossfadeDuration = 2.0)
        val (player, engine) = rig(opts)

        warn(player)
        runCurrent()

        assertEquals(listOf(TestItem("b").url), engine.secondaryLoads)
    }

    @Test
    fun theHandOffIsAnnouncedSoAChromeCanDrawAnUpNextCard() = runTest {
        val opts = AutoAdvanceOptions(crossfade = true, crossfadeDuration = 2.0)
        val (player, _) = rig(opts)
        val started: MutableList<String> = mutableListOf()
        player.on(MusicEvents.CrossfadeStart) { started += it.to.id }

        warn(player)
        runCurrent()

        assertEquals(listOf("b"), started)
    }

    @Test
    fun crossfadeIsOffUntilTheHostAsksForIt() = runTest {
        // Crossfading by surprise is worse than not crossfading.
        val opts = AutoAdvanceOptions()
        val (player, engine) = rig(opts)

        warn(player)
        runCurrent()

        assertEquals(0, engine.crossfades)
    }

    @Test
    fun aDisabledPluginDoesNothingWithTheWindow() = runTest {
        val opts = AutoAdvanceOptions(enabled = false, crossfade = true, crossfadeDuration = 2.0)
        val (player, engine) = rig(opts)

        warn(player)
        runCurrent()

        assertEquals(0, engine.crossfades)
    }

    @Test
    fun theGeneratorDecidesWhatIsWarmed() = runTest {
        // A radio that says "stop" must not have the queue's own next track
        // faded in behind it.
        val opts = AutoAdvanceOptions(
            crossfade = true,
            crossfadeDuration = 2.0,
            generator = object : PlaylistGenerator {
                override val id: String = "stops"
                override fun next(size: Int, currentIndex: Int): Int? = null
                override fun previous(size: Int, currentIndex: Int): Int? = null
            },
        )
        val (player, engine) = rig(opts)

        warn(player)
        runCurrent()

        assertTrue(engine.secondaryLoads.isEmpty())
    }

    @Test
    fun theEndOfTheQueueIsNotAFailure() = runTest {
        val opts = AutoAdvanceOptions(crossfade = true, crossfadeDuration = 2.0)
        val engine = TwoSlotBackend()
        val player = NMMusicPlayer(engine, engine, backgroundScope, id = "auto-advance-tail")
        val single: List<PlaylistItem> = listOf(TestItem("only"))
        player.queue(single)
        backgroundScope.launch {
            player.addPlugin(AutoAdvancePlugin(player, opts))
            player.setup()
            player.playItem("only")
        }
        runCurrent()

        warn(player)
        runCurrent()

        assertEquals(0, engine.crossfades)
    }
}
