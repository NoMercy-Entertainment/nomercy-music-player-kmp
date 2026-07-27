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
import tv.nomercy.player.music.NMMusicPlayer
import tv.nomercy.player.music.Track
import tv.nomercy.player.music.TwoTrackBackend
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

// A device following someone else's playback.
//
// The invariant under all of it is that a passive device never loads and never
// plays — that is what makes a second stream impossible rather than merely
// unlikely — and every case that checks the mirror moved is paired with one
// checking the engine did not.
class MusicConnectPassiveTest {

    private class Rig(
        val player: NMMusicPlayer,
        val backend: TwoTrackBackend,
        val channel: FakeMusicConnectChannel,
        val plugin: MusicConnectPlugin,
    )

    private fun TestScope.eager(): CoroutineScope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))

    private suspend fun TestScope.rig(): Rig {
        val backend = TwoTrackBackend()
        val channel = FakeMusicConnectChannel(deviceId = "dev-a")
        val player = NMMusicPlayer(backend, backend)
        val plugin = MusicConnectPlugin(player, channel, eager())

        player.setup()
        player.addPlugin(plugin)
        testScheduler.advanceUntilIdle()
        return Rig(player, backend, channel, plugin)
    }

    // runCurrent rather than advanceUntilIdle, so delivering a frame does not
    // also run the bar to the end of the track. Every case below moves the clock
    // itself, by the amount it means to measure.
    private suspend fun TestScope.send(rig: Rig, frame: MusicPlayerState) {
        rig.channel.broadcast(frame)
        testScheduler.runCurrent()
    }

    private fun playingElsewhere(seq: Long = 1, progressMs: Long = 30_000) = MusicPlayerState(
        deviceId = "dev-b",
        seq = seq,
        item = Track("a"),
        isPlaying = true,
        progressMs = progressMs,
        durationMs = 200_000,
    )

    @Test
    fun aPassiveDeviceShowsTheTrackWithoutLoadingIt() = runTest {
        val rig: Rig = rig()

        send(rig, playingElsewhere())

        assertEquals("a", rig.plugin.mirror.value.item?.id)
        assertEquals(0, rig.backend.playCount, "a passive device started playing")
    }

    @Test
    fun theBarMovesBetweenFramesRatherThanSittingStill() = runTest {
        // A server broadcasts on change, not continuously. A bar drawn only from
        // frames sits still for a whole track and then jumps.
        val rig: Rig = rig()
        send(rig, playingElsewhere(progressMs = 30_000))

        testScheduler.advanceTimeBy(1_000)
        testScheduler.runCurrent()

        assertTrue(
            rig.plugin.mirror.value.positionMs > 30_000,
            "the bar stayed at ${rig.plugin.mirror.value.positionMs}",
        )
    }

    @Test
    fun theBarMovingDoesNotStartAnything() = runTest {
        // The other half. A bar that moves because the device started playing
        // would look identical from the mirror and be the bug.
        val rig: Rig = rig()
        send(rig, playingElsewhere())

        testScheduler.advanceTimeBy(2_000)
        testScheduler.runCurrent()

        assertEquals(0, rig.backend.playCount)
    }

    @Test
    fun anArrivingFrameCorrectsTheBarRatherThanCompoundingWithIt() = runTest {
        // Interpolation drifts. Every frame is the truth, and a client that
        // added to its own guess instead would walk further away each time.
        val rig: Rig = rig()
        send(rig, playingElsewhere(seq = 1, progressMs = 30_000))
        testScheduler.advanceTimeBy(5_000)
        testScheduler.runCurrent()

        send(rig, playingElsewhere(seq = 2, progressMs = 31_000))

        assertEquals(31_000, rig.plugin.mirror.value.positionMs)
    }

    @Test
    fun aPausedElsewhereBarStopsMoving() = runTest {
        val rig: Rig = rig()
        send(rig, playingElsewhere())
        send(
            rig,
            MusicPlayerState(
                deviceId = "dev-b",
                seq = 2,
                item = Track("a"),
                isPlaying = false,
                progressMs = 40_000,
                durationMs = 200_000,
            ),
        )

        testScheduler.advanceTimeBy(3_000)
        testScheduler.runCurrent()

        assertEquals(40_000, rig.plugin.mirror.value.positionMs, "a paused mirror kept advancing")
    }

    @Test
    fun theBarStopsAtTheEndOfTheTrack() = runTest {
        // Otherwise a mirror left running past the end shows a position longer
        // than the song, which a bar draws as overflowing its own track.
        //
        // The start is deliberately not a whole number of ticks from the end. At
        // 199_000 the last tick lands exactly on the duration and the clamp is
        // never asked to do anything — the test passed with it removed, which is
        // how this was found. Real durations are not multiples of 250ms.
        val rig: Rig = rig()
        send(rig, playingElsewhere(progressMs = 199_100))

        testScheduler.advanceTimeBy(10_000)
        testScheduler.runCurrent()

        assertEquals(200_000, rig.plugin.mirror.value.positionMs)
    }

    @Test
    fun becomingActiveClearsTheMirror() = runTest {
        // The device is now the one playing and renders from its own player. A
        // mirror left behind would draw a second, stale progress bar.
        val rig: Rig = rig()
        send(rig, playingElsewhere())

        send(rig, MusicPlayerState(deviceId = "dev-a", seq = 2, item = Track("a"), isPlaying = true))

        assertNull(rig.plugin.mirror.value.item)
    }

    @Test
    fun theSessionEndingClearsTheMirror() = runTest {
        val rig: Rig = rig()
        send(rig, playingElsewhere())

        send(rig, MusicPlayerState(deviceId = "dev-b", seq = 2, item = null))

        assertNull(rig.plugin.mirror.value.item)
    }

    @Test
    fun aDisposedPluginStopsMovingTheBar() = runTest {
        // A ticker outliving its plugin is a coroutine per player ever opened,
        // each of them waking four times a second forever.
        val rig: Rig = rig()
        send(rig, playingElsewhere())
        rig.plugin.dispose()
        val stoppedAt: Long = rig.plugin.mirror.value.positionMs

        testScheduler.advanceTimeBy(5_000)
        testScheduler.runCurrent()

        assertEquals(stoppedAt, rig.plugin.mirror.value.positionMs, "a disposed plugin kept ticking")
    }
}
