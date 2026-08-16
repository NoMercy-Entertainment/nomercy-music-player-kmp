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

// Starting playback on a device nothing else was using.
//
// The device claims itself active and tells the server, and for one round trip
// the hub keeps broadcasting the state from before it heard — which, when no
// session was running, has no item in it. The session-end branch reads a frame
// with no item as "the session is over everywhere" and stops the audio, so the
// track a viewer had just pressed play on died within milliseconds: measured on
// an SM-A137F as the mp3 decoder reaching RUNNING and then RELEASED eight
// milliseconds later, with no audio, no error and nothing on screen.
class MusicConnectLocalStartTest {

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

    // No opening broadcast, unlike the shield test's rig: this device is the
    // first thing on the session, which is the only state the bug appears in.
    private suspend fun TestScope.rig(): Rig {
        val backend = ConnectBackend()
        val channel = FakeMusicConnectChannel(deviceId = "dev-a")
        val player = NMMusicPlayer(backend)
        val clock = TestClocks()
        val plugin = ClockedPlugin(player, channel, eager(), clock)

        player.setup()
        player.addPlugin(plugin)
        testScheduler.runCurrent()

        player.queue(listOf(Track("a")))
        testScheduler.runCurrent()
        channel.sent.clear()
        return Rig(player, backend, channel, plugin, clock)
    }

    private fun sessionOver(seq: Long, serverTimeMs: Long?) = MusicPlayerState(
        deviceId = "dev-a",
        seq = seq,
        item = null,
        isPlaying = false,
        progressMs = 0,
        durationMs = 0,
        serverTimeMs = serverTimeMs,
    )

    private suspend fun TestScope.send(rig: Rig, frame: MusicPlayerState) {
        rig.channel.broadcast(frame)
        testScheduler.runCurrent()
    }

    @Test
    fun aFrameFromBeforeTheStartDoesNotStopWhatJustStarted() = runTest {
        val rig: Rig = rig()

        rig.player.play()
        testScheduler.runCurrent()

        // Produced by the server a moment BEFORE it heard the press.
        send(rig, sessionOver(seq = 2, serverTimeMs = rig.clock.serverMs - 200))

        assertEquals(0, rig.backend.stopCount, "the frame that predates the start stopped playback")
    }

    @Test
    fun anUnstampedFrameFromBeforeTheStartIsAlsoHeld() = runTest {
        // A server too old to stamp its frames cannot be placed either side of
        // the press. Holding costs a stale session on screen for two seconds;
        // adopting costs the audio the viewer just started.
        val rig: Rig = rig()

        rig.player.play()
        testScheduler.runCurrent()

        send(rig, sessionOver(seq = 2, serverTimeMs = null))

        assertEquals(0, rig.backend.stopCount, "an unstamped frame stopped playback")
    }

    @Test
    fun theServerAcknowledgingTheDeviceBeforeTheTrackDoesNotStopIt() = runTest {
        // The frame that actually did it, measured on the device: it arrives
        // AFTER the press, not before, so no stamp comparison saves it. The
        // server had processed the device claim and not yet the track, so it
        // named this device and carried no item.
        val rig: Rig = rig()

        rig.player.play()
        testScheduler.runCurrent()

        send(rig, sessionOver(seq = 2, serverTimeMs = rig.clock.serverMs + 30))

        assertEquals(0, rig.backend.stopCount, "the server's own acknowledgement stopped playback")
    }

    @Test
    fun aSessionThatReallyEndsAfterwardsStillStops() = runTest {
        // The other half. The gap defended above is the settlement window, not
        // the session: once it closes, a frame with no item is a real answer and
        // a device that ignored it would play on into a session nobody owns.
        val rig: Rig = rig()

        rig.player.play()
        testScheduler.runCurrent()

        rig.clock.localMs += OPTIMISTIC_SHIELD_MS
        send(rig, sessionOver(seq = 2, serverTimeMs = rig.clock.serverMs + 200))

        assertEquals(1, rig.backend.stopCount, "a genuine session end was ignored")
    }

    @Test
    fun anotherDeviceEndingTheSessionStopsThisOneImmediately() = runTest {
        // The window only covers this device's own acknowledgement. A frame from
        // somewhere else ending the session ends it here too, settling or not.
        val rig: Rig = rig()

        rig.player.play()
        testScheduler.runCurrent()

        send(
            rig,
            sessionOver(seq = 2, serverTimeMs = rig.clock.serverMs + 30).copy(deviceId = "dev-b"),
        )

        assertEquals(1, rig.backend.stopCount, "another device's session end was ignored")
    }

    @Test
    fun theWindowClosesOnTheFirstFrameThatCarriesATrack() = runTest {
        // Once the server has said what is playing it is caught up, and a
        // session end after that is real however soon it arrives.
        val rig: Rig = rig()

        rig.player.play()
        testScheduler.runCurrent()

        send(
            rig,
            sessionOver(seq = 2, serverTimeMs = rig.clock.serverMs + 30)
                .copy(item = Track("a"), isPlaying = true),
        )
        send(rig, sessionOver(seq = 3, serverTimeMs = rig.clock.serverMs + 40))

        assertEquals(1, rig.backend.stopCount, "the window outlived the server catching up")
    }
}
