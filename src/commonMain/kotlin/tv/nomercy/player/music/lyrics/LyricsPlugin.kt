// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.music.lyrics

import tv.nomercy.player.core.cues.Cue
import tv.nomercy.player.core.cues.TextPayload
import tv.nomercy.player.core.events.CoreEvents
import tv.nomercy.player.core.events.EventKey
import tv.nomercy.player.core.events.ItemChange
import tv.nomercy.player.core.events.TimeUpdate
import tv.nomercy.player.core.media.CueCrossing
import tv.nomercy.player.core.media.CueTracker
import tv.nomercy.player.core.media.PlaylistItem
import tv.nomercy.player.core.plugin.Plugin
import tv.nomercy.player.core.plugin.PluginManifest
import tv.nomercy.player.core.plugin.pluginEventKey
import tv.nomercy.player.core.ports.CueParser

public data class LyricsOptions(
    /**
     * Where a track's lyrics live.
     *
     * A resolver rather than a field on the item, which is what the web does
     * and is the reason this plugin does not need a richer item type than
     * [PlaylistItem]. A host whose items carry a `lyricsUrl` writes one lambda;
     * a host that derives the url from an id writes a different one, and
     * neither has to conform to a shape this library invented. Defaulting to
     * null means the plugin ships inert.
     */
    val getLyricsUrl: (PlaylistItem) -> String? = { null },
    /** Fetch on every item change. Off means the host calls [LyricsPlugin.load]. */
    val autoFetch: Boolean = true,
)

/**
 * Where the lyrics come from.
 *
 * A port for the same reason [tv.nomercy.player.music.scrobble.Scrobbler] is
 * one: the web reaches the network through the kit's auth-aware fetch, and
 * there is no such thing here that a library should own. A host already has an
 * http client with its tokens in it.
 */
public interface LyricsSource {
    /** The file at [url], or null when there is none. */
    public suspend fun fetch(url: String): String?
}

/** Fetches nothing, so adding the plugin cannot reach the network by itself. */
public object NoLyricsSource : LyricsSource {
    override suspend fun fetch(url: String): String? = null
}

/**
 * Synced lyrics, line by line.
 *
 * The same `lyrics` id the web plugin has, so a consumer moving code across
 * writes the same line:
 *
 *     player.addPlugin(LyricsPlugin(source = MyBackend()))
 *
 * On every item change it resolves a url, fetches it, parses it through the
 * HOST's cue-parser registry — reached via [resolveCueParser], the same seam
 * `player.resolveCueParser(url)` gives a consumer — so an LRC file and a VTT
 * file both work, and a consumer's own format works by registering a parser on
 * the player. This used to carry a private [tv.nomercy.player.core.ports.CueParserRegistry]
 * seeded with the built-ins, which could read the two formats everybody ships
 * but never a format the host itself had registered — a consumer's own parser
 * was invisible to it. There is no such registry here now; there is one
 * registry, the player's, and this plugin asks it the way every other consumer
 * does.
 *
 * Publishes lines and draws nothing. The web plugin owns a DOM node because a
 * browser gives it one; here a Compose or SwiftUI surface renders [line] and
 * [lines] its own way and both get the same answers.
 */
