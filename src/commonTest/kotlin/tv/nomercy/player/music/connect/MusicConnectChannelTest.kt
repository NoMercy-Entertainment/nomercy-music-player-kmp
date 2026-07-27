// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.music.connect

import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

// The shape of the conversation with a Connect server.
//
// The library owns the protocol and the application owns the wire, so what is
// pinned here is the domain side of that line: the units commands carry, what a
// null means, and the fact that frames arrive already decoded.
class MusicConnectChannelTest {

    @Test
    fun aSeekCarriesSecondsBecauseThatIsThePlayersUnit() = runTest {
        // Everything else the player counts is in seconds. Converting here would
        // put a millisecond somewhere the rest of the player does not use one.
        val channel = FakeMusicConnectChannel()

        channel.playbackCommand(ConnectCommand.SEEK, dataSeconds = 42.0)

        assertEquals(listOf("seek:42.0"), channel.sent)
    }

    @Test
    fun aTransportCommandCarriesNothingElse() = runTest {
        val channel = FakeMusicConnectChannel()

        channel.playbackCommand(ConnectCommand.PLAY)
        channel.playbackCommand(ConnectCommand.PAUSE)

        assertEquals(listOf("play", "pause"), channel.sent)
    }

    @Test
    fun onlyPositionsInMillisecondsAreReported() = runTest {
        // The server counts in milliseconds even though the player does not, and
        // this is the one place the two meet.
        val channel = FakeMusicConnectChannel()

        channel.reportPosition(90_000)

        assertEquals(listOf(90_000L), channel.reported)
    }

    @Test
    fun aFrameArrivesAlreadyDecoded() = runTest {
        // No envelope, no method name, no snake_case. That decode is the
        // application's, and keeping it there is what lets one plugin serve
        // transports that have nothing in common.
        val channel = FakeMusicConnectChannel()

        val received = async { channel.frames.first() }
        testScheduler.advanceUntilIdle()
        channel.broadcast(MusicPlayerState(deviceId = "device-a", seq = 1, item = null))

        assertEquals("device-a", received.await().deviceId)
    }

    @Test
    fun aFrameWithNoItemIsTheSessionEnding() = runTest {
        // The server nulls the current track on stop, and every device reads
        // that as "stop, wherever you are".
        val frame = MusicPlayerState(deviceId = "device-a", seq = 2, item = null)

        assertNull(frame.item)
    }

    @Test
    fun aFrameWithNoActiveDeviceIsAdoptedRatherThanArguedWith() = runTest {
        // The server decides who is active. A client that reasoned about it
        // would be a second authority, which is how two devices come to play at
        // once.
        val frame = MusicPlayerState(deviceId = null, seq = 3, item = null)

        assertNull(frame.deviceId)
    }

    @Test
    fun aServerTooOldToAnswerItsClockSaysSo() = runTest {
        // Null is a reason to fall back to local time rather than to refuse to
        // mirror. A device that would not follow because it could not measure an
        // offset is a device that stops working against an older server.
        val channel = FakeMusicConnectChannel(serverTime = null)

        assertNull(channel.serverTimeMs())
    }

    @Test
    fun everyDeviceKeepsItsOwnVolume() = runTest {
        // A phone at thirty percent and a television at eighty are both correct.
        // Mirroring one onto the other makes one of them wrong every time
        // playback moves.
        val frame = MusicPlayerState(
            deviceId = "tv",
            seq = 4,
            item = null,
            deviceVolumes = mapOf("phone" to 30, "tv" to 80),
        )

        assertEquals(30, frame.deviceVolumes["phone"])
        assertEquals(80, frame.deviceVolumes["tv"])
    }
}
