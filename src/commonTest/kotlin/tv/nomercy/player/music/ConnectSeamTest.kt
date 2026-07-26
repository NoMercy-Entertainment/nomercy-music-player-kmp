// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.music

import kotlinx.coroutines.test.runTest
import tv.nomercy.player.core.events.CoreEvents
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

// The seam a NoMercy Connect plugin will hang off, proven reachable.
//
// This library stays transport-agnostic on purpose. Connect's own machinery —
// the optimistic action shield, the server clock, the passive ticker, active-
// device arbitration — belongs to the plugin, not here. Building any of it into
// the library would rebuild the multi-device engine inside the player and
// recreate the trap where two clients each carry half a protocol and disagree.
//
// What the library owes a plugin is the ability to intervene, and that is what
// these tests measure: a listener can refuse any transport action before it
// reaches the engine, and hear that it was refused. Everything the web's
// musicConnectPlugin does to gate playback is built on exactly that.
class ConnectSeamTest {

    private suspend fun player(): Pair<NMMusicPlayer, TwoTrackBackend> {
        val backend = TwoTrackBackend()
        val subject = NMMusicPlayer(backend, backend)
        subject.setup()
        subject.ready().await()
        subject.queue(listOf(Track("a"), Track("b")))
        return subject to backend
    }

    @Test
    fun aPluginCanRefusePlayBeforeItReachesTheEngine() = runTest {
        // The shape the web plugin uses: another device holds the session, so
        // this one forwards the intent to the hub and does not play locally.
        val (subject, backend) = player()
        subject.on(CoreEvents.BeforePlay) { it.preventDefault() }
        var refusal = 0
        subject.on(CoreEvents.PlayPrevented) { refusal += 1 }

        subject.play()

        assertEquals(0, backend.playCount, "a refused play still started the engine")
        assertEquals(1, refusal, "nothing told the caller the play was refused")
    }

    @Test
    fun aPluginCanRefuseASeek() = runTest {
        val (subject, backend) = player()
        subject.play()
        subject.on(CoreEvents.BeforeSeek) { it.preventDefault() }

        subject.time(120.0)

        assertTrue(backend.seekedTo.isEmpty(), "a refused seek moved the engine")
    }

    @Test
    fun aPluginCanRewriteWhereASeekLandsRatherThanOnlyRefusingIt() = runTest {
        // The difference between a veto and a negotiation. A Connect plugin
        // reconciling two devices' clocks needs to move a seek, not just stop
        // it — the viewer asked to jump, and the answer is where, not whether.
        val (subject, backend) = player()
        subject.play()
        subject.on(CoreEvents.BeforeSeek) { it.data = it.data.copy(time = 90.0) }

        subject.time(120.0)

        assertEquals(listOf(90.0), backend.seekedTo)
    }

    @Test
    fun aPluginCanRefuseACrossfadeWithoutStoppingPlayback() = runTest {
        // A gapless album should not be crossfaded, and the thing that knows
        // that is the plugin holding the tags. Refusing must leave the player
        // able to advance the ordinary way.
        val (subject, backend) = player()
        subject.play()
        subject.on(MusicEvents.BeforeCrossfade) { it.preventDefault() }

        val crossfaded: Boolean = subject.crossfadeTo(Track("b"))

        assertTrue(!crossfaded)
        assertTrue(backend.calls.isEmpty(), "a refused crossfade still touched the buffers")
        subject.next()
        assertEquals("b", subject.item()?.id, "the player could not advance after a refused crossfade")
    }

    @Test
    fun theRealtimeSeamIsReachableAndNamesWhatIsMissing() = runTest {
        // A player built without a realtime transport is the ordinary case: the
        // library ships no socket. What matters is that asking for one fails
        // with a named error rather than a null dereference, so a plugin author
        // learns what to supply.
        val (subject, _) = player()

        val failure = runCatching { subject.websocket("wss://server.test/hub", REALTIME_DEFAULTS) }

        assertTrue(failure.isFailure)
        assertTrue(
            failure.exceptionOrNull()?.message?.contains("realtime", ignoreCase = true) == true,
            "the failure did not name the missing transport: ${failure.exceptionOrNull()?.message}",
        )
    }

    private companion object {
        val REALTIME_DEFAULTS = tv.nomercy.player.core.ports.RealtimeFactoryOptions()
    }
}
