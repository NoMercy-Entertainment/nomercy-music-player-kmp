// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.music.scrobble

import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import tv.nomercy.player.core.events.CoreEvents
import tv.nomercy.player.core.events.EventKey
import tv.nomercy.player.core.events.TimeUpdate
import tv.nomercy.player.core.ports.Clock
import tv.nomercy.player.music.NMMusicPlayer
import kotlin.test.Test
import kotlin.test.assertEquals
import tv.nomercy.player.testing.FakeMediaBackend
import tv.nomercy.player.testing.TestItem

private class FixedClock(private val millis: Long) : Clock {
    override fun now(): Long = millis
}

private class RecordingScrobbler : Scrobbler {
    val nowPlayingCalls: MutableList<String> = mutableListOf()
    val reports: MutableList<Pair<String, ScrobbleContext>> = mutableListOf()

    override suspend fun nowPlaying(trackId: String) {
        nowPlayingCalls += trackId
    }

    override suspend fun scrobble(trackId: String, context: ScrobbleContext) {
        reports += trackId to context
    }
}

// A scrobble without a start timestamp is not a scrobble Last.fm will take: the
// submission API requires `timestamp` and rejects a report without one. The
// plugin was dropping it, so every report it built was unsubmittable.
class ScrobbleContextTest {

    // 2026-08-01T00:00:00Z as milliseconds, so the seconds conversion is
    // visible rather than asserted against whatever the wall clock said.
    private val startMillis: Long = 1_785_888_000_000L

    // A plugin emits under its own namespace, so this is the name a consumer
    // subscribes to — the bare key is what the plugin passes in, not what goes
    // out on the bus.
    private val nowPlaying: EventKey<NowPlayingReport> = EventKey("plugin:scrobble:nowPlaying")

    private fun TestScope.rig(scrobbler: RecordingScrobbler): NMMusicPlayer {
        val player = NMMusicPlayer(FakeMediaBackend(), scope = backgroundScope, id = "scrobble-context")
        backgroundScope.launch {
            player.addPlugin(ScrobblePlugin(scrobbler, ScrobbleRules(), FixedClock(startMillis)))
            player.setup()
            // Queued after setup, so the item event lands with the plugin wired
            // — which is the ordinary case and the one the tracker needs.
            player.queue(listOf(TestItem("track-a")))
        }
        runCurrent()
        return player
    }

    private fun heardEnough(player: NMMusicPlayer) {
        // Half of a five-minute track, in ticks small enough for the tracker to
        // count as listening rather than as a seek.
        var position = 0.0
        while (position < 160.0) {
            position += 1.0
            player.emit(CoreEvents.Time, TimeUpdate(time = position, duration = 300.0, percentage = 0.0))
        }
    }

    @Test
    fun theReportCarriesTheMomentTheTrackStarted() = runTest {
        val scrobbler = RecordingScrobbler()
        val player = rig(scrobbler)

        heardEnough(player)
        runCurrent()

        assertEquals(startMillis / 1_000L, scrobbler.reports.single().second.startedAt)
    }

    @Test
    fun theReportCarriesTheTracksOwnLength() = runTest {
        val scrobbler = RecordingScrobbler()
        val player = rig(scrobbler)

        heardEnough(player)
        runCurrent()

        assertEquals(300.0, scrobbler.reports.single().second.durationSeconds)
    }

    @Test
    fun aTrackStartingIsAnnouncedAndNotOnlyReported() = runTest {
        // The backend was being told and nobody else was, so a chrome drawing a
        // "scrobbling to Last.fm" badge had nothing to bind to.
        val scrobbler = RecordingScrobbler()
        val seen: MutableList<String> = mutableListOf()
        val player = rig(scrobbler)
        player.on(nowPlaying) { seen += it.trackId }

        player.emit(CoreEvents.Item, tv.nomercy.player.core.events.ItemChange(TestItem("track-b"), 1))
        runCurrent()

        assertEquals(listOf("track-b"), seen)
    }

    @Test
    fun theBackendIsStillToldFirst() = runTest {
        val scrobbler = RecordingScrobbler()
        val player = rig(scrobbler)
        val order: MutableList<String> = mutableListOf()
        player.on(nowPlaying) { order += "event" }

        player.emit(CoreEvents.Item, tv.nomercy.player.core.events.ItemChange(TestItem("track-b"), 1))
        runCurrent()

        assertEquals(listOf("track-a", "track-b"), scrobbler.nowPlayingCalls)
        assertEquals(listOf("event"), order)
    }
}
