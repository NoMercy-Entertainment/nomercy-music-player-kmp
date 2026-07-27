// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.music.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import kotlinx.coroutines.CoroutineScope
import tv.nomercy.player.core.media.PlaylistItem
import tv.nomercy.player.music.NMMusicPlayer

// The drop-in music player.
//
// Collapsed by default, because that is what a music player is most of the time:
// a row at the bottom of an application that is doing something else. A listener
// opens it to choose something and closes it again; the row is where they spend
// the session.
//
// Both views are drawn from the same state and the same commands. They disagree
// about layout and cannot disagree about what is playing, which is the failure
// two separately-bound views produce: a full player showing the track before
// last because it read a different source.
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

    var expanded: Boolean by remember { mutableStateOf(false) }

    Box(modifier = modifier.fillMaxWidth()) {
        // One at a time rather than a crossfade between two live copies. Both
        // mounted at once is two queue lists reading the same state and two sets
        // of controls a test can find, and a press landing on the invisible one.
        AnimatedVisibility(visible = !expanded, enter = fadeIn(), exit = fadeOut()) {
            MiniPlayer(state, commands, strings = strings, onExpand = { expanded = true }, artwork = artwork)
        }

        AnimatedVisibility(visible = expanded, enter = fadeIn(), exit = fadeOut()) {
            FullPlayer(state, commands, strings = strings, onCollapse = { expanded = false }, artwork = artwork)
        }
    }
}
