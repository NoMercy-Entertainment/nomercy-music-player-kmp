// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.music.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// What is coming.
//
// By position rather than by identifier, and that is the one place in these
// libraries where position is right: a queue is an ordered list a listener
// points at, and the same recording can legitimately be in it twice. A track
// menu is the opposite — no duplicates, and it reorders underneath.
@Composable
public fun QueueList(
    state: MusicChromeState,
    commands: MusicCommands,
    modifier: Modifier = Modifier,
) {
    LazyColumn(modifier = modifier.fillMaxWidth().testTag(QUEUE_TAG)) {
        itemsIndexed(state.queue) { index, track ->
            QueueRow(
                track = track,
                index = index,
                isCurrent = index == state.queueIndex,
                onSelect = { commands.playQueueIndex(index) },
            )
        }
    }
}

@Composable
private fun QueueRow(track: MusicTrack, index: Int, isCurrent: Boolean, onSelect: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onSelect)
            .padding(ROW_PADDING)
            .testTag(queueRowTag(index)),
    ) {
        BasicText(
            text = track.title,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            // Weight rather than colour, so the row somebody is on is readable
            // to anyone who cannot tell the two colours apart.
            style = TextStyle(
                color = Color.White,
                fontSize = TITLE_SIZE,
                fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
            ),
        )

        track.artist?.takeIf { it.isNotBlank() }?.let { artist ->
            BasicText(
                text = artist,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = TextStyle(color = Color.White, fontSize = ARTIST_SIZE),
            )
        }
    }
}

// Indexed, because a test pointing at "the third row" is pointing at a position
// and a tag built from a title would collide the moment a queue repeats one.
internal fun queueRowTag(index: Int): String = "$QUEUE_ROW_PREFIX$index"

internal const val QUEUE_TAG = "nm-music-queue"
internal const val QUEUE_ROW_PREFIX = "nm-queue-row-"

private val ROW_PADDING = 12.dp
private val TITLE_SIZE = 15.sp
private val ARTIST_SIZE = 13.sp
