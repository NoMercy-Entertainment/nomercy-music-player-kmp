// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.music.connect

import tv.nomercy.player.core.media.PlaylistItem

// What a device that is not playing shows.
//
// Separate from the player's own state on purpose. A passive device has no audio
// and never loads anything, so its player has nothing to report — the track and
// the moving progress bar are the active device's, arriving over the hub and
// interpolated between frames.
//
// The active device renders from its player as usual and ignores this.
public data class ConnectMirror(
    val item: PlaylistItem? = null,
    val isPlaying: Boolean = false,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
) {

    // Whether the bar has anywhere left to go. A mirror that is paused, or that
    // has arrived at the end of the track, is finished — and the ticker reads
    // this rather than deciding for itself, so there is one answer to what makes
    // the bar move and both of them use it.
    internal val isAdvancing: Boolean
        get() = isPlaying && (durationMs <= 0 || positionMs < durationMs)

    // A duration of zero means the active device has not said how long the track
    // is, which is a partial frame from an older server rather than an error. The
    // bar keeps counting up in that case, because elapsed time is still true even
    // when the proportion is unknown.
    internal fun advancedBy(millis: Long): ConnectMirror = when {
        !isAdvancing -> this
        durationMs > 0 -> copy(positionMs = (positionMs + millis).coerceAtMost(durationMs))
        else -> copy(positionMs = positionMs + millis)
    }
}
