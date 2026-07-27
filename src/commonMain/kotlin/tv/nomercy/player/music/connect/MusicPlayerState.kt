// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.music.connect

import tv.nomercy.player.core.media.PlaylistItem
import tv.nomercy.player.core.player.RepeatState

// One decoded Connect frame: what the server says every device should be doing.
//
// Already decoded, and that is the seam. The application walks the wire envelope
// and produces these; the library never sees a hub method name, a JSON object or
// a snake_case key. That split is what lets one plugin serve a phone, a desktop
// and a television whose transports have nothing in common.
public data class MusicPlayerState(
    // The device producing sound. Adopted verbatim rather than reasoned about:
    // the server decides who is active, and a client that argued would be a
    // second authority — which is how two devices come to play at once.
    val deviceId: String?,
    // Per-user and monotonic. Zero means a server old enough not to sequence its
    // broadcasts, and those are always applied rather than dropped.
    val seq: Long,
    // Null is the session ending. The server nulls the current item on stop, and
    // every device treats that as "stop, wherever you are".
    val item: PlaylistItem?,
    // What comes AFTER the current track. The server removes the current one on
    // advancing, so a client that prepended it would show it twice.
    val playlist: List<PlaylistItem> = emptyList(),
    val isPlaying: Boolean = false,
    val progressMs: Long = 0,
    val durationMs: Long = 0,
    // When the server sent this, on the server's clock. Passive devices
    // interpolate from it, so it is the anchor rather than decoration.
    val timestamp: Long = 0,
    // When the active device's position was actually taken, which is not the
    // same instant as the broadcast and is the better anchor where a server
    // sends it. Older ones do not.
    val positionCapturedAtMs: Long? = null,
    val serverTimeMs: Long? = null,
    val mutedState: Boolean = false,
    val volumePercentage: Int = 100,
    val repeatState: RepeatState = RepeatState.OFF,
    val shuffleState: Boolean = false,
    val currentList: String? = null,
    // Each device's own remembered level, by device id. Never a mirrored value:
    // a phone at thirty percent and a television at eighty are both correct, and
    // adopting the other's would make one of them wrong every time focus moved.
    val deviceVolumes: Map<String, Int> = emptyMap(),
)
