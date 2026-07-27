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
import tv.nomercy.player.core.player.RepeatState
import tv.nomercy.player.core.player.ShuffleState
import tv.nomercy.player.music.NMMusicPlayer
import tv.nomercy.player.music.Track
import tv.nomercy.player.music.TwoTrackBackend
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

// What a device does when the server tells it something.
//
// The applier is where a client stops deciding and starts following, so what is
// asserted is the outcome on the player rather than on the plugin's own fields:
// a queue a viewer can see, a repeat mode a button reflects, a stop that
// actually reached the engine.
class MusicConnectInboundTest {

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
        testScheduler.runCurrent()
        return Rig(player, backend, channel, plugin)
    }

    private suspend fun TestScope.send(rig: Rig, frame: MusicPlayerState) {
        rig.channel.broadcast(frame)
        testScheduler.runCurrent()
    }

    @Test
    fun theQueueTheServerSendsIsTheQueueEveryDeviceShows() = runTest {
        // A passive device showing a different list from the one playing is a
        // viewer looking at the wrong thing — and it becomes the wrong thing to
        // play from the moment they take over.
        val rig: Rig = rig()

        send(
            rig,
            MusicPlayerState(
                deviceId = "dev-b",
                seq = 1,
                item = Track("a"),
                playlist = listOf(Track("b"), Track("c")),
            ),
        )

        assertEquals(listOf("a", "b", "c"), rig.player.queue().map { it.id })
    }

    @Test
    fun theCurrentTrackLeadsTheQueueRatherThanBeingLostFromIt() = runTest {
        // The server sends what comes AFTER the current one, so a client that
        // used the list verbatim would show the next track as the current one
        // and drop what is actually playing.
        val rig: Rig = rig()

        send(rig, MusicPlayerState(deviceId = "dev-b", seq = 1, item = Track("a"), playlist = listOf(Track("b"))))

        assertEquals("a", rig.player.queue().first().id)
    }

    @Test
    fun repeatAndShuffleAreFollowedByADeviceThatIsNotPlaying() = runTest {
        // They belong to the session rather than to the playback, so a passive
        // device that ignored them would show the wrong state on its own buttons.
        val rig: Rig = rig()

        send(
            rig,
            MusicPlayerState(
                deviceId = "dev-b",
                seq = 1,
                item = Track("a"),
                repeatState = RepeatState.ALL,
                shuffleState = true,
            ),
        )

        assertEquals(RepeatState.ALL, rig.player.repeatState())
        assertEquals(ShuffleState.ON, rig.player.shuffleState())
    }

    @Test
    fun aFrameWithNoTrackEndsTheSessionEverywhere() = runTest {
        // The server nulls the current item on stop. Every device stops, not
        // just the one that was playing.
        val rig: Rig = rig()
        send(rig, MusicPlayerState(deviceId = "dev-a", seq = 1, item = Track("a")))

        send(rig, MusicPlayerState(deviceId = "dev-a", seq = 2, item = null))

        assertEquals(DeviceRole.NONE, rig.plugin.role, "a device stayed active through a session end")
    }

    @Test
    fun aSessionEndClearsTheRoleBeforeAnythingElseReadsIt() = runTest {
        // Ordering, and it matters: reconciling the role first would leave the
        // device that just stopped being active taking the passive branch and
        // mirroring a session that no longer exists.
        val rig: Rig = rig()
        send(rig, MusicPlayerState(deviceId = "dev-b", seq = 1, item = Track("a")))
        assertEquals(DeviceRole.PASSIVE, rig.plugin.role)

        send(rig, MusicPlayerState(deviceId = "dev-b", seq = 2, item = null))

        assertEquals(DeviceRole.NONE, rig.plugin.role)
    }

    @Test
    fun aStaleFrameNeverReachesThePlayer() = runTest {
        // The gate, measured where it matters: not that the plugin ignored it,
        // but that the queue a viewer is looking at did not change.
        val rig: Rig = rig()
        send(rig, MusicPlayerState(deviceId = "dev-b", seq = 5, item = Track("a"), playlist = listOf(Track("b"))))

        send(rig, MusicPlayerState(deviceId = "dev-b", seq = 3, item = Track("z"), playlist = emptyList()))

        assertEquals(listOf("a", "b"), rig.player.queue().map { it.id }, "a stale frame rewrote the queue")
    }

    @Test
    fun followingTheServerDoesNotTalkBackToIt() = runTest {
        // Everything the applier drives is marked as the server's doing, and the
        // outbound guards read that. Without it, one broadcast becomes a command
        // from every device that received it.
        val rig: Rig = rig()

        send(
            rig,
            MusicPlayerState(
                deviceId = "dev-a",
                seq = 1,
                item = Track("a"),
                repeatState = RepeatState.ALL,
                shuffleState = true,
            ),
        )

        assertEquals(emptyList(), rig.channel.sent, "following the server sent it ${rig.channel.sent}")
    }

    @Test
    fun aSessionEndDoesNotSendAStopBackToTheServer() = runTest {
        // The one place the marking has teeth. Stop is a guarded action, so an
        // unmarked stop driven by the applier becomes an outbound command — and
        // with several devices receiving the same broadcast, one session end
        // becomes one stop per device arriving back at the hub.
        val rig: Rig = rig()
        send(rig, MusicPlayerState(deviceId = "dev-a", seq = 1, item = Track("a")))
        rig.channel.sent.clear()

        send(rig, MusicPlayerState(deviceId = "dev-a", seq = 2, item = null))

        assertEquals(emptyList(), rig.channel.sent, "a session end was echoed as ${rig.channel.sent}")
    }

    @Test
    fun anUnsequencedServerIsStillFollowed() = runTest {
        // A server old enough not to sequence its broadcasts. Refusing those
        // means refusing to work with it at all.
        val rig: Rig = rig()

        send(rig, MusicPlayerState(deviceId = "dev-b", seq = 0, item = Track("a")))

        assertTrue(rig.player.queue().isNotEmpty(), "an unsequenced frame was dropped")
    }
}
