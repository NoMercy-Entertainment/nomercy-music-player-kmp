// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.music.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import tv.nomercy.player.core.player.RepeatState

// The player a listener opens to choose something.
//
// The same state and the same commands the row uses, laid out for a screen
// rather than for a strip. Nothing here reaches the player directly, which is
// what lets the two views disagree about layout and never about what is playing.
@Composable
public fun FullPlayer(
    state: MusicChromeState,
    commands: MusicCommands,
    modifier: Modifier = Modifier,
    strings: MusicStrings = MusicStrings(),
    onCollapse: () -> Unit = {},
    artwork: @Composable (MusicChromeState) -> Unit = {},
) {
    Column(
        modifier = modifier.fillMaxSize().background(Color.Black).padding(SCREEN_PADDING).testTag(FULL_PLAYER_TAG),
    ) {
        MusicIconButton(
            icon = MusicIcons.Collapse,
            description = strings.collapse,
            onClick = onCollapse,
            modifier = Modifier.testTag(COLLAPSE_TAG),
        )

        Box(modifier = Modifier.fillMaxWidth().weight(1f).testTag(FULL_ARTWORK_TAG)) { artwork(state) }

        TrackHeading(state, strings)

        MusicScrubber(state, commands, Modifier.padding(vertical = BLOCK_GAP))

        FullTransport(state, commands, strings)

        // Underneath rather than behind a separate screen. What is coming is the
        // other half of what a listener opened this for, and a queue one more
        // tap away is one they stop checking.
        QueueList(state, commands, Modifier.height(QUEUE_HEIGHT))
    }
}

@Composable
private fun TrackHeading(state: MusicChromeState, strings: MusicStrings) {
    Column(modifier = Modifier.fillMaxWidth()) {
        BasicText(
            text = state.track?.title?.takeIf { it.isNotBlank() } ?: strings.nothingPlaying,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            style = TextStyle(color = Color.White, fontSize = TITLE_SIZE, fontWeight = FontWeight.Bold),
        )

        state.track?.artist?.takeIf { it.isNotBlank() }?.let { artist ->
            BasicText(
                text = artist,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = TextStyle(color = Color.White, fontSize = ARTIST_SIZE),
                modifier = Modifier.testTag(FULL_ARTIST_TAG),
            )
        }
    }
}

// Shuffle and repeat live here and not on the row, because the row is for
// answering "what is playing" at a glance and these are decisions somebody makes
// deliberately. Both are drawn from state rather than remembered, so a change
// made on another device is what the button shows.
@Composable
private fun FullTransport(state: MusicChromeState, commands: MusicCommands, strings: MusicStrings) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MusicIconButton(
            icon = MusicIcons.Shuffle,
            description = if (state.shuffled) strings.shuffleOff else strings.shuffleOn,
            onClick = { commands.setShuffled(!state.shuffled) },
            modifier = Modifier.testTag(SHUFFLE_TAG),
        )

        MusicIconButton(
            icon = FluentIcons.Previous,
            description = strings.previous,
            onClick = commands::previous,
            modifier = Modifier.testTag(FULL_PREVIOUS_TAG),
        )

        PlayPause(state, commands, strings)

        MusicIconButton(
            icon = FluentIcons.Next,
            description = strings.next,
            onClick = commands::next,
            modifier = Modifier.testTag(FULL_NEXT_TAG),
        )

        MusicIconButton(
            icon = MusicIcons.Repeat,
            description = repeatLabel(state.repeat, strings),
            onClick = { commands.setRepeat(nextRepeat(state.repeat)) },
            modifier = Modifier.testTag(REPEAT_TAG),
        )
    }
}

// The glyph and the label are one decision rather than two conditions that
// happen to read the same state, which is how a pause triangle came to announce
// itself as Play in the row this shares its shape with.
@Composable
private fun PlayPause(state: MusicChromeState, commands: MusicCommands, strings: MusicStrings) {
    val control: FullControl = if (state.playing) {
        FullControl(FluentIcons.Pause, strings.pause)
    } else {
        FullControl(FluentIcons.Play, strings.play)
    }

    MusicIconButton(
        icon = control.icon,
        description = control.description,
        onClick = { commands.setPlaying(!state.playing) },
        modifier = Modifier.testTag(FULL_PLAY_PAUSE_TAG),
    )
}

// Off, then the whole queue, then this one track. The order every client uses,
// and the reason it is not a boolean: "repeat" without saying what is being
// repeated is a button whose second press surprises somebody.
internal fun nextRepeat(current: RepeatState): RepeatState = when (current) {
    RepeatState.OFF -> RepeatState.ALL
    RepeatState.ALL -> RepeatState.ONE
    RepeatState.ONE -> RepeatState.OFF
}

// What the button will do, not what is currently set. A control announcing its
// own state gives a screen reader "repeat off" on a button that turns it on.
internal fun repeatLabel(current: RepeatState, strings: MusicStrings): String = when (current) {
    RepeatState.OFF -> strings.repeatAll
    RepeatState.ALL -> strings.repeatOne
    RepeatState.ONE -> strings.repeatOff
}

// A glyph and what it announces itself as, which are the same choice.
private data class FullControl(val icon: ImageVector, val description: String)

internal const val FULL_PLAYER_TAG = "nm-full-player"
internal const val COLLAPSE_TAG = "nm-full-collapse"
internal const val FULL_ARTWORK_TAG = "nm-full-artwork"
internal const val FULL_ARTIST_TAG = "nm-full-artist"
internal const val FULL_PLAY_PAUSE_TAG = "nm-full-play-pause"
internal const val FULL_NEXT_TAG = "nm-full-next"
internal const val FULL_PREVIOUS_TAG = "nm-full-previous"
internal const val SHUFFLE_TAG = "nm-full-shuffle"
internal const val REPEAT_TAG = "nm-full-repeat"

private val SCREEN_PADDING = 16.dp
private val BLOCK_GAP = 12.dp
private val QUEUE_HEIGHT = 180.dp
private val TITLE_SIZE = 22.sp
private val ARTIST_SIZE = 16.sp
