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
import tv.nomercy.player.core.player.ActionOptions
import tv.nomercy.player.core.player.ActionSource
import tv.nomercy.player.music.NMMusicPlayer
import tv.nomercy.player.music.TwoTrackBackend
import tv.nomercy.player.music.Track
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

// What this device does when the viewer presses something.
//
// Two shapes, and every case that asserts one is paired with the same action in
// the other role — because "passive does not play" passes on a player that never
// plays, and "active plays" passes on a plugin that does nothing at all.
class MusicConnectOutboundTest {

    private class Rig(
        val player: NMMusicPlayer,
        val backend: TwoTrackBackend,
        val channel: FakeMusicConnectChannel,
        val plugin: MusicConnectPlugin,
    )

    private fun TestScope.eager(): CoroutineScope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))

    // The role is seeded by a frame rather than set by hand, so what is tested
    // is the plugin deriving it — the way it will on a real hub.
    private suspend fun TestScope.rig(activeDeviceId: String?): Rig {
        val backend = TwoTrackBackend()
        val channel = FakeMusicConnectChannel(deviceId = "dev-a")
        val player = NMMusicPlayer(backend, backend)
        val plugin = MusicConnectPlugin(player, channel, eager())

        player.setup()
        player.queue(listOf(Track("a"), Track("b")))
        player.addPlugin(plugin)

        // Pumped before broadcasting, because the plugin collects on the
        // player's own scheduler and a frame emitted before that collection
        // starts is dropped — a shared flow with no replay behaves like a real
        // hub, which does not resend what happened before you connected.
        testScheduler.runCurrent()
        channel.broadcast(MusicPlayerState(deviceId = activeDeviceId, seq = 1, item = Track("a")))
        testScheduler.runCurrent()

        channel.sent.clear()
        return Rig(player, backend, channel, plugin)
    }

    @Test
    fun theActiveDeviceTellsTheServerAndCarriesOnPlaying() = runTest {
        // It is already the one making sound. Stopping to wait for a round trip
        // would put a gap in the audio for the person holding it.
        val rig: Rig = rig(activeDeviceId = "dev-a")

        rig.player.play()

        assertTrue(rig.backend.playCount > 0, "the active device did not play")
        assertEquals(listOf("play"), rig.channel.sent)
    }

    @Test
    fun aPassiveDeviceTellsTheServerAndStaysSilent() = runTest {
        // It is not the one playing. Letting it start is the second stream this
        // whole subsystem exists to remove.
        val rig: Rig = rig(activeDeviceId = "dev-b")

        rig.player.play()

        assertEquals(0, rig.backend.playCount, "a passive device started playing")
        assertEquals(listOf("play"), rig.channel.sent)
    }

    @Test
    fun everyTransportVerbCarriesItsOwnName() = runTest {
        // A plugin routing only play leaves a viewer able to pause the device in
        // their hand and nothing else.
        val rig: Rig = rig(activeDeviceId = "dev-b")

        rig.player.pause()
        rig.player.next()
        rig.player.previous()

        assertEquals(listOf("pause", "next", "previous"), rig.channel.sent)
    }

    @Test
    fun aSeekCarriesItsPositionInSeconds() = runTest {
        val rig: Rig = rig(activeDeviceId = "dev-b")

        rig.player.time(42.0)

        assertEquals(listOf("seek:42.0"), rig.channel.sent)
    }

    @Test
    fun anActionTheServerCausedIsNotSentBackToIt() = runTest {
        // The server told this device to pause, the player raised its own
        // before-pause, and sending that back asks the server to do what it just
        // said had happened. On a hub with several listeners that is a loop.
        val rig: Rig = rig(activeDeviceId = "dev-a")

        rig.player.pause(ActionOptions(source = ActionSource.REMOTE))

        assertEquals(emptyList(), rig.channel.sent)
    }

    @Test
    fun anEchoedActionStillHappensLocally() = runTest {
        // The other half of the same rule. Not echoing it back must not mean
        // ignoring it — the server said pause, so this device pauses.
        val rig: Rig = rig(activeDeviceId = "dev-a")
        rig.player.play()
        val pausedBefore: Int = rig.backend.pauseCount

        rig.player.pause(ActionOptions(source = ActionSource.REMOTE))

        assertTrue(rig.backend.pauseCount > pausedBefore, "an action from the server did nothing here")
    }

    @Test
    fun withNoActiveDeviceAnywhereThisOneStillDoesNotAssumeItIsPlaying() = runTest {
        // Nothing is playing. This device is not active, so it reports and waits
        // to be told — a device that assumed would be the second authority.
        val rig: Rig = rig(activeDeviceId = null)

        rig.player.play()

        assertEquals(0, rig.backend.playCount)
        assertEquals(listOf("play"), rig.channel.sent)
    }

    @Test
    fun theRoleComesFromTheServerRatherThanFromThisDevice() = runTest {
        val rig: Rig = rig(activeDeviceId = "dev-b")
        assertEquals(DeviceRole.PASSIVE, rig.plugin.role)

        rig.channel.broadcast(MusicPlayerState(deviceId = "dev-a", seq = 2, item = Track("a")))
        testScheduler.runCurrent()

        assertEquals(DeviceRole.ACTIVE, rig.plugin.role)
    }

    @Test
    fun aDisposedPluginStopsFollowingTheHub() = runTest {
        // A subscription outliving its plugin keeps applying frames on behalf of
        // a player that is gone — and on a device that opens a player per screen
        // that is one live listener per screen ever opened.
        val rig: Rig = rig(activeDeviceId = "dev-b")

        rig.plugin.dispose()
        rig.channel.broadcast(MusicPlayerState(deviceId = "dev-a", seq = 5, item = Track("a")))
        testScheduler.runCurrent()

        assertEquals(DeviceRole.PASSIVE, rig.plugin.role, "a disposed plugin was still listening")
    }

    @Test
    fun aStaleFrameDoesNotChangeWhoIsPlaying() = runTest {
        // The gate, seen through the plugin. A redelivered frame naming the
        // previous active device would hand playback back to a device that has
        // already given it up.
        val rig: Rig = rig(activeDeviceId = "dev-a")

        rig.channel.broadcast(MusicPlayerState(deviceId = "dev-b", seq = 1, item = Track("a")))
        testScheduler.runCurrent()

        assertEquals(DeviceRole.ACTIVE, rig.plugin.role, "a stale frame moved playback")
    }
}
