// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.music.session

import tv.nomercy.player.core.media.PlaylistItem
import tv.nomercy.player.core.plugin.MediaSessionPlugin
import tv.nomercy.player.core.plugin.TransportCommands
import tv.nomercy.player.core.ports.NowPlaying
import tv.nomercy.player.core.ports.SystemTransport
import tv.nomercy.player.core.ports.defaultSystemTransport
import tv.nomercy.player.music.item.MusicPlaylistItem

// What a track looks like on a lock screen.
//
// The three lines and the cover, which is the layout every one of these
// platforms was designed around — and the one case where the core plugin's
// title-only default is most obviously wrong, because a music notification with
// no artist and no art is a grey box with a filename in it.
//
// No translation here, unlike the video plugin's season label: every value is
// the host's own text and none of it is a sentence this library writes.
public open class MusicMediaSessionPlugin(
    commands: TransportCommands,
    openTransport: () -> SystemTransport = ::defaultSystemTransport,
) : MediaSessionPlugin(commands, openTransport) {

    override fun nowPlayingFor(item: PlaylistItem): NowPlaying {
        val track: MusicPlaylistItem = item as? MusicPlaylistItem ?: return super.nowPlayingFor(item)

        return super.nowPlayingFor(item).copy(
            title = track.name,
            artist = track.artist,
            album = track.album,
            artworkUrl = track.image ?: track.cover,
        )
    }
}
