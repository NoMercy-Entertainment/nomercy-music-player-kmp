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
import tv.nomercy.player.core.ports.Clock
import tv.nomercy.player.core.ports.defaultClock

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
    // Stamps the moment a track started. Injected so a test decides what time it
    // is, and so a fleet sharing one clock stamps the same instant.
    private val clock: Clock = defaultClock(),
) : Plugin<ScrobbleRules>() {

    public companion object Manifest : PluginManifest {
        override val id: String = "scrobble"

        // Two, matching the web plugin this mirrors.
        override val version: String = "2.0.0"

        internal const val MILLIS_PER_SECOND: Long = 1_000L
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
            if (id == null) tracker.reset() else beginTrack(id)
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

    private var startedAt: Long = 0L

    private fun beginTrack(id: String) {
        tracker.startTrack(id)
        // Seconds, not milliseconds: Last.fm's `timestamp` is a Unix time in
        // seconds and a millisecond value is silently rejected as a date
        // thousands of years out.
        startedAt = clock.now() / MILLIS_PER_SECOND
        launch {
            scrobbler.nowPlaying(id)
            // After the backend answered, matching the reference: the event
            // means "the service has been told", and firing it before the call
            // would make a listener's badge appear for a service that refused.
            emit(NowPlaying, NowPlayingReport(id))
        }
    }

    private fun reportIfDue(durationSeconds: Double) {
        lastDuration = durationSeconds
        if (!tracker.shouldScrobble(durationSeconds)) return

        val id: String = tracker.currentTrack() ?: return
        val heard: Double = tracker.listenedSeconds()

        launch {
            scrobbler.scrobble(
                id,
                ScrobbleContext(
                    startedAt = startedAt,
                    listenedSeconds = heard,
                    durationSeconds = durationSeconds,
                ),
            )
        }
        emit(Scrobbled, ScrobbleReport(id, heard))
    }
}

// Where a listen started and how much of it was heard.
//
// [startedAt] is a Unix timestamp in SECONDS. Last.fm's submission API requires
// it and rejects a scrobble without one, so a report that carried only the
// listened seconds was unsubmittable however complete it looked.
public data class ScrobbleContext(
    val startedAt: Long,
    val listenedSeconds: Double,
    val durationSeconds: Double,
    // Whether the viewer chose this track or it arrived by auto-advance. Fixed
    // at "user" until the queue carries the distinction; the field is here
    // because the wire format has it and a backend reading a report should not
    // have to guess which shape it got.
    val source: String = "user",
)

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
    public suspend fun scrobble(trackId: String, context: ScrobbleContext)
}

/**
 * The default: reports nowhere. The web's `NoopScrobbler`.
 *
 * The empty bodies are the whole point rather than an oversight -- adding the
 * plugin must not start sending a listener's history to a service nobody
 * configured -- so they are suppressed here rather than filled with something
 * that would make the default do work.
 */
@Suppress("EmptyFunctionBlock")
public object NoScrobbler : Scrobbler {
    override suspend fun nowPlaying(trackId: String) {}

    override suspend fun scrobble(trackId: String, context: ScrobbleContext) {}
}

public data class ScrobbleReport(val trackId: String, val listenedSeconds: Double)

public data class NowPlayingReport(val trackId: String)

/** Emitted after a track crosses the threshold, matching the web's `scrobbled`. */
public val Scrobbled: EventKey<ScrobbleReport> = EventKey("scrobbled")

/**
 * Emitted once per track after the backend has been told it started, matching
 * the web's `nowPlaying`. The plugin was calling the backend and telling nobody,
 * so a chrome drawing a "scrobbling to Last.fm" badge had nothing to bind to.
 */
public val NowPlaying: EventKey<NowPlayingReport> = EventKey("nowPlaying")
