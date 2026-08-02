// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.music.item

/**
 * A track, for a caller who has one rather than a library of them.
 *
 * The counterpart of core's MediaItem, and missing for the same reason it was:
 * [MusicPlaylistItem] is an interface so a host's own row can BE a track
 * without being copied into a shape this library made up. That is right, and it
 * leaves a caller holding a url, a name and an artist with nothing to
 * construct — and Swift cannot declare a conformer to an exported Kotlin
 * interface at all, so on Apple the gap is not an inconvenience but a wall.
 *
 * It adds nothing to the interface. It exists so that "play this track" is a
 * line rather than a file.
 */
public data class MusicItem(
    override val id: String,
    override val url: String,
    override val name: String,
    override val artist: String? = null,
    override val album: String? = null,
    override val image: String? = null,
    override val cover: String? = null,
) : MusicPlaylistItem {

    // The generic label, filled from the specific one. A chrome reading either
    // gets the track's name rather than one of them getting null.
    override val title: String get() = name
}
