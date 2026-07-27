// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.music.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import tv.nomercy.player.core.media.PlaylistItem
import tv.nomercy.player.core.player.BufferState
import tv.nomercy.player.core.player.PlayState
import tv.nomercy.player.core.player.PlayerState
import tv.nomercy.player.core.player.RepeatState
import tv.nomercy.player.core.player.ShuffleState
import tv.nomercy.player.music.NMMusicPlayer

// What is playing, as the mini-player reads it.
//
// A projection rather than the player's own state, for the same reason the video
// chrome has one: a row with a title and a play button should be drawable from a
// fixture, and a widget handed the whole player is a widget that cannot be
// tested without one.
public data class MusicChromeState(
    val playing: Boolean = false,
    val buffering: Boolean = false,

    val track: MusicTrack? = null,

    val timeSeconds: Double = 0.0,
    val durationSeconds: Double = 0.0,

    val volume: Int = 100,
    val muted: Boolean = false,

    val repeat: RepeatState = RepeatState.OFF,
    val shuffled: Boolean = false,

    val queueSize: Int = 0,
    val queueIndex: Int = 0,
    // What is coming, in order, so the list and the row are drawn from one
    // value. Read separately by the list, it would be a queue that changes
    // underneath the row somebody is tapping.
    val queue: List<MusicTrack> = emptyList(),
) {

    // Drawn by the thin line under the row, so it is computed once here. Zero
    // rather than a division by zero, which is every stream whose length is not
    // known yet.
    public val progress: Float
        get() = if (durationSeconds <= 0.0) 0f else (timeSeconds / durationSeconds).coerceIn(0.0, 1.0).toFloat()

    public val hasNext: Boolean get() = queueIndex < queueSize - 1

    public val hasPrevious: Boolean get() = queueIndex > 0
}

// The two lines of text and the artwork, as a row shows them.
//
// Its own type rather than the player's item, which carries an id, a title and a
// url and nothing else. An artist and an album are the host's — they come from
// whichever server it talks to — and this is where it puts them.
public data class MusicTrack(
    val title: String,
    val artist: String? = null,
    val album: String? = null,
    val artworkUrl: String? = null,
)

@Composable
public fun rememberMusicChromeState(
    player: NMMusicPlayer,
    trackOf: (PlaylistItem?) -> MusicTrack? = ::musicTrackOf,
): MusicChromeState {
    val snapshot: PlayerState by player.stateFlow.collectAsState()

    return musicChromeStateOf(snapshot, trackOf(snapshot.item), player.queue().mapNotNull(trackOf))
}

// Split from the composable so it can be driven from a fixture.
public fun musicChromeStateOf(
    snapshot: PlayerState,
    track: MusicTrack?,
    queue: List<MusicTrack> = emptyList(),
): MusicChromeState = MusicChromeState(
    playing = snapshot.playState == PlayState.PLAYING,
    buffering = snapshot.bufferState != BufferState.IDLE,
    track = track,
    timeSeconds = snapshot.time,
    durationSeconds = snapshot.duration,
    volume = snapshot.volume,
    muted = snapshot.muted,
    repeat = snapshot.repeatState,
    shuffled = snapshot.shuffleState == ShuffleState.ON,
    queueSize = snapshot.queueLength,
    queueIndex = snapshot.index,
    queue = queue,
)

// The title, which is all a playlist item has. An artist and artwork are the
// host's to supply, and a default that invented them would be guessing at a wire
// format the library was never told about.
//
// An item whose title never arrived is not a track worth naming: the row says so
// itself rather than drawing an empty line, which reads as something that failed
// to load.
public fun musicTrackOf(item: PlaylistItem?): MusicTrack? =
    item?.title?.takeIf { it.isNotBlank() }?.let { MusicTrack(title = it) }
