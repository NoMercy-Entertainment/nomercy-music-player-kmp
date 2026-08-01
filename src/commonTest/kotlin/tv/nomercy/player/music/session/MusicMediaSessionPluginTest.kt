// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.music.session

import kotlinx.coroutines.test.runTest
import tv.nomercy.player.core.controllers.ComposedPlayer
import tv.nomercy.player.core.events.CoreEvents
import tv.nomercy.player.core.events.ItemChange
import tv.nomercy.player.core.media.PlaylistItem
import tv.nomercy.player.core.player.PlayerConfig
import tv.nomercy.player.core.plugin.TransportCommands
import tv.nomercy.player.core.ports.NowPlaying
import tv.nomercy.player.core.ports.SystemTransport
import tv.nomercy.player.core.ports.TransportActions
import tv.nomercy.player.core.ports.TransportPlaybackState
import tv.nomercy.player.music.item.MusicPlaylistItem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

private data class Track(
    override val id: String = "t1",
    override val url: String = "https://media.example.test/wish-you-were-here.flac",
    override val title: String? = null,
    override val name: String = "Wish You Were Here",
    override val artist: String? = null,
    override val album: String? = null,
    override val image: String? = null,
    override val cover: String? = null,
) : MusicPlaylistItem

private class CapturingTransport : SystemTransport {
    var lastNowPlaying: NowPlaying? = null
        private set

    override fun setNowPlaying(nowPlaying: NowPlaying) {
        lastNowPlaying = nowPlaying
    }

    override fun setPlaybackState(state: TransportPlaybackState, positionMs: Long, playbackRate: Double) = Unit
    override fun setActionHandlers(actions: TransportActions) = Unit
    override fun clear() = Unit
    override fun release() = Unit
}

private class SilentCommands : TransportCommands {
    override fun play() = Unit
    override fun pause() = Unit
    override fun stop() = Unit
    override fun seekTo(positionMs: Long) = Unit
}

// The four things a music notification draws.
//
// Without the mapping this proves, a track reaches the lock screen as a file
// name in a grey box: NowPlaying carries an artist, an album and a cover, and
// nothing in this library was filling any of them.
class MusicMediaSessionPluginTest {

    private suspend fun announce(item: PlaylistItem): NowPlaying? {
        val transport = CapturingTransport()
        val player = ComposedPlayer(backend = null)
        player.setup(PlayerConfig())
        player.addPlugin(MusicMediaSessionPlugin(SilentCommands()) { transport })

        player.emit(CoreEvents.Item, ItemChange(item = item, index = 0))
        return transport.lastNowPlaying
    }

    @Test
    fun aTrackReachesTheLockScreenAsNameArtistAlbumAndCover() = runTest {
        val playing: NowPlaying? = announce(
            Track(
                artist = "Pink Floyd",
                album = "Wish You Were Here",
                image = "https://images.example.test/wywh.jpg",
            ),
        )

        assertEquals("Wish You Were Here", playing?.title)
        assertEquals("Pink Floyd", playing?.artist)
        assertEquals("Wish You Were Here", playing?.album)
        assertEquals("https://images.example.test/wywh.jpg", playing?.artworkUrl)
    }

    @Test
    fun theTitleIsTheTrackNameRatherThanTheGenericItemTitle() = runTest {
        // Core reads title; music items carry both, and the name is the one a
        // listener recognises. A host that fills only title still has core's
        // fallback, and one that fills both must not get the wrong of the two.
        assertEquals("Wish You Were Here", announce(Track(title = "track-04"))?.title)
    }

    @Test
    fun theOlderCoverFieldIsReadWhenTheCanonicalImageIsAbsent() = runTest {
        assertEquals("cover.jpg", announce(Track(cover = "cover.jpg"))?.artworkUrl)
        assertEquals("image.jpg", announce(Track(image = "image.jpg", cover = "cover.jpg"))?.artworkUrl)
    }

    @Test
    fun aTrackWithNoArtistOrAlbumLeavesThoseLinesOutRatherThanBlank() = runTest {
        val playing: NowPlaying? = announce(Track())

        assertNull(playing?.artist)
        assertNull(playing?.album)
        assertNull(playing?.artworkUrl)
    }
}
