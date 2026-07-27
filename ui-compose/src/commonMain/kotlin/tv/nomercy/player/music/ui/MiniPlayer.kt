// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.music.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// The row that sits at the bottom of an application while something plays.
//
// Artwork, two lines, three buttons and a thin line of progress, which is the
// shape every music client has settled on because it is the least that answers
// "what is playing and can I stop it" at a glance.
//
// The progress line is drawn rather than draggable. A four-pixel strip is not a
// target anybody hits, and a mini-player that scrubbed by accident is one that
// loses a listener's place every time they reach for the pause button.
@Composable
public fun MiniPlayer(
    state: MusicChromeState,
    commands: MusicCommands,
    modifier: Modifier = Modifier,
    strings: MusicStrings = MusicStrings(),
    onExpand: () -> Unit = {},
    artwork: @Composable (MusicChromeState) -> Unit = {},
) {
    Column(modifier = modifier.fillMaxWidth().background(Color.Black).testTag(MINI_PLAYER_TAG)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(ROW_PADDING),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(GAP),
        ) {
            // The artwork and the two lines open the full player; the buttons
            // beside them do not. Making the whole row one tap target was the
            // first attempt and it announced the entire row as a single control
            // called "Open player" — the pause button inside it stopped being
            // something a screen reader could reach at all. The test that found
            // it was looking for the progress line, which had vanished into the
            // same merge.
            Row(
                modifier = Modifier
                    .weight(1f)
                    .clickable(onClick = onExpand)
                    .semantics { contentDescription = strings.expand },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(GAP),
            ) {
                // A slot rather than an image loader. The library has none and
                // should not: artwork is a URL an application already knows how
                // to fetch, and shipping a fetcher would be shipping a second
                // one beside the one it has.
                Box(modifier = Modifier.size(ARTWORK_SIZE).testTag(ARTWORK_TAG)) { artwork(state) }

                TrackLines(state, strings, Modifier.weight(1f))
            }

            Transport(state, commands, strings)
        }

        ProgressLine(state.progress)
    }
}

@Composable
private fun TrackLines(state: MusicChromeState, strings: MusicStrings, modifier: Modifier) {
    Column(modifier = modifier) {
        BasicText(
            text = state.track?.title?.takeIf { it.isNotBlank() } ?: strings.nothingPlaying,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = TextStyle(color = Color.White, fontSize = TITLE_SIZE),
        )

        // Absent rather than blank. An empty second line is a gap a listener
        // reads as something that failed to load.
        state.track?.artist?.takeIf { it.isNotBlank() }?.let { artist ->
            BasicText(
                text = artist,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = TextStyle(color = Color.White, fontSize = ARTIST_SIZE),
                modifier = Modifier.testTag(ARTIST_TAG),
            )
        }
    }
}

// Gated on there being somewhere to go, both ways. A control a listener presses
// to find out it does nothing is worse than one that is absent.
@Composable
private fun Transport(state: MusicChromeState, commands: MusicCommands, strings: MusicStrings) {
    if (state.hasPrevious) {
        MusicIconButton(
            icon = MusicIcons.Previous,
            description = strings.previous,
            onClick = commands::previous,
            modifier = Modifier.testTag(PREVIOUS_TAG),
        )
    }

    // The glyph and the label are one decision rather than two conditions that
    // happen to read the same state. Written apart, an edit to one is an edit to
    // half of it — a pause glyph announcing itself as Play, which is invisible
    // to anyone looking at the screen and wrong for everyone who is not.
    val control: TransportControl = if (state.playing) {
        TransportControl(MusicIcons.Pause, strings.pause)
    } else {
        TransportControl(MusicIcons.Play, strings.play)
    }

    MusicIconButton(
        icon = control.icon,
        description = control.description,
        onClick = { commands.setPlaying(!state.playing) },
        modifier = Modifier.testTag(PLAY_PAUSE_TAG),
    )

    if (state.hasNext) {
        MusicIconButton(
            icon = MusicIcons.Next,
            description = strings.next,
            onClick = commands::next,
            modifier = Modifier.testTag(NEXT_TAG),
        )
    }
}

@Composable
private fun ProgressLine(progress: Float) {
    Box(modifier = Modifier.fillMaxWidth().height(LINE_HEIGHT).background(TRACK_COLOUR)) {
        Box(
            modifier = Modifier
                .fillMaxWidth(progress)
                .height(LINE_HEIGHT)
                .background(Color.White)
                .testTag(PROGRESS_TAG),
        )
    }
}

// A glyph and what it announces itself as, which are the same choice.
private data class TransportControl(val icon: ImageVector, val description: String)

// Supplied rather than spelled here, because these are the strings most likely
// to differ per language and a library that hard-coded them would be one nobody
// can ship outside English.
public data class MusicStrings(
    val play: String = "Play",
    val pause: String = "Pause",
    val next: String = "Next",
    val previous: String = "Previous",
    val nothingPlaying: String = "Nothing playing",
    val collapse: String = "Close player",
    val expand: String = "Open player",
    // What the button will DO, which is what a label is for. A control that
    // announced its own state would tell a screen reader "repeat off" on a
    // button that turns repeating on.
    val shuffleOn: String = "Shuffle",
    val shuffleOff: String = "Stop shuffling",
    val repeatAll: String = "Repeat queue",
    val repeatOne: String = "Repeat track",
    val repeatOff: String = "Stop repeating",
)

internal const val MINI_PLAYER_TAG = "nm-mini-player"
internal const val PLAY_PAUSE_TAG = "nm-mini-play-pause"
internal const val NEXT_TAG = "nm-mini-next"
internal const val PREVIOUS_TAG = "nm-mini-previous"
internal const val PROGRESS_TAG = "nm-mini-progress"
internal const val ARTWORK_TAG = "nm-mini-artwork"
internal const val ARTIST_TAG = "nm-mini-artist"

private val ROW_PADDING = 8.dp
private val GAP = 12.dp
private val ARTWORK_SIZE = 48.dp
private val LINE_HEIGHT = 2.dp
private val TITLE_SIZE = 15.sp
private val ARTIST_SIZE = 13.sp
private val TRACK_COLOUR = Color(red = 1f, green = 1f, blue = 1f, alpha = 0.25f)
