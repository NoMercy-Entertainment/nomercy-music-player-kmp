// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.music.connect

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import tv.nomercy.player.core.player.PlayState
import tv.nomercy.player.music.ConnectBackend
import tv.nomercy.player.music.NMMusicPlayer
import tv.nomercy.player.music.Track
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

// Two devices, one hub, and the bug this whole subsystem exists to remove.
//
// Everything else in this package tests one device against a fake server. This
// tests the thing that actually went wrong in the shipped application: two
// clients, each convinced it should be playing, producing the same song twice a
// fraction of a second apart. Both players here are real, both plugins are real,
// and they share one channel — so a rule that only holds because a test drove
// one device has nowhere to hide.
class ConnectHandoffAcceptanceTest {

    // One hub with a view per device. The frames are the same objects for both;
    // only the identity each one compares them against differs, which is exactly
    // how the server sees it.
    private class Hub {
        private val broadcasts = MutableSharedFlow<MusicPlayerState>(replay = 0, extraBufferCapacity = 64)

        fun channelFor(id: String): HubChannel = HubChannel(id, broadcasts)

        suspend fun broadcast(state: MusicPlayerState) {
            broadcasts.emit(state)
        }
    }

    private class HubChannel(
        override val deviceId: String,
        override val frames: Flow<MusicPlayerState>,
    ) : MusicConnectChannel {
        val sent: MutableList<String> = mutableListOf()

        override suspend fun playbackCommand(command: String, dataSeconds: Double?) {
            sent += if (dataSeconds == null) command else "$command:$dataSeconds"
        }

        override suspend fun changeDevice(targetDeviceId: String) {
            sent += "changeDevice:$targetDeviceId"
        }

        override suspend fun reportPosition(positionMs: Long) = Unit

        override suspend fun serverTimeMs(): Long? = null
    }

    private class Device(
        val id: String,
        val player: NMMusicPlayer,
        val backend: ConnectBackend,
        val channel: HubChannel,
        val plugin: MusicConnectPlugin,
    ) {
        val isPlayingNow: Boolean get() = player.playState() == PlayState.PLAYING
    }

