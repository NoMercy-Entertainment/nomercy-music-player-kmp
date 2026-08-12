// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.music.item

import tv.nomercy.player.core.media.PlaylistItem

// A track, with the four fields a Now Playing surface reads off it.
//
// Core's item is an id, a url and a title, and that is right for core. What a
// lock screen draws for music is a track name, an artist, an album and a cover,
// and none of those are core's to invent — so they live here, on the library
// that has them.
//
// Implemented by a host's own track rather than constructed here, like the video
// item: a row from whatever server the host talks to can BE one of these without
// being copied into a shape this library made up.
public interface MusicPlaylistItem : PlaylistItem {
    /**
     * The track name. Required, because a music item without one is not a track
     * a viewer can be shown — core's [PlaylistItem.title] is the generic label
     * and this is the specific one the web item declares.
     */
    public val name: String

    /** Plain artist name. A host with linked-entity data joins or picks the primary. */
    public val artist: String? get() = null

    /** Plain album name. A host with linked-entity data joins or picks the primary. */
    public val album: String? get() = null

    /** Cover art, read first. Same field name the video item uses for the same picture. */
    public val image: String? get() = null

    /**
     * Cover art, read when [image] is absent.
     *
     * The older of the two names, kept for the same reason the web item keeps
     * it: hosts already populate it, and reading both costs one elvis.
     */
    public val cover: String? get() = null

    /**
     * Whether the host's own server considers this track a favorite.
     *
     * Read-only here — toggling it is a server call this library has no
     * business making (see [PlaylistItem]'s own narrow-seam reasoning). A
     * host that has the concept supplies it; one that does not leaves the
     * default, and no favorite button is drawn for it.
     */
    public val favorite: Boolean get() = false

    /**
     * The server-relative link a host POSTs a favorite toggle to, or null
     * for a track with nothing to toggle (a plugin's synthetic item, a
     * bare stream). Distinct from [PlaylistItem.url] — that is the media
     * source, this is the API resource.
     */
    public val link: String? get() = null
}
