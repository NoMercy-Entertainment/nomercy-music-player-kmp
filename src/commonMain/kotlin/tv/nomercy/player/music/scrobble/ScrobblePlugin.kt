// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.music.scrobble

import tv.nomercy.player.core.events.CoreEvents
import tv.nomercy.player.core.events.EventKey
import tv.nomercy.player.core.events.ItemChange
import tv.nomercy.player.core.events.TimeUpdate
import tv.nomercy.player.core.plugin.Plugin
import tv.nomercy.player.core.plugin.PluginManifest

/**
 * Reports what was listened to.
 *
 * The same `scrobble` id the web plugin has, so a consumer moving code across
 * writes the same line:
 *
 *     player.addPlugin(ScrobblePlugin(scrobbler = MyBackend()))
 *
 * [ScrobbleTracker] holds the rules and this holds the wiring. That split is
 * not decoration: the thresholds are worth testing without a player, and the
 * player is worth driving without a network.
 *
 * Ships inert. [scrobbler] defaults to reporting nowhere, matching the web's
 * `NoopScrobbler`, so adding the plugin cannot start sending a listener's
 * history to a service nobody configured.
 */
public open class ScrobblePlugin(
    private val scrobbler: Scrobbler = NoScrobbler,
    private val rules: ScrobbleRules = ScrobbleRules(),
) : Plugin<ScrobbleRules>() {

    public companion object Manifest : PluginManifest {
        override val id: String = "scrobble"

        // Two, matching the web plugin this mirrors.
        override val version: String = "2.0.0"
    }

    override val manifest: PluginManifest get() = Manifest

    override val options: ScrobbleRules get() = rules

    private val tracker = ScrobbleTracker(rules)

    /** Seconds of the current track actually heard. */
    public fun listened(): Double = tracker.listenedSeconds()

    /** Whether the current track has already been reported. */
    public fun isScrobbled(): Boolean = tracker.hasScrobbled()

    override fun use() {
        on(CoreEvents.Item) { change: ItemChange ->
            val id: String? = change.item?.id
            if (id == null) {
                tracker.reset()
            } else {
                tracker.startTrack(id)
                launch { scrobbler.nowPlaying(id) }
            }
        }

        on(CoreEvents.Time) { update: TimeUpdate ->
            tracker.advanceTo(update.time)
            reportIfDue(update.duration)
        }

        // Asked again on ended, because a track that finishes between two time
        // updates would otherwise never cross the threshold on a tick. The
        // tracker latches, so asking twice reports once.
        on(CoreEvents.Ended) { lastDuration?.let(::reportIfDue) }
    }

    override fun dispose() {
        tracker.reset()
    }

    private var lastDuration: Double? = null

    private fun reportIfDue(durationSeconds: Double) {
        lastDuration = durationSeconds
        if (!tracker.shouldScrobble(durationSeconds)) return

        val id: String = tracker.currentTrack() ?: return
        val heard: Double = tracker.listenedSeconds()

        launch { scrobbler.scrobble(id, heard) }
        emit(Scrobbled, ScrobbleReport(id, heard))
    }
}

/**
 * Where playback reports go.
 *
 * Ids rather than items, because that is all `PlaylistItem` carries and it is
 * all a backend needs to match against its own catalogue. A richer version
 * belongs with the music item type this library does not have yet.
 */
public interface Scrobbler {
    /** Fired once per track, as soon as it starts. */
    public suspend fun nowPlaying(trackId: String)

    /** Fired once per track, when enough of it has been heard. */
    public suspend fun scrobble(trackId: String, listenedSeconds: Double)
}

/** The default: reports nowhere. The web's `NoopScrobbler`. */
public object NoScrobbler : Scrobbler {
    override suspend fun nowPlaying(trackId: String) {}

    override suspend fun scrobble(trackId: String, listenedSeconds: Double) {}
}

public data class ScrobbleReport(val trackId: String, val listenedSeconds: Double)

/** Emitted after a track crosses the threshold, matching the web's `scrobbled`. */
public val Scrobbled: EventKey<ScrobbleReport> = EventKey("scrobbled")
