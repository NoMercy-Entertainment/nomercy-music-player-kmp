// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.music.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Dragging along the track, in the full player where there is room for it.
//
// The mini-player's line is drawn and this one is dragged, which is the whole
// difference between them: a two-pixel strip beside a pause button is a target
// nobody meant to hit, and a mini-player that scrubbed by accident loses a
// listener's place every time they reach for pause.
//
// The track does not move until the drag ends. What moves while somebody is
// hunting is the figure, because seeking on every pixel is a seek storm the
// engine answers by stalling.
@Composable
public fun MusicScrubber(
    state: MusicChromeState,
    commands: MusicCommands,
    modifier: Modifier = Modifier,
) {
    var dragSeconds: Double? by remember { mutableStateOf(null) }
    var width: Float by remember { mutableStateOf(1f) }

    val shownSeconds: Double = dragSeconds ?: state.timeSeconds
    val shownFraction: Float = fractionOf(shownSeconds, state.durationSeconds)

    Column(modifier = modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(TOUCH_HEIGHT)
                .testTag(SCRUBBER_TAG)
                // The whole strip is the target rather than the few pixels the
                // bar is drawn in. The drawn height is a design decision; the
                // reachable height is not.
                .pointerInput(state.durationSeconds) {
                    width = size.width.toFloat().coerceAtLeast(1f)

                    val moveTo: (Float) -> Unit = { x ->
                        dragSeconds = secondsAt(x, width, state.durationSeconds)
                    }

                    detectDragGestures(
                        onDragStart = { offset -> moveTo(offset.x) },
                        onDrag = { change, _ -> moveTo(change.position.x) },
                        // Only a completed drag moves the track. A cancel leaves
                        // it where it was, and the figure goes back either way
                        // or the bar keeps showing a place nobody went.
                        onDragEnd = {
                            dragSeconds?.let { commands.seekTo(it) }
                            dragSeconds = null
                        },
                        onDragCancel = { dragSeconds = null },
                    )
                }
                .semantics { contentDescription = formatTime(shownSeconds) },
            contentAlignment = Alignment.CenterStart,
        ) {
            ScrubberLine(shownFraction)
        }

        Times(shownSeconds, state.durationSeconds)
    }
}

@Composable
private fun ScrubberLine(fraction: Float) {
    Box(modifier = Modifier.fillMaxWidth().height(LINE_HEIGHT).background(TRACK_COLOUR))
    Box(
        modifier = Modifier
            .fillMaxWidth(fraction)
            .height(LINE_HEIGHT)
            .background(Color.White)
            .testTag(SCRUBBER_FILL_TAG),
    )
}

// Elapsed on the left and the whole length on the right, which is the pair every
// player draws. Remaining is the video chrome's answer because somebody deciding
// whether to start another episode is asking how much is left; a listener
// looking at a song is asking how long it is.
@Composable
private fun Times(shownSeconds: Double, durationSeconds: Double) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        BasicText(
            text = formatTime(shownSeconds),
            style = TextStyle(color = Color.White, fontSize = TIME_SIZE),
            modifier = Modifier.testTag(ELAPSED_TAG),
        )
        BasicText(
            text = formatTime(durationSeconds),
            style = TextStyle(color = Color.White, fontSize = TIME_SIZE),
        )
    }
}

// Where along the track a horizontal position falls.
//
// Clamped at both ends, because a drag that leaves the strip reports a position
// outside it and an unclamped answer is a seek past the end or before the start.
internal fun secondsAt(x: Float, width: Float, durationSeconds: Double): Double {
    if (durationSeconds <= 0.0 || width <= 0f) return 0.0

    return (x / width).toDouble().coerceIn(0.0, 1.0) * durationSeconds
}

private fun fractionOf(seconds: Double, duration: Double): Float =
    if (duration <= 0.0) 0f else (seconds / duration).coerceIn(0.0, 1.0).toFloat()

internal const val SCRUBBER_TAG = "nm-music-scrubber"
internal const val SCRUBBER_FILL_TAG = "nm-music-scrubber-fill"
internal const val ELAPSED_TAG = "nm-music-elapsed"

// Taller than the line it draws. Fingers are not pixels.
private val TOUCH_HEIGHT = 32.dp
private val LINE_HEIGHT = 3.dp
private val TIME_SIZE = 12.sp
private val TRACK_COLOUR = Color(red = 1f, green = 1f, blue = 1f, alpha = 0.25f)
