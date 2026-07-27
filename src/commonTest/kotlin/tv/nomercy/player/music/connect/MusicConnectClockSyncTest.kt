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

// Agreeing with the server about what time it is.
//
// Two devices in different rooms have clocks that differ by seconds, and every
// judgement Connect makes about a frame is a comparison against "now": whether
// it predates a press, and how far the bar has moved since the position was
// taken. Measured against the wrong clock, a device is wrong by the whole skew
// for the whole session and it looks like a broken progress bar rather than a
// wrong clock.
class MusicConnectClockSyncTest {

    // The device's own clock, driven by the test and advanced by what each
    // answer cost on the wire, so a round trip actually takes time here the way
    // it does on a network.
    private class LocalClockPlugin(
        player: NMMusicPlayer,
        private val fake: FakeMusicConnectChannel,
        scope: CoroutineScope,
        private val startedAtMs: Long,
    ) : MusicConnectPlugin(player, fake, scope) {
        override fun nowMs(): Long = startedAtMs + fake.elapsedMs
    }

    private class Rig(
        val player: NMMusicPlayer,
        val channel: FakeMusicConnectChannel,
        val plugin: MusicConnectPlugin,
    )

    private fun TestScope.eager(): CoroutineScope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))

    // The costs are set before the plugin starts, because a plugin measures once
    // on its own the moment it does. Asserting against that first round rather
    // than a hand-driven one is the difference between testing the measurement
    // and testing a method nothing calls in production.
    private suspend fun TestScope.rig(
        serverTimeMs: Long?,
        roundTripCosts: List<Long> = emptyList(),
        startedAtMs: Long = 10_000,
    ): Rig {
        val backend = ConnectBackend()
        val channel = FakeMusicConnectChannel(deviceId = "dev-a", serverTime = serverTimeMs)
        channel.roundTripCosts = roundTripCosts
        val player = NMMusicPlayer(backend)
        val plugin = LocalClockPlugin(player, channel, eager(), startedAtMs)

        player.setup()
        player.addPlugin(plugin)
        testScheduler.runCurrent()
        return Rig(player, channel, plugin)
    }

    @Test
    fun theOffsetIsTheDistanceBetweenTheTwoClocks() = runTest {
        // The device thinks it is 10s past the epoch and the server says 50s.
        // Everything the plugin compares against a frame has to be shifted by
        // that difference or it is judging one clock against another.
        val rig: Rig = rig(serverTimeMs = 50_000)

        assertEquals(40_000, rig.plugin.serverClockOffsetMs)
    }

    @Test
    fun theAnswerIsAnchoredHalfwayThroughTheExchange() = runTest {
        // The server's answer describes a moment somewhere between the request
        // leaving and the reply arriving. Anchoring on either end builds the
        // whole round trip into the offset, and on a slow connection that is a
        // bar seconds out of place.
        val rig: Rig = rig(serverTimeMs = 50_000, roundTripCosts = listOf(400))

        // Sent at 10_000, answered at 10_400, so the exchange sits at 10_200.
        assertEquals(39_800, rig.plugin.serverClockOffsetMs)
    }

    @Test
    fun theQuickestRoundTripIsTheOneKept() = runTest {
        // A sample that came back quickly was queued behind less, so its guess at
        // one-way latency is closer to true. Averaging it with a slow one drags
        // the answer toward the worse sample.
        val rig: Rig = rig(serverTimeMs = 50_000, roundTripCosts = listOf(2_000, 2_000, 20, 2_000, 2_000))

        // The third exchange runs from 14_000 to 14_020, centred on 14_010.
        assertEquals(35_990, rig.plugin.serverClockOffsetMs)
    }

    @Test
    fun aServerThatCannotAnswerLeavesTheDeviceOnItsOwnClock() = runTest {
        // Falling back to local time is approximate. Refusing to mirror is a
        // blank screen, and the two are not close in value.
        val rig: Rig = rig(serverTimeMs = null)

        assertEquals(0, rig.plugin.serverClockOffsetMs)
    }

    @Test
    fun aServerThatStopsAnsweringKeepsTheOffsetItAlreadyHad() = runTest {
        // A measurement that failed is not a measurement of zero. Throwing away
        // a good offset because one round failed would swing every judgement by
        // the whole skew until the next success.
        val rig: Rig = rig(serverTimeMs = 50_000)

        rig.channel.serverTime = null
        rig.plugin.syncClock()

        assertEquals(40_000, rig.plugin.serverClockOffsetMs)
    }

    @Test
    fun theMirrorInterpolatesOnTheServersClockRatherThanThisDevices() = runTest {
        // The outcome all of it is for. The frame says the position was taken at
        // the moment the server sent it, so a synced device draws exactly that
        // position — and an unsynced one draws the skew as forty seconds of
        // playback that never happened.
        val rig: Rig = rig(serverTimeMs = 50_000)

        rig.channel.broadcast(
            MusicPlayerState(
                deviceId = "dev-b",
                seq = 1,
                item = Track("a"),
                isPlaying = true,
                progressMs = 30_000,
                durationMs = 200_000,
                positionCapturedAtMs = 50_000,
            ),
        )
        testScheduler.runCurrent()

        assertEquals(30_000, rig.plugin.mirror.value.positionMs)
    }

    @Test
    fun aFrameTakenAWhileAgoIsDrawnWhereItWouldBeNow() = runTest {
        // The correction is the whole reason the clock is measured. A bar drawn
        // at the number in the frame is always behind by however long the frame
        // took to arrive.
        val rig: Rig = rig(serverTimeMs = 50_000)

        rig.channel.broadcast(
            MusicPlayerState(
                deviceId = "dev-b",
                seq = 1,
                item = Track("a"),
                isPlaying = true,
                progressMs = 30_000,
                durationMs = 200_000,
                positionCapturedAtMs = 48_000,
            ),
        )
        testScheduler.runCurrent()

        // Taken two seconds ago on the server's clock, so that is where it is.
        assertEquals(32_000, rig.plugin.mirror.value.positionMs)
    }
}