public open class LyricsPlugin(
    private val source: LyricsSource = NoLyricsSource,
    private val opts: LyricsOptions = LyricsOptions(),
) : Plugin<LyricsOptions>() {

    public companion object Manifest : PluginManifest {
        override val id: String = "lyrics"

        // Two, matching the web plugin this mirrors.
        override val version: String = "2.0.0"
    }

    override val manifest: PluginManifest get() = Manifest

    override val options: LyricsOptions get() = opts

    private val tracker = CueTracker<TextPayload>()

    /** Every line of the current track, in order. Empty when there are none. */
    public fun lines(): List<Cue<TextPayload>> = tracker.cues()

    /** The line at the current position, or null between lines. */
    public fun line(): Cue<TextPayload>? = tracker.line

    override fun use() {
        on(CoreEvents.Item) { change: ItemChange ->
            // Cleared first, always. A track whose lyrics fail to arrive must
            // not leave the previous track's words on the screen, which is a
            // worse failure than showing none: it is confidently wrong.
            tracker.load(emptyList())
            emit(LyricsEvents.Cleared, Unit)

            val item: PlaylistItem? = change.item
            if (opts.autoFetch && item != null) {
                opts.getLyricsUrl(item)?.let { url -> launch { fetchInto(url) } }
            }
        }

        on(CoreEvents.Time) { update: TimeUpdate ->
            val crossing: CueCrossing<TextPayload> = tracker.advanceTo(update.time)

            if (crossing.changed) {
                crossing.exited.forEach { emit(LyricsEvents.LineExit, it) }
                crossing.entered.forEach { emit(LyricsEvents.LineEnter, it) }

                // The active line as well as the crossings, because most
                // consumers want "what do I draw" rather than "what moved".
                // Emitted after the crossings so a listener to both sees them
                // in that order.
                emit(LyricsEvents.Line, tracker.line)
            }
        }
    }

    /**
     * Fetch and attach a lyrics file by hand.
     *
     * For a host that turns [LyricsOptions.autoFetch] off because it resolves
     * lyrics some other way — a cache, a bundled file, a user-supplied one.
     */
    public suspend fun load(url: String) {
        fetchInto(url)
    }

    /** Attach cues that are already parsed. */
    public fun attach(cues: List<Cue<TextPayload>>) {
        tracker.load(cues)
        emit(LyricsEvents.Loaded, cues.size)
    }

    private suspend fun fetchInto(url: String) {
        val raw: String = source.fetch(url) ?: run {
            emit(LyricsEvents.Unavailable, url)
            return
        }

        val parser: CueParser<*>? = resolveCueParser(url)
        if (parser == null) {
            // Named, not swallowed. A karaoke format nobody registered a parser
            // for is a consumer's missing line of setup, and a plugin that
            // reported "no lyrics" would send them looking at the file.
            emit(LyricsEvents.NoParser, url)
            return
        }

        // The registry is heterogeneous by design — it also holds sprite and
        // arbitrary-format parsers — so nothing proves its T from here. Narrowing
        // to TextPayload rather than to LrcPayload is deliberate: this url can
        // resolve to the LRC parser or the VTT subtitle parser (both work, and
        // the test suite pins that), and their payloads are LrcPayload and
        // VttSubtitlePayload — two different concrete types with one field in
        // common, `text`. Casting straight to LrcPayload compiled and then threw
        // a ClassCastException the first time a VTT file went through the seam;
        // TextPayload is the shape every built-in payload actually shares, which
        // is also exactly how the web's own LyricsPlugin gets away with a single
        // cast here — its local `LyricPayload` type is `{ text: string }`, an
        // open shape a lyrics or a subtitle payload equally satisfies. The second
        // suppression is the detekt-player rule that flags the first; both apply
        // to this one documented erasure boundary, not a second one anywhere else.
        @Suppress("UNCHECKED_CAST", "NoUncheckedCast")
        val lyricsParser = parser as CueParser<TextPayload>

        attach(lyricsParser.parse(raw, baseUrl = url))
    }
}

// What the plugin publishes, and what a consumer subscribes to on the player.
//
// Both halves, because a plugin's emit is namespaced on the way out and a
// listener built from the wrong one never fires.
public object LyricsEvents {

    public val Loaded: EventKey<Int> = EventKey("loaded")

    public val Line: EventKey<Cue<TextPayload>?> = EventKey("line")

    public val LineEnter: EventKey<Cue<TextPayload>> = EventKey("lineEnter")

    public val LineExit: EventKey<Cue<TextPayload>> = EventKey("lineExit")

    public val Cleared: EventKey<Unit> = EventKey("cleared")

    public val Unavailable: EventKey<String> = EventKey("unavailable")

    public val NoParser: EventKey<String> = EventKey("noParser")

    public val LoadedOnPlayer: EventKey<Int> =
        pluginEventKey(LyricsPlugin.Manifest, "loaded")

    public val LineOnPlayer: EventKey<Cue<TextPayload>?> =
        pluginEventKey(LyricsPlugin.Manifest, "line")

    public val LineEnterOnPlayer: EventKey<Cue<TextPayload>> =
        pluginEventKey(LyricsPlugin.Manifest, "lineEnter")

    public val LineExitOnPlayer: EventKey<Cue<TextPayload>> =
        pluginEventKey(LyricsPlugin.Manifest, "lineExit")

    public val ClearedOnPlayer: EventKey<Unit> =
        pluginEventKey(LyricsPlugin.Manifest, "cleared")

    public val UnavailableOnPlayer: EventKey<String> =
        pluginEventKey(LyricsPlugin.Manifest, "unavailable")

    public val NoParserOnPlayer: EventKey<String> =
        pluginEventKey(LyricsPlugin.Manifest, "noParser")
}
