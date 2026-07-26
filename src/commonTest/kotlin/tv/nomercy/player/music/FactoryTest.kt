// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.music

import kotlinx.coroutines.test.runTest
import tv.nomercy.player.core.player.PlayerConfig
import tv.nomercy.player.core.ports.CrossfadeTransitionStrategy
import tv.nomercy.player.core.ports.GaplessTransitionStrategy
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

// The factory, the registry, and the defaults setup fills in.
class FactoryTest {

    private val ids: MutableList<String> = mutableListOf()

    private fun named(id: String): NMMusicPlayer {
        ids += id
        return nmMusicPlayer(id)
    }

    @AfterTest
    fun forgetPlayers() {
        // The registry is process-wide, so a test that left one behind would
        // hand it to the next test asking for the same id.
        ids.forEach(NMMusicPlayer::forget)
    }

    @Test
    fun theSameIdGivesBackTheSamePlayer() {
        // Two music players in one app fight over the audio focus, and the
        // second wins by silencing the first.
        assertSame(named("library"), nmMusicPlayer("library"))
    }

    @Test
    fun aDifferentIdIsADifferentPlayer() {
        // Deliberate is allowed: a preview scrubber beside the main player is
        // something a consumer may genuinely want.
        assertNotEquals(named("main"), named("preview"))
    }

    @Test
    fun aPlayerCanBeForgottenSoItsIdIsFreeAgain() {
        val first: NMMusicPlayer = named("session")

        NMMusicPlayer.forget("session")

        assertNotEquals(first, named("session"))
    }

    @Test
    fun aPlayerIsFindableByTheIdItWasBuiltUnder() {
        val player: NMMusicPlayer = named("library")

        assertSame(player, NMMusicPlayer.byId("library"))
    }

    @Test
    fun anIdNobodyBuiltIsNotFound() {
        assertEquals(null, NMMusicPlayer.byId("never-built"))
    }

    @Test
    fun setupCrossfadesByDefaultBecauseThatIsWhatMusicDoes() = runTest {
        // A music player that crossfades out of the box is what everyone
        // expects, and a video player that did would be wrong — which is why
        // this default lives here and not in core.
        val player: NMMusicPlayer = named("defaults")

        player.setup(PlayerConfig(crossfadeLeadSeconds = 3.0, crossfadeTailSeconds = 3.0))

        assertTrue(player.crossfadeEnabled())
        assertTrue(player.transitionStrategy() is CrossfadeTransitionStrategy)
    }

    @Test
    fun aConsumersOwnStrategySurvivesSetup() = runTest {
        // Someone who set a strategy asked for it. A library that overwrote it
        // at setup would be answering a question nobody asked.
        val player: NMMusicPlayer = named("consumer-strategy")
        val chosen = GaplessTransitionStrategy()
        player.setTransitionStrategy(chosen)

        player.setup(PlayerConfig(crossfadeTailSeconds = 3.0))

        assertSame(chosen, player.transitionStrategy())
    }

    @Test
    fun aConsumersOwnDurationSurvivesSetup() = runTest {
        // Read through the engine, because the duration is configuration rather
        // than state and there is no getter for it: what a crossfade actually
        // asks the backend to fade for is the only honest measure.
        val backend = TwoTrackBackend()
        val player = NMMusicPlayer(backend, backend, id = "consumer-duration")
        ids += "consumer-duration"
        player.configureCrossfade(8.0)

        player.setup(PlayerConfig(crossfadeTailSeconds = 3.0))
        player.queue(listOf(Track("a"), Track("b")))
        player.play()
        player.crossfadeTo(Track("b"))

        assertEquals(8_000L, backend.fadedForMs, "setup overwrote a duration the consumer chose")
    }

    @Test
    fun setupFillsInTheDurationWhenTheConsumerDidNot() = runTest {
        val backend = TwoTrackBackend()
        val player = NMMusicPlayer(backend, backend, id = "default-duration")
        ids += "default-duration"

        player.setup(PlayerConfig(crossfadeTailSeconds = 3.0))
        player.queue(listOf(Track("a"), Track("b")))
        player.play()
        player.crossfadeTo(Track("b"))

        assertEquals(3_000L, backend.fadedForMs)
    }
}
