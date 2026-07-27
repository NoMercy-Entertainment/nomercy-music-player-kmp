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
import tv.nomercy.player.music.Crossfade
import tv.nomercy.player.music.MusicEvents
import tv.nomercy.player.music.NMMusicPlayer
import tv.nomercy.player.music.Track
import tv.nomercy.player.music.TwoTrackBackend
import kotlin.test.Test
import kotlin.test.assertEquals

// The three things the rich Android client did that the thin ones did not.
//
// They are here because the collapse to one plugin has to level every client UP.
// Each of them is a real session going wrong in a way that reads as a bug in
// something else: a device that forgets its own volume, a handoff that pauses
// itself, a crossfade that restarts the song it just faded into.
class MusicConnectRichnessTest {

    private class ClockedPlugin(
        player: NMMusicPlayer,
        channel: MusicConnectChannel,
        scope: CoroutineScope,
        private val clock: () -> Long,
    ) : MusicConnectPlugin(player, channel, scope) {
        override fun nowMs(): Long = clock()
        override fun serverNowMs(): Long = clock()
    }

    private class Rig(
        val player: NMMusicPlayer,
        val backend: TwoTrackBackend,
        val channel: FakeMusicConnectChannel,
        val plugin: MusicConnectPlugin,
    ) {
        var nowMs: Long = 10_000
    }

