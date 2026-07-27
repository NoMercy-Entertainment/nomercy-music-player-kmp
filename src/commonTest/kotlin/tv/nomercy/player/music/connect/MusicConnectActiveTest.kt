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
import tv.nomercy.player.core.ports.CanonicalBackendEvent
import tv.nomercy.player.music.ConnectBackend
import tv.nomercy.player.music.NMMusicPlayer
import tv.nomercy.player.music.Track
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

// The device that is actually playing, brought back in line with the server.
//
// Taking over is the case worth being careful about. The engine holds the wrong
// track or none, so it has to load and wait: seeking a source that is not set
// does nothing and playing it starts the wrong song at the wrong place. Every
// case here is written so that a plugin doing the right things in the wrong
// order fails it.
class MusicConnectActiveTest {

    private class Rig(
        val player: NMMusicPlayer,
        val backend: ConnectBackend,
        val channel: FakeMusicConnectChannel,
        val plugin: MusicConnectPlugin,
    )

    private fun TestScope.eager(): CoroutineScope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))

    private suspend fun TestScope.rig(): Rig {
        val backend = ConnectBackend()
        val channel = FakeMusicConnectChannel(deviceId = "dev-a")
        val player = NMMusicPlayer(backend)
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

    // No capture instant and no send time, so the seek target is the frame's own
    // number and a case about ordering is not also a case about arithmetic. The
    // latency correction has its own tests below.
    private fun playingHere(
        seq: Long,
        id: String = "a",
        progressMs: Long = 12_000,
        isPlaying: Boolean = true,
    ) = MusicPlayerState(
        deviceId = "dev-a",
        seq = seq,
        item = Track(id),
        isPlaying = isPlaying,
        progressMs = progressMs,
        durationMs = 200_000,
    )

    @Test
    fun takingOverLoadsTheTrackTheServerNames() = runTest {
        val rig: Rig = rig()

        send(rig, playingHere(seq = 1))

        assertEquals(listOf("https://example.test/a"), rig.backend.loadedUrls)
    }

    @Test
    fun theSeekAndThePlayWaitForTheLoadToFinish() = runTest {
        // The bug this exists to stop. A seek issued before the engine holds the
        // source is dropped, and the play that follows it starts the track from
        // the beginning — so a viewer taking over halfway through a song hears
        // it restart.
        val rig: Rig = rig()
        rig.backend.holdTheNextLoad()

        send(rig, playingHere(seq = 1))

        assertEquals(emptyList(), rig.backend.seekedTo, "seeked into a source that was not open yet")
        assertEquals(0, rig.backend.playCount, "played before the engine had the track")
    }

    @Test
    fun onceTheLoadFinishesItSeeksThenPlays() = runTest {
        // The other half. Waiting is only correct if it then acts.
        val rig: Rig = rig()
        rig.backend.holdTheNextLoad()
        send(rig, playingHere(seq = 1))

        rig.backend.finishLoading()
        testScheduler.runCurrent()

        assertEquals(listOf(12.0), rig.backend.seekedTo)
        assertEquals(1, rig.backend.playCount)
    }

    @Test
    fun takingOverAPausedSessionLoadsAndSeeksWithoutPlaying() = runTest {
        // Handing a paused session to another device must not start it. The
        // viewer moved the session, they did not press play.
        val rig: Rig = rig()

        send(rig, playingHere(seq = 1, isPlaying = false))

        assertEquals(listOf(12.0), rig.backend.seekedTo)
        assertEquals(0, rig.backend.playCount, "a paused session started playing on handoff")
    }

    @Test
    fun aFailedLoadDoesNotLeaveTheSeekArmedForTheNextTrack() = runTest {
        // A load that errors never reports ready. If the continuation is left
        // armed, the NEXT track's readiness runs this frame's seek — so a song
        // that failed to open makes the following one start at its position.
        val rig: Rig = rig()
        rig.backend.holdTheNextLoad()
        send(rig, playingHere(seq = 1, progressMs = 90_000))
        rig.backend.fire(CanonicalBackendEvent.STREAM_ERROR)
        testScheduler.runCurrent()
        rig.backend.finishLoading()
        testScheduler.runCurrent()

        assertTrue(
            rig.backend.seekedTo.none { it == 90.0 },
            "a cancelled continuation still seeked: ${rig.backend.seekedTo}",
        )
    }

    @Test
    fun aSmallDriftIsLeftAlone() = runTest {
        // Correcting every frame would make each one a seek, and a seek on a
        // music engine is an audible gap. The tolerance exists to keep the
        // common case silent.
        val rig: Rig = rig()
        send(rig, playingHere(seq = 1, progressMs = 12_000))
        val seeksAfterLoad: Int = rig.backend.seekedTo.size

        send(rig, playingHere(seq = 2, progressMs = 14_000))

        assertEquals(seeksAfterLoad, rig.backend.seekedTo.size, "a two second drift was corrected")
    }

    @Test
    fun aDriftBigEnoughToHearIsCorrected() = runTest {
        val rig: Rig = rig()
        send(rig, playingHere(seq = 1, progressMs = 12_000))

        send(rig, playingHere(seq = 2, progressMs = 40_000))

        assertEquals(40.0, rig.backend.seekedTo.last())
    }

    @Test
    fun aTrackChangeLoadsRatherThanSeekingWithinTheOldOne() = runTest {
        // Seeking to the new track's position inside the old one is a jump to a
        // random point of the wrong song, which is what a same-track path
        // applied to a different track does.
        val rig: Rig = rig()
        send(rig, playingHere(seq = 1, id = "a"))

        send(rig, playingHere(seq = 2, id = "b"))

        assertEquals(listOf("https://example.test/a", "https://example.test/b"), rig.backend.loadedUrls)
    }

    @Test
    fun reconcilingDoesNotTalkBackToTheServer() = runTest {
        // Everything the applier drives is marked as the server's doing. Without
        // it, one broadcast becomes a seek and a play command from every device
        // that received it.
        val rig: Rig = rig()

        send(rig, playingHere(seq = 1))
        send(rig, playingHere(seq = 2, progressMs = 40_000))

        assertEquals(emptyList(), rig.channel.sent, "reconciling sent ${rig.channel.sent}")
    }

    @Test
    fun theActiveDeviceIsNotAlsoMirroring() = runTest {
        // It renders from its own player because it is the thing playing. A
        // mirror left running would draw a second progress bar beside the real
        // one, moving on its own schedule.
        val rig: Rig = rig()

        send(rig, playingHere(seq = 1))

        assertEquals(null, rig.plugin.mirror.value.item)
    }

    @Test
    fun playingSomethingAlreadyAtTheRightPlaceStartsItWithoutReloading() = runTest {
        // A pause and resume driven from another device. The track is loaded and
        // the position is right, so a reload here would be an audible re-buffer
        // on every resume.
        val rig: Rig = rig()
        send(rig, playingHere(seq = 1, isPlaying = false))
        val loads: Int = rig.backend.loadedUrls.size

        send(rig, playingHere(seq = 2, isPlaying = true))

        assertEquals(loads, rig.backend.loadedUrls.size, "a resume reloaded the track")
        assertEquals(1, rig.backend.playCount)
    }

    @Test
    fun aStateThatAlreadyMatchesIsNotReAppliedToTheEngine() = runTest {
        // A hub broadcasts several times a second. Playing a player that is
        // already playing emits a start a chrome renders, so an applier that did
        // not compare would flicker once per frame.
        val rig: Rig = rig()
        send(rig, playingHere(seq = 1))
        val plays: Int = rig.backend.playCount

        send(rig, playingHere(seq = 2, progressMs = 12_000))

        assertEquals(plays, rig.backend.playCount)
    }
}
