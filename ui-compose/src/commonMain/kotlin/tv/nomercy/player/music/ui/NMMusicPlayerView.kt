// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.music.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import kotlinx.coroutines.CoroutineScope
import tv.nomercy.player.core.media.PlaylistItem
import tv.nomercy.player.music.NMMusicPlayer

// The drop-in music player.
//
// Collapsed by default, because that is what a music player is most of the time:
// a row at the bottom of an application that is doing something else. A listener
// opens the full player to choose something and closes it again; the row is
// where they spend the rest of the session.
@Composable
public fun NMMusicPlayerView(
    player: NMMusicPlayer,
    modifier: Modifier = Modifier,
    strings: MusicStrings = MusicStrings(),
    trackOf: (PlaylistItem?) -> MusicTrack? = ::musicTrackOf,
    artwork: @Composable (MusicChromeState) -> Unit = {},
) {
    val scope: CoroutineScope = rememberCoroutineScope()
    val state: MusicChromeState = rememberMusicChromeState(player, trackOf)
    val commands: MusicCommands = remember(player, scope) { musicCommandsOf(player, scope) }

    MiniPlayer(state, commands, modifier, strings, artwork)
}
