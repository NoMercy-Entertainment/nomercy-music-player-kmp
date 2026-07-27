// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.music.connect

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import tv.nomercy.player.music.ConnectBackend
import tv.nomercy.player.music.NMMusicPlayer
import tv.nomercy.player.music.Track
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

// A press on a device that is not the one playing.
//
// The command goes to the server and the answer takes a round trip. In that gap
// the hub is still broadcasting the old state, so a device that adopted every
// frame would flip the button back under the viewer's finger and then flip it
// forward again a moment later. The shield holds the press against frames the
// server produced before it heard about it — and only those, because a later
// frame is a real answer, including a refusal.
class MusicConnectShieldTest {

    // Both clocks are driven, and they are separate on purpose: the shield is
    // stamped on the server's and expires on this device's, so a test that moved
    // one number could not tell those two apart.
    private class ClockedPlugin(
        player: NMMusicPlayer,
        channel: MusicConnectChannel,
        scope: CoroutineScope,
        private val clock: TestClocks,
    ) : MusicConnectPlugin(player, channel, scope) {
        override fun serverNowMs(): Long = clock.serverMs
        override fun nowMs(): Long = clock.localMs
    }

    private class TestClocks(var serverMs: Long = 1_000_000, var localMs: Long = 500)

    private class Rig(
        val player: NMMusicPlayer,
        val backend: ConnectBackend,
        val channel: FakeMusicConnectChannel,
        val plugin: MusicConnectPlugin,
        val clock: TestClocks,
    )

    private fun TestScope.eager(): CoroutineScope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))

    private suspend fun TestScope.rig(): Rig {
        val backend = ConnectBackend()
        val channel = FakeMusicConnectChannel(deviceId = "dev-a")
        val player = NMMusicPlayer(backend)
        val clock = TestClocks()
        val plugin = ClockedPlugin(player, channel, eager(), clock)

        player.setup()
        player.addPlugin(plugin)
        testScheduler.runCurrent()

        // Passive: another device holds the session, playing.
        channel.broadcast(elsewhere(seq = 1, isPlaying = true, serverTimeMs = 999_000))
        testScheduler.runCurrent()
        channel.sent.clear()
        return Rig(player, backend, channel, plugin, clock)
    }

    private fun elsewhere(
        seq: Long,
        isPlaying: Boolean,
        serverTimeMs: Long?,
        progressMs: Long = 10_000,
    ) = MusicPlayerState(
        deviceId = "dev-b",
        seq = seq,
        item = Track("a"),
        isPlaying = isPlaying,
        progressMs = progressMs,
        durationMs = 200_000,
        serverTimeMs = serverTimeMs,
    )

    private suspend fun TestScope.send(rig: Rig, frame: MusicPlayerState) {
        rig.channel.broadcast(frame)
        testScheduler.runCurrent()
    }

    @Test
    fun aPressShowsTheIntentBeforeTheServerHasAnswered() = runTest {
        val rig: Rig = rig()

        rig.player.pause()

        assertFalse(rig.plugin.mirror.value.isPlaying, "the button did not follow the press")
        assertEquals(listOf("pause"), rig.channel.sent)
    }

    @Test
    fun showingTheIntentDoesNotStartAnythingLocally() = runTest {
        // The half that matters. A device that showed the intent by actually
        // playing would be the second stream, and from the mirror alone the two
        // look identical.
        val rig: Rig = rig()

        rig.player.play()

        assertEquals(0, rig.backend.playCount, "a passive press reached the engine")
    }

    @Test
    fun theBarDoesNotStartMovingOnAPressAlone() = runTest {
        // Interpolating from a position nobody has confirmed compounds a guess on
        // a guess. The button flips now; the bar starts when the answer arrives
        // carrying a real position.
        val rig: Rig = rig()
        send(rig, elsewhere(seq = 2, isPlaying = false, serverTimeMs = 999_100))
        rig.player.play()
        val startedAt: Long = rig.plugin.mirror.value.positionMs

        testScheduler.advanceTimeBy(2_000)
        testScheduler.runCurrent()

        assertEquals(startedAt, rig.plugin.mirror.value.positionMs, "the bar ran ahead of the server")
    }

    @Test
    fun aFrameTheServerSentBeforeThePressDoesNotUndoIt() = runTest {
        val rig: Rig = rig()
        rig.player.pause()

        send(rig, elsewhere(seq = 2, isPlaying = true, serverTimeMs = 999_500))

        assertFalse(rig.plugin.mirror.value.isPlaying, "a stale frame flipped the button back")
    }

    @Test
    fun aFrameTheServerSentAfterThePressWins() = runTest {
        // Including a refusal. The server is the authority, and a shield that
        // outlasted the answer would leave the device showing something the
        // session is not doing.
        val rig: Rig = rig()
        rig.player.pause()

        send(rig, elsewhere(seq = 2, isPlaying = true, serverTimeMs = 1_000_500))

        assertTrue(rig.plugin.mirror.value.isPlaying, "the real answer was ignored")
    }

    @Test
    fun theShieldLetsGoOnceItsTimeIsUp() = runTest {
        // A lost command must correct itself. Without the bound the device shows
        // a press that never landed until something else happens to change it.
        val rig: Rig = rig()
        rig.player.pause()

        rig.clock.localMs += OPTIMISTIC_SHIELD_MS
        send(rig, elsewhere(seq = 2, isPlaying = true, serverTimeMs = 999_500))

        assertTrue(rig.plugin.mirror.value.isPlaying, "an expired shield was still holding")
    }

    @Test
    fun onlyTheDirectionIsHeldAndNotThePosition() = runTest {
        // The press said play or pause. It said nothing about where the track
        // is, and holding that too would freeze the bar for two seconds every
        // time someone pressed a button.
        val rig: Rig = rig()
        rig.player.pause()

        send(rig, elsewhere(seq = 2, isPlaying = true, serverTimeMs = 999_500, progressMs = 40_000))

        assertEquals(40_000, rig.plugin.mirror.value.positionMs)
        assertFalse(rig.plugin.mirror.value.isPlaying)
    }

    @Test
    fun aServerThatDoesNotStampItsFramesIsHeldRatherThanObeyed() = runTest {
        // An unstamped frame cannot be placed either side of the press, and the
        // shield expires on its own. Adopting it instead is the flicker.
        val rig: Rig = rig()
        rig.player.pause()

        send(rig, elsewhere(seq = 2, isPlaying = true, serverTimeMs = null))

        assertFalse(rig.plugin.mirror.value.isPlaying)
    }

    @Test
    fun aSeekIsNotShielded() = runTest {
        // Only play and pause are shown ahead of the answer. A scrub bar that
        // jumped to a position the server had not accepted would then jump back.
        val rig: Rig = rig()
        val before: Long = rig.plugin.mirror.value.positionMs

        rig.player.time(88.0)

        assertEquals(before, rig.plugin.mirror.value.positionMs)
        assertEquals(listOf("seek:88.0"), rig.channel.sent)
    }

    @Test
    fun takingOverDropsTheShield() = runTest {
        // This device is now the one playing and renders from its own player. A
        // shield left armed would hold a stale intent over the next session it
        // happened to be passive in.
        val rig: Rig = rig()
        rig.player.pause()

        send(
            rig,
            MusicPlayerState(deviceId = "dev-a", seq = 2, item = Track("a"), serverTimeMs = 999_500),
        )
        send(rig, elsewhere(seq = 3, isPlaying = true, serverTimeMs = 999_600))

        assertTrue(rig.plugin.mirror.value.isPlaying, "a shield survived a handoff")
    }
}
