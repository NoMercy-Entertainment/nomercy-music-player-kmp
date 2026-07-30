// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.music.lyrics

import tv.nomercy.player.core.cues.registerBuiltIns
import tv.nomercy.player.core.events.CoreEvents
import tv.nomercy.player.core.events.CueEvent
import tv.nomercy.player.core.events.EventKey
import tv.nomercy.player.core.events.ItemChange
import tv.nomercy.player.core.events.TimeUpdate
import tv.nomercy.player.core.media.CueCrossing
import tv.nomercy.player.core.media.CueTracker
import tv.nomercy.player.core.media.PlaylistItem
import tv.nomercy.player.core.plugin.Plugin
import tv.nomercy.player.core.plugin.PluginManifest
import tv.nomercy.player.core.plugin.pluginEventKey
import tv.nomercy.player.core.ports.CueParserRegistry

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
 * kit's [CueParserRegistry] — so an LRC file and a VTT file both work, and a
 * consumer's own format works by registering a parser — and hands the cues to a
 * [CueTracker]. Time updates then produce line crossings.
 *
 * Publishes lines and draws nothing. The web plugin owns a DOM node because a
 * browser gives it one; here a Compose or SwiftUI surface renders [line] and
 * [lines] its own way and both get the same answers.
 */
public open class LyricsPlugin(
    private val source: LyricsSource = NoLyricsSource,
    // The built-ins, so a plugin whose whole job is synced lyrics can read an
    // LRC file without a consumer registering a parser for the format everybody
    // ships. An empty registry was the default and it made the default plugin
    // inert: every url reported noParser.
    //
    // Pass `player.cueParsers` to share the player's registry, which is what a
    // host that has registered its own format wants — this default cannot see
    // those, because a plugin here reaches the host through PluginHost and cue
    // resolution is not on it.
    private val parsers: CueParserRegistry = CueParserRegistry().registerBuiltIns(),
    private val opts: LyricsOptions = LyricsOptions(),
) : Plugin<LyricsOptions>() {

    public companion object Manifest : PluginManifest {
        override val id: String = "lyrics"

        // Two, matching the web plugin this mirrors.
        override val version: String = "2.0.0"
    }

    override val manifest: PluginManifest get() = Manifest

    override val options: LyricsOptions get() = opts

    private val tracker = CueTracker()

    /** Every line of the current track, in order. Empty when there are none. */
    public fun lines(): List<CueEvent> = tracker.cues()

    /** The line at the current position, or null between lines. */
    public fun line(): CueEvent? = tracker.line

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
            val crossing: CueCrossing = tracker.advanceTo(update.time)

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
    public fun attach(cues: List<CueEvent>) {
        tracker.load(cues)
        emit(LyricsEvents.Loaded, cues.size)
    }

    private suspend fun fetchInto(url: String) {
        val raw: String = source.fetch(url) ?: run {
            emit(LyricsEvents.Unavailable, url)
            return
        }

        val parser = parsers.resolve(url) ?: run {
            // Named, not swallowed. A karaoke format nobody registered a parser
            // for is a consumer's missing line of setup, and a plugin that
            // reported "no lyrics" would send them looking at the file.
            emit(LyricsEvents.NoParser, url)
            return
        }

        attach(parser.parse(raw, baseUrl = url))
    }
}

// What the plugin publishes, and what a consumer subscribes to on the player.
//
// Both halves, because a plugin's emit is namespaced on the way out and a
// listener built from the wrong one never fires.
public object LyricsEvents {

    public val Loaded: EventKey<Int> = EventKey("loaded")

    public val Line: EventKey<CueEvent?> = EventKey("line")

    public val LineEnter: EventKey<CueEvent> = EventKey("lineEnter")

    public val LineExit: EventKey<CueEvent> = EventKey("lineExit")

    public val Cleared: EventKey<Unit> = EventKey("cleared")

    public val Unavailable: EventKey<String> = EventKey("unavailable")

    public val NoParser: EventKey<String> = EventKey("noParser")

    public val LoadedOnPlayer: EventKey<Int> =
        pluginEventKey(LyricsPlugin.Manifest, "loaded")

    public val LineOnPlayer: EventKey<CueEvent?> =
        pluginEventKey(LyricsPlugin.Manifest, "line")

    public val LineEnterOnPlayer: EventKey<CueEvent> =
        pluginEventKey(LyricsPlugin.Manifest, "lineEnter")

    public val LineExitOnPlayer: EventKey<CueEvent> =
        pluginEventKey(LyricsPlugin.Manifest, "lineExit")

    public val ClearedOnPlayer: EventKey<Unit> =
        pluginEventKey(LyricsPlugin.Manifest, "cleared")

    public val UnavailableOnPlayer: EventKey<String> =
        pluginEventKey(LyricsPlugin.Manifest, "unavailable")

    public val NoParserOnPlayer: EventKey<String> =
        pluginEventKey(LyricsPlugin.Manifest, "noParser")
}
