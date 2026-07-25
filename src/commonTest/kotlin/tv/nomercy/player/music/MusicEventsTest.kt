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
import tv.nomercy.player.core.events.EventEmitter
import tv.nomercy.player.core.events.PreventReason
import tv.nomercy.player.core.media.PlaylistItem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

private data class Track(
    override val id: String,
    override val url: String = "https://example.test/$id",
    override val title: String? = null,
) : PlaylistItem

class MusicEventsTest {

    @Test
    fun everyKeyNameIsTheWebMusicEventMapKeyVerbatim() {
        assertEquals(
            listOf("backend:changed", "beforeCrossfade", "crossfadeStart", "crossfadeComplete", "crossfadePrevented"),
            MusicEvents.all.map { it.name },
        )
    }

    @Test
    fun theOnlyNameSharedWithCoreIsTheOneTheContractSharesToo() {
        val core = CoreEvents.all.map { it.name }.toSet()
        val music = MusicEvents.all.map { it.name }.toSet()

        // backend:changed exists in both maps because both libraries swap
        // engines; the payloads differ, which is why each declares its own key.
        assertEquals(setOf("backend:changed"), core intersect music)
    }

    @Test
    fun aCrossfadeCarriesBothTracksAndTheOverlap() {
        val bus = EventEmitter<Unit>()
        var seen: Crossfade? = null
        bus.on(MusicEvents.CrossfadeStart) { seen = it }

        bus.emit(MusicEvents.CrossfadeStart, Crossfade(from = Track("a"), to = Track("b"), duration = 3.0))

        assertEquals("a", seen?.from?.id)
        assertEquals("b", seen?.to?.id)
        assertEquals(3.0, seen?.duration)
    }

    @Test
    fun theFirstTrackOfASessionFadesInFromNothing() {
        val opening = Crossfade(from = null, to = Track("a"), duration = 3.0)

        // A visualiser reading `from` has to handle this: there is nothing to
        // fade out of.
        assertNull(opening.from)
        assertEquals("a", opening.to.id)
    }

    @Test
    fun aCrossfadeIsRefusableLikeEveryOtherPlayerAction() = runTest {
        val bus = EventEmitter<Unit>()
        bus.on(MusicEvents.BeforeCrossfade) { it.preventDefault() }

        val outcome = bus.dispatchBefore(
            MusicEvents.BeforeCrossfade,
            Crossfade(from = Track("a"), to = Track("b"), duration = 3.0),
        )

        assertTrue(outcome.prevented)
        assertEquals(PreventReason.ListenerPrevented, outcome.reason)
    }

    @Test
    fun aListenerCanShortenACrossfadeRatherThanOnlyRefusingIt() = runTest {
        val bus = EventEmitter<Unit>()
        bus.on(MusicEvents.BeforeCrossfade) { it.data = it.data.copy(duration = 1.0) }

        val outcome = bus.dispatchBefore(
            MusicEvents.BeforeCrossfade,
            Crossfade(from = Track("a"), to = Track("b"), duration = 8.0),
        )

        assertTrue(!outcome.prevented)
        assertEquals(1.0, outcome.data.duration)
    }

    @Test
    fun noNameIsRegisteredTwice() {
        val names = MusicEvents.all.map { it.name }

        assertEquals(names.size, names.toSet().size)
    }
}
