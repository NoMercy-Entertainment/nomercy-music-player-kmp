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
import tv.nomercy.player.core.player.VolumeState
import tv.nomercy.player.music.NMMusicPlayer
import tv.nomercy.player.music.Track
import tv.nomercy.player.music.TwoTrackBackend
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

// The server owns the mute flag of the session, the same way it owns repeat and
// shuffle. The active device's engine has to follow it, or a mute pressed on
// another phone changes the pill and leaves the room playing.
class MusicConnectMuteStateTest {

    private class Rig(
        val player: NMMusicPlayer,
        val channel: FakeMusicConnectChannel,
    )

    private fun TestScope.eager(): CoroutineScope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))

    private suspend fun TestScope.rig(): Rig {
        val backend = TwoTrackBackend()
        val channel = FakeMusicConnectChannel(deviceId = "dev-a")
        val player = NMMusicPlayer(backend, backend)
        player.setup()
        player.addPlugin(MusicConnectPlugin(player, channel, eager()))
        testScheduler.runCurrent()
        return Rig(player, channel)
    }

    private suspend fun TestScope.send(rig: Rig, frame: MusicPlayerState) {
        rig.channel.broadcast(frame)
        testScheduler.runCurrent()
    }

    private fun frame(seq: Long, activeDevice: String, muted: Boolean) = MusicPlayerState(
        deviceId = activeDevice,
        seq = seq,
        item = Track("a"),
        isPlaying = true,
        durationMs = 200_000,
        mutedState = muted,
    )

    @Test
    fun anActiveDeviceMutesItsEngineWhenTheFrameSaysMuted() = runTest {
        val rig: Rig = rig()

        send(rig, frame(seq = 1, activeDevice = "dev-a", muted = true))

        assertEquals(VolumeState.MUTED, rig.player.volumeState())
    }

    @Test
    fun anActiveDeviceUnmutesItsEngineWhenALaterFrameSaysNotMuted() = runTest {
        val rig: Rig = rig()
        send(rig, frame(seq = 1, activeDevice = "dev-a", muted = true))

        send(rig, frame(seq = 2, activeDevice = "dev-a", muted = false))

        assertEquals(VolumeState.UNMUTED, rig.player.volumeState())
    }

    @Test
    fun applyingTheFrameMuteSendsNothingBackToTheServer() = runTest {
        val rig: Rig = rig()

        send(rig, frame(seq = 1, activeDevice = "dev-a", muted = true))

        assertTrue(rig.channel.sent.isEmpty(), "the echo became a command: ${rig.channel.sent}")
    }

    @Test
    fun aPassiveDeviceKeepsItsOwnMuteWhateverTheFrameSays() = runTest {
        val rig: Rig = rig()

        send(rig, frame(seq = 1, activeDevice = "dev-b", muted = true))

        assertEquals(VolumeState.UNMUTED, rig.player.volumeState())
    }
}
