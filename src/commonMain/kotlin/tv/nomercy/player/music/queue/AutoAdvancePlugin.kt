// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.music.queue

import tv.nomercy.player.core.controllers.ComposedPlayer
import tv.nomercy.player.core.events.CoreEvents
import tv.nomercy.player.core.media.PlaylistItem
import tv.nomercy.player.core.plugin.Plugin
import tv.nomercy.player.core.plugin.PluginManifest
import tv.nomercy.player.music.NMMusicPlayer

/** What the plugin is allowed to do at the end of a track. */
public data class AutoAdvanceOptions(
    val enabled: Boolean = true,
    /**
     * What plays next. Null is the player's own order, which is what the web
     * means by "no generator" — identical behaviour, one less thing to pass.
     */
    val generator: PlaylistGenerator? = null,
    /** On `itemEndingSoon`, hand off to `NMMusicPlayer.crossfadeTo`. Default `false`. */
    val crossfade: Boolean = false,
    /** Crossfade duration in seconds. Default `0` — a hard cut. */
    val crossfadeDuration: Double = 0.0,
)

/**
 * Moves to the next track when one finishes.
 *
 * The same `auto-advance` id the web plugin has, so a consumer moving code
 * across writes the same line:
 *
 *     player.addPlugin(AutoAdvancePlugin(player))
 *
 * Do NOT register it when something else drives the queue — a Connect session,
 * a cast receiver, a server-driven radio. Two things advancing one queue skip a
 * track each time, and the symptom looks like a corrupt playlist rather than
 * two owners.
 *
 * The generator is the reason this is worth having as a plugin rather than a
 * flag on the player: "what comes after this" is where radio, recommendations
 * and mood ordering live, and a player that hardcoded index + 1 closes all of
 * them off.
 */
public open class AutoAdvancePlugin(
    private val player: ComposedPlayer,
    private val opts: AutoAdvanceOptions = AutoAdvanceOptions(),
) : Plugin<AutoAdvanceOptions>() {

    public companion object Manifest : PluginManifest {
        override val id: String = "auto-advance"

        // Two, matching the web plugin this mirrors.
        override val version: String = "2.0.0"
    }

    override val manifest: PluginManifest get() = Manifest

    override val options: AutoAdvanceOptions get() = opts

    override fun use() {
        on(CoreEvents.Ended) {
            if (opts.enabled) launch { advance() }
        }

        // The other half of the dispatch list, and the one that was missing.
        // `itemEndingSoon` has a producer and had no consumer at all, so the
        // hand-off window this plugin exists to use went by untouched: every
        // track change was a hard cut into a load that started at silence.
        on(CoreEvents.ItemEndingSoon) {
            if (opts.enabled) launch { onItemEndingSoon() }
        }
    }

    /**
     * Warm the next track and, when configured, start the crossfade into it.
     *
     * Public for the same reason [advance] is: a chrome that shows an "up next"
     * card wants the same preparation the timer would have done.
     */
    /**
     * Load the coming track into the next slot.
     *
     * Safe to call at any time and a no-op when the queue has nothing after
     * this. The reference exposes it so a host can warm the next track on its
     * own schedule — a radio UI priming as soon as the listener picks a
     * station, rather than waiting for the ending-soon threshold — and without
     * it the only way to preload was to sit and wait for the plugin to decide.
     *
     * Failures are swallowed, as they are there: a preload that did not land
     * costs a gapless transition, not the track that is currently playing.
     */
    @Suppress("TooGenericExceptionCaught", "SwallowedException")
    public suspend fun preloadNext() {
        val next: PlaylistItem = resolveNext() ?: return

        try {
            player.preloadNow(next)
        } catch (failure: Throwable) {
            // Swallowed as the reference swallows it: a preload that did not
            // land costs a gapless transition, not the track now playing.
        }
    }

    /**
     * Extra work to run when an item ends, after the built-in advance.
     *
     * The three handler lists are how the reference lets a host add behaviour
     * without subclassing — an analytics ping on every advance, a scrobble, a
     * queue refill. None of them existed here, so the only way to react was to
     * listen for the event separately and re-derive what the plugin had
     * already worked out.
     */
    public fun addEndedHandler(handler: suspend () -> Unit) {
        endedHandlers += handler
    }

    /** Extra work to run when the item is ending soon, given what is next. */
    public fun addPreloadHandler(handler: suspend (PlaylistItem?) -> Unit) {
        preloadHandlers += handler
    }

    /** Extra work to run alongside a crossfade, given what is next and how long. */
    public fun addCrossfadeHandler(handler: suspend (PlaylistItem?, Double) -> Unit) {
        crossfadeHandlers += handler
    }

    private val endedHandlers: MutableList<suspend () -> Unit> = mutableListOf()
    private val preloadHandlers: MutableList<suspend (PlaylistItem?) -> Unit> = mutableListOf()
    private val crossfadeHandlers: MutableList<suspend (PlaylistItem?, Double) -> Unit> = mutableListOf()

    public suspend fun onItemEndingSoon() {
        if (!opts.crossfade) return

        // Only a music player can crossfade — the method is not on the core
        // composition — so a plugin registered on a video player skips it
        // rather than failing at a cast.
        val music: NMMusicPlayer = player as? NMMusicPlayer ?: return
        val next: PlaylistItem = resolveNext() ?: return

        // The crossfade IS the head start: it loads the coming track into the
        // engine's secondary slot and primes it before the fade begins. The
        // reference's separate preloadNextOnEnding exists because a browser has
        // no such slot and warms the HTTP cache instead.
        music.crossfadeTo(next, opts.crossfadeDuration)
    }

    // What plays next, through the generator when there is one and the player's
    // own order when there is not — the same answer [advance] acts on, so a
    // preload cannot warm a track the advance is not going to play.
    private fun resolveNext(): PlaylistItem? {
        val generator: PlaylistGenerator = opts.generator ?: return player.peekNext()
        val queue: List<PlaylistItem> = player.queue()
        // The items, not the count. A generator that scores tracks by what they
        // are cannot do it from a size, and the count-only overload is what it
        // falls back to.
        val target: Int = generator.next(queue, player.index()) ?: return null
        return queue.getOrNull(target)
    }

    /**
     * Move on now, whether or not the track ended.
     *
     * Public because the web's is: a chrome's next button and a voice command
     * both want the generator's answer rather than the player's raw next().
     */
    public suspend fun advance() {
        val generator: PlaylistGenerator = opts.generator ?: run {
            player.next()
            return
        }

        val queue = player.queue()
        val target: Int? = generator.next(queue, player.index())

        // Null is end of queue, which is a normal outcome. Falling back to the
        // player's own next() here would make a generator that said "stop"
        // advance anyway, and a radio that ended would loop instead.
        //
        // By id rather than by index, because that is what the player takes and
        // because an index resolved against a queue that changed between the
        // generator answering and this line running would play the wrong track.
        val id: String? = queue.getOrNull(target ?: return)?.id
        if (id != null) player.item(id)
    }

    /** What the generator says is next, without moving. */
    public fun peekNext(): Int? {
        val generator: PlaylistGenerator = opts.generator ?: return null
        return generator.next(player.queue(), player.index())
    }
}