    private fun TestScope.eager(): CoroutineScope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))

    private suspend fun TestScope.device(hub: Hub, id: String): Device {
        val backend = ConnectBackend()
        val channel = hub.channelFor(id)
        val player = NMMusicPlayer(backend)
        val plugin = MusicConnectPlugin(player, channel, eager())

        player.setup()
        player.addPlugin(plugin)
        testScheduler.runCurrent()
        return Device(id, player, backend, channel, plugin)
    }

    private fun frame(
        deviceId: String?,
        seq: Long,
        id: String? = "a",
        isPlaying: Boolean = true,
        progressMs: Long = 0,
    ) = MusicPlayerState(
        deviceId = deviceId,
        seq = seq,
        item = id?.let { Track(it) },
        isPlaying = isPlaying,
        progressMs = progressMs,
        durationMs = 200_000,
    )

    @Test
    fun exactlyOneDeviceEverProducesSound() = runTest {
        val hub = Hub()
        val a: Device = device(hub, "dev-a")
        val b: Device = device(hub, "dev-b")

        hub.broadcast(frame(deviceId = "dev-a", seq = 1))
        testScheduler.runCurrent()

        assertTrue(a.isPlayingNow, "the device the server named is not playing")
        assertTrue(!b.isPlayingNow, "a second device is playing the same song")
    }

    @Test
    fun thePassiveDeviceNeverEvenOpensTheStream() = runTest {
        // Stronger than "it is not playing". A passive device that loaded the
        // source is one call away from being a second stream, and on a metered
        // connection it has already cost the viewer the bandwidth.
        val hub = Hub()
        val a: Device = device(hub, "dev-a")
        val b: Device = device(hub, "dev-b")

        hub.broadcast(frame(deviceId = "dev-a", seq = 1))
        testScheduler.runCurrent()

        assertEquals(listOf("https://example.test/a"), a.backend.loadedUrls)
        assertEquals(emptyList(), b.backend.loadedUrls, "a passive device opened the stream")
    }

    @Test
    fun aHandoffMovesTheAudioWithNoInstantWhereBothPlay() = runTest {
        // The whole acceptance. Checked after every frame rather than at the end,
        // because an overlap that opens and closes between two broadcasts is
        // exactly the bug and a final assertion would miss it.
        val hub = Hub()
        val a: Device = device(hub, "dev-a")
        val b: Device = device(hub, "dev-b")
        val overlaps: MutableList<Long> = mutableListOf()

        for (seq in 1L..6L) {
            val holder: String = if (seq % 2L == 1L) "dev-a" else "dev-b"
            hub.broadcast(frame(deviceId = holder, seq = seq, progressMs = seq * 1_000))
            testScheduler.runCurrent()
            if (a.isPlayingNow && b.isPlayingNow) overlaps += seq
        }

        assertEquals(emptyList(), overlaps, "both devices were playing at once after frames $overlaps")
        assertTrue(!a.isPlayingNow && b.isPlayingNow, "the audio did not end up on the device holding it")
    }

    @Test
    fun theDeviceHandingOverPausesRatherThanStopping() = runTest {
        // A stop tears the bar down. Someone who has just pushed the music to
        // another room is still looking at this screen and wants to see where it
        // has got to.
        val hub = Hub()
        val a: Device = device(hub, "dev-a")
        device(hub, "dev-b")

        hub.broadcast(frame(deviceId = "dev-a", seq = 1))
        testScheduler.runCurrent()
        hub.broadcast(frame(deviceId = "dev-b", seq = 2))
        testScheduler.runCurrent()

        assertEquals(1, a.backend.pauseCount)
        assertEquals(0, a.backend.stopCount, "handing over stopped the device instead of pausing it")
    }

    @Test
    fun theDeviceTakingOverPicksUpWhereTheOtherLeftOff() = runTest {
        // Otherwise a handoff restarts the song, which is the failure a viewer
        // actually reports: "it started again when I moved it to the kitchen".
        val hub = Hub()
        device(hub, "dev-a")
        val b: Device = device(hub, "dev-b")

        hub.broadcast(frame(deviceId = "dev-a", seq = 1, progressMs = 95_000))
        testScheduler.runCurrent()
        hub.broadcast(frame(deviceId = "dev-b", seq = 2, progressMs = 95_000))
        testScheduler.runCurrent()

        assertEquals(listOf(95.0), b.backend.seekedTo)
    }

    @Test
    fun aRedeliveredFrameIsRefusedByBothDevices() = runTest {
        // A hub redelivers on reconnect. A frame naming the previous holder,
        // applied after a handoff, hands the audio back to a device that has
        // already given it up — and the frame after it takes it away again.
        val hub = Hub()
        val a: Device = device(hub, "dev-a")
        val b: Device = device(hub, "dev-b")
        hub.broadcast(frame(deviceId = "dev-a", seq = 5))
        testScheduler.runCurrent()
        hub.broadcast(frame(deviceId = "dev-b", seq = 6))
        testScheduler.runCurrent()

        hub.broadcast(frame(deviceId = "dev-a", seq = 5))
        testScheduler.runCurrent()

        assertEquals(DeviceRole.PASSIVE, a.plugin.role, "a stale frame promoted a device back")
        assertEquals(DeviceRole.ACTIVE, b.plugin.role)
        assertTrue(!a.isPlayingNow, "a stale frame started a second stream")
    }

    @Test
    fun endingTheSessionSilencesEveryDevice() = runTest {
        val hub = Hub()
        val a: Device = device(hub, "dev-a")
        val b: Device = device(hub, "dev-b")
        hub.broadcast(frame(deviceId = "dev-a", seq = 1))
        testScheduler.runCurrent()

        hub.broadcast(frame(deviceId = "dev-a", seq = 2, id = null))
        testScheduler.runCurrent()

        assertTrue(!a.isPlayingNow && !b.isPlayingNow)
        assertEquals(DeviceRole.NONE, a.plugin.role)
        assertEquals(DeviceRole.NONE, b.plugin.role)
        assertEquals(null, b.plugin.mirror.value.item, "a mirror survived the session that fed it")
    }

    @Test
    fun aPressOnThePassiveDeviceReachesTheServerWithoutMakingSound() = runTest {
        // What a viewer does with the device in their hand while the music plays
        // somewhere else. It has to reach the hub, and it must not start a second
        // copy on the way.
        val hub = Hub()
        val a: Device = device(hub, "dev-a")
        val b: Device = device(hub, "dev-b")
        hub.broadcast(frame(deviceId = "dev-a", seq = 1))
        testScheduler.runCurrent()
        b.channel.sent.clear()

        b.player.pause()
        testScheduler.runCurrent()

        assertEquals(listOf("pause"), b.channel.sent)
        assertEquals(0, b.backend.playCount, "a passive press produced sound")
        assertTrue(a.isPlayingNow, "the press stopped the device that was actually playing")
    }

    @Test
    fun neitherDeviceTalksBackWhenItIsOnlyFollowing() = runTest {
        // One broadcast reaching two devices must not become two commands back.
        // On a hub with several listeners that is a loop, and it grows with the
        // number of devices in the house.
        val hub = Hub()
        val a: Device = device(hub, "dev-a")
        val b: Device = device(hub, "dev-b")

        hub.broadcast(frame(deviceId = "dev-a", seq = 1))
        testScheduler.runCurrent()
        hub.broadcast(frame(deviceId = "dev-b", seq = 2))
        testScheduler.runCurrent()

        assertEquals(emptyList(), a.channel.sent, "the handing device answered with ${a.channel.sent}")
        assertEquals(emptyList(), b.channel.sent, "the receiving device answered with ${b.channel.sent}")
    }
}