    private fun TestScope.eager(): CoroutineScope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))

    private suspend fun TestScope.rig(): Rig {
        val backend = TwoTrackBackend()
        val channel = FakeMusicConnectChannel(deviceId = "dev-a")
        val player = NMMusicPlayer(backend, backend)
        lateinit var rig: Rig
        val plugin = ClockedPlugin(player, channel, eager()) { rig.nowMs }
        rig = Rig(player, backend, channel, plugin)

        player.setup()
        player.addPlugin(plugin)
        testScheduler.runCurrent()
        return rig
    }

    private suspend fun TestScope.send(rig: Rig, frame: MusicPlayerState) {
        rig.channel.broadcast(frame)
        testScheduler.runCurrent()
    }

    private fun here(
        seq: Long,
        id: String = "a",
        isPlaying: Boolean = true,
        serverTimeMs: Long? = null,
    ) = MusicPlayerState(
        deviceId = "dev-a",
        seq = seq,
        item = Track(id),
        isPlaying = isPlaying,
        durationMs = 200_000,
        serverTimeMs = serverTimeMs,
    )

    @Test
    fun aDeviceAppliesItsOwnRememberedLevelRatherThanTheSessionsFigure() = runTest {
        // A phone at thirty and a television at eighty are both correct. The
        // session's single figure belongs to whichever device is playing, and
        // adopting it would make one of them wrong every time focus moved.
        val rig: Rig = rig()

        send(
            rig,
            MusicPlayerState(
                deviceId = "dev-b",
                seq = 1,
                item = Track("a"),
                volumePercentage = 20,
                deviceVolumes = mapOf("dev-a" to 73),
            ),
        )

        assertEquals(73, rig.player.volume())
    }

    @Test
    fun aPassiveDeviceStillAppliesItsOwnLevel() = runTest {
        // Volume is the one thing that is never about who is playing. A device
        // watching another room still has its own hardware in someone's hand.
        val rig: Rig = rig()

        send(
            rig,
            MusicPlayerState(
                deviceId = "dev-b",
                seq = 1,
                item = Track("a"),
                deviceVolumes = mapOf("dev-a" to 41),
            ),
        )

        assertEquals(41, rig.player.volume())
    }

    @Test
    fun aLevelForSomeoneElseIsNotTakenAsOurs() = runTest {
        val rig: Rig = rig()
        val before: Int = rig.player.volume()

        send(
            rig,
            MusicPlayerState(
                deviceId = "dev-b",
                seq = 1,
                item = Track("a"),
                deviceVolumes = mapOf("dev-b" to 9),
            ),
        )

        assertEquals(before, rig.player.volume(), "another device's level was adopted")
    }

    @Test
    fun aReleaseStillInFlightDoesNotPauseTheDeviceThatJustTookOver() = runTest {
        // Taking over is two messages and they cross: this device says it has
        // the session, the old one says it has let go. The release lands second
        // and pauses the track the viewer just moved, about half a second after
        // they moved it.
        val rig: Rig = rig()
        send(rig, here(seq = 1, isPlaying = true))
        val paused: Int = rig.backend.pauseCount

        send(rig, here(seq = 2, isPlaying = false))

        assertEquals(paused, rig.backend.pauseCount, "a stale release paused a promoted device")
    }

    @Test
    fun aGenuinePauseAfterTheWindowStillApplies() = runTest {
        // The other half, and the reason the window is short. Suppressing pauses
        // for longer than a handoff takes is a pause button that does nothing.
        val rig: Rig = rig()
        send(rig, here(seq = 1, isPlaying = true))
        val paused: Int = rig.backend.pauseCount

        rig.nowMs += SETTLEMENT_MS
        send(rig, here(seq = 2, isPlaying = false))

        assertEquals(paused + 1, rig.backend.pauseCount, "the pause button stopped working")
    }

    @Test
    fun theWindowDoesNotSuppressPlaying() = runTest {
        // Only the pause direction is held. A stale frame still saying playing
        // catches up harmlessly, and refusing it would leave a promoted device
        // silent while every other one shows it playing.
        val rig: Rig = rig()
        send(rig, here(seq = 1, isPlaying = false))

        send(rig, here(seq = 2, isPlaying = true))

        assertEquals(1, rig.backend.playCount)
    }

    @Test
    fun theServerCatchingUpToACrossfadeDoesNotRestartTheTrack() = runTest {
        // The audio is already on the new song and a few seconds into it.
        // Reloading because the frame names a different track than the cursor is
        // the song starting again under the viewer.
        val rig: Rig = rig()
        send(rig, here(seq = 1, id = "a"))
        rig.player.emitCrossfadeStart(Track("b"))
        testScheduler.runCurrent()
        val loads: Int = rig.backend.loadedUrls.size

        send(rig, here(seq = 2, id = "b"))

        assertEquals("b", rig.player.item()?.id, "the cursor did not follow the crossfade")
        assertEquals(loads, rig.backend.loadedUrls.size, "the track the crossfade already swapped in was reloaded")
    }

    @Test
    fun aFrameStillNamingTheTrackACrossfadeMovedPastIsDropped() = runTest {
        // The server has not caught up yet. Applying it fades back to the song
        // that just ended, and the frame after it moves forward again.
        val rig: Rig = rig()
        send(rig, here(seq = 1, id = "a"))
        send(rig, here(seq = 2, id = "b"))
        rig.player.emitCrossfadeStart(Track("c"))
        testScheduler.runCurrent()

        send(rig, here(seq = 3, id = "a", serverTimeMs = rig.nowMs - 1_000))

        assertEquals("b", rig.player.item()?.id, "a stale frame reverted the track")
        assertEquals(listOf("b"), rig.player.queue().map { it.id }, "a dropped frame still rewrote the queue")
    }

    @Test
    fun aTrackChosenAfterTheMoveIsObeyedEvenIfItIsThePreviousOne() = runTest {
        // The other half of the same test, and the reason it is a question about
        // time rather than about which track. Someone on another device picking
        // the song that just finished is a deliberate choice, not the hub
        // lagging, and refusing it would make that track unplayable for a while.
        val rig: Rig = rig()
        send(rig, here(seq = 1, id = "a"))
        send(rig, here(seq = 2, id = "b"))
        rig.player.emitCrossfadeStart(Track("c"))
        testScheduler.runCurrent()

        send(rig, here(seq = 3, id = "a", serverTimeMs = rig.nowMs + 1_000))

        assertEquals("a", rig.player.item()?.id, "a deliberate choice was dropped as stale")
    }

    @Test
    fun aServerThatDoesNotStampItsFramesStillGetsItsTrackChanges() = runTest {
        // An unstamped frame cannot be placed either side of the move. Holding a
        // button through one costs a flicker; dropping a track change costs the
        // wrong song, so this side gives an older server the benefit of the
        // doubt and the button does not.
        val rig: Rig = rig()
        send(rig, here(seq = 1, id = "a"))
        send(rig, here(seq = 2, id = "b"))
        rig.player.emitCrossfadeStart(Track("c"))
        testScheduler.runCurrent()

        send(rig, here(seq = 3, id = "a", serverTimeMs = null))

        assertEquals("a", rig.player.item()?.id)
    }

    @Test
    fun anOrdinaryTrackChangeStillLoads() = runTest {
        // The guard is narrow on purpose. With no crossfade outstanding, a track
        // change is a track change and the engine has to be given the new one.
        val rig: Rig = rig()
        send(rig, here(seq = 1, id = "a"))

        send(rig, here(seq = 2, id = "b"))

        assertEquals("b", rig.player.item()?.id)
    }
}

// The plugin watches the player for this rather than being told, so a test that
// set the field directly would be testing a seam nothing uses.
private fun NMMusicPlayer.emitCrossfadeStart(to: Track) {
    emit(MusicEvents.CrossfadeStart, Crossfade(from = item(), to = to, duration = 5.0))
}
