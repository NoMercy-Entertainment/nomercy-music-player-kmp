// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.music

import tv.nomercy.player.core.events.BeforeEvent
import tv.nomercy.player.core.events.EventKey
import tv.nomercy.player.core.media.PlaylistItem

// What a music player has that a video player does not: crossfade.
//
// Five keys. That is the whole difference at the event level, which is the
// point — a music player is a player, and almost everything it does is
// CoreEvents. A listener for `play` uses CoreEvents.Play here too.

// Which engine is playing. Music swaps engines mid-session in a way video does
// not: a gapless local file and a streamed track can want different ones.
public data class AudioBackendChange(val kind: String)

// One track fading into the next, and how long the overlap is.
//
// [from] is null on the first track of a session, which a visualiser reading it
// has to handle — there is nothing to fade out of.
public data class Crossfade(
    val from: PlaylistItem?,
    val to: PlaylistItem,
    val duration: Double,
)

public data class CrossfadeComplete(val item: PlaylistItem)

// Why a crossfade did not happen. Same shape as every other prevented event so
// a consumer handles them the same way.
public data class CrossfadePrevented(val reason: String?, val cause: Any? = null)

public object MusicEvents {
    public val BackendChanged: EventKey<AudioBackendChange> = EventKey("backend:changed")

    // Refusable, because a crossfade is a decision about two items and a plugin
    // may know something about either of them.
    public val BeforeCrossfade: EventKey<BeforeEvent<Crossfade>> = EventKey("beforeCrossfade")

    public val CrossfadeStart: EventKey<Crossfade> = EventKey("crossfadeStart")
    public val CrossfadeComplete: EventKey<tv.nomercy.player.music.CrossfadeComplete> =
        EventKey("crossfadeComplete")
    public val CrossfadePrevented: EventKey<tv.nomercy.player.music.CrossfadePrevented> =
        EventKey("crossfadePrevented")

    // Every key, for the conformance gate that checks this registry against the
    // contract's music map.
    public val all: List<EventKey<*>> = listOf(
        BackendChanged,
        BeforeCrossfade,
        CrossfadeStart,
        CrossfadeComplete,
        CrossfadePrevented,
    )
}
