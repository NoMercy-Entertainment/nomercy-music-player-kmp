// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.music

import tv.nomercy.player.core.controllers.ComposedPlayer
import tv.nomercy.player.core.media.PlaylistItem
import tv.nomercy.player.core.ports.AudioBackend
import tv.nomercy.player.core.player.PlayerConfig
import tv.nomercy.player.core.ports.CrossfadeCurve
import tv.nomercy.player.core.ports.CrossfadeTransitionStrategy
import tv.nomercy.player.core.ports.TransitionStrategy
import tv.nomercy.player.core.ports.MediaBackend
import tv.nomercy.player.core.ports.TransitionBackend

// A music player.
//
// It is a core player plus crossfade, which is genuinely the whole difference.
// Transport, queue, volume, plugins and state all come from the core
// composition and are not restated here.
//
// Crossfading is a decision, not just a fade: it goes through the same
// cancellable seam every other player action does, so a plugin can shorten it,
// refuse it, or hold it open while it decides. A gapless album should not be
// crossfaded, and the plugin that knows that is the one holding the tags.
public open class NMMusicPlayer(
    backend: MediaBackend? = null,
    // The same backend again when it can hold two tracks at once.
    //
    // Passed rather than tested for, because asking a MediaBackend whether it is
    // really a TransitionBackend is a cast, and the one rule this library does
    // not bend is that a caller says what it has instead of the library
    // guessing. The convenience constructor below covers the ordinary case.
    private val transitions: TransitionBackend? = null,
    // Names this player, and is what the factory looks it up by.
    id: String = "nmmusic",
) : ComposedPlayer(backend, playerId = id) {

    // An audio backend is both, so a caller with one says so once.
    public constructor(audio: AudioBackend, id: String = "nmmusic") : this(audio, audio, id)

    private var crossfadeSeconds: Double = 0.0

    // Whether a consumer picked the transition themselves.
    //
    // Needed because "core's default" and "a consumer who deliberately chose
    // gapless" are the same object otherwise, and setup filling in a crossfade
    // over the second is exactly the overwrite this is meant to avoid.
    private var transitionChosen: Boolean = false

    // Whether a crossfade is running right now.
    //
    // The guard that makes crossfadeTo idempotent. Two overlapping crossfades
    // both drive the same gain, and the loser wins: one ends by setting the
    // outgoing track to silence while the other is still fading it up, so a
    // track disappears mid-song. A second call while one is running is answered
    // rather than queued, because by the time the first ends the second is
    // about a track that is no longer next.
    public open fun isTransitioning(): Boolean = transitioning

    public open fun crossfadeEnabled(): Boolean = crossfadeSeconds > 0.0

    // How long a crossfade takes when the caller does not say.
    //
    // Configured rather than exposed as a getter and setter, matching the web,
    // where it lives in crossfadeDefaults. Zero means off, which is the default
    // and the right one: crossfading by surprise is worse than not crossfading.
    public open fun configureCrossfade(seconds: Double) {
        crossfadeSeconds = seconds.coerceAtLeast(0.0)
    }

    private var transitioning: Boolean = false

    // Fades from what is playing into [next] and reports what happened.
    //
    // Returns true when the crossfade ran. A refusal is not a failure — it means
    // something knew better — so the caller gets false and plays the next track
    // the ordinary way.
    public open suspend fun crossfadeTo(next: PlaylistItem, seconds: Double? = null): Boolean {
        // Refused rather than queued. By the time a running crossfade finishes,
        // a second request is about a track that is no longer the one coming up.
        if (transitioning) return refuse(ALREADY_TRANSITIONING)

        val outcome = dispatchBefore(
            MusicEvents.BeforeCrossfade,
            Crossfade(from = item(), to = next, duration = seconds ?: crossfadeSeconds),
        )
        val resolved: Crossfade = outcome.data
        val engine: TransitionBackend? = transitions

        // Every way this can decline, decided in one place. A listener refusing,
        // a listener shortening it to nothing, and an engine that cannot hold two
        // tracks at once are all the same answer to the caller: false, with a
        // reason. Emitting a start and a completion with nothing between them is
        // what this did before the transition seam existed, and it looked exactly
        // like a working crossfade.
        val refusal: String? = when {
            outcome.prevented -> outcome.reason
            resolved.duration <= 0.0 -> "zero-duration"
            engine == null -> "no-transition-backend"
            !engine.supportsCrossfade() -> "backend-cannot-crossfade"
            else -> null
        }
        if (engine == null || refusal != null) return refuse(refusal)

        emit(MusicEvents.CrossfadeStart, resolved)
        transitioning = true
        try {
            engine.loadSecondary(resolved.to.url)
            engine.primeSecondary()
            engine.crossfade((resolved.duration * MILLIS_PER_SECOND).toLong(), CrossfadeCurve.EQUAL_POWER)
        } finally {
            // In a finally because an engine that throws mid-fade would
            // otherwise leave this player believing it is still transitioning,
            // and every crossfade after it would be refused for the rest of the
            // session.
            transitioning = false
        }

        emit(MusicEvents.CrossfadeComplete, CrossfadeComplete(resolved.to))
        return true
    }

    private fun refuse(reason: String?): Boolean {
        emit(MusicEvents.CrossfadePrevented, CrossfadePrevented(reason))
        return false
    }

    public open fun announceBackend(kind: String) {
        emit(MusicEvents.BackendChanged, AudioBackendChange(kind))
    }

    // Music defaults, applied where the caller left a gap.
    //
    // A music player that crossfades out of the box is what everyone expects,
    // and a video player that did would be wrong — which is exactly why the
    // default lives here rather than in core.
    //
    // Only where absent. A consumer who configured three seconds asked for three
    // seconds, and a library that overwrote it at setup would be answering a
    // question nobody asked.
    override suspend fun setup(config: PlayerConfig) {
        super.setup(config)
        if (crossfadeSeconds <= 0.0) crossfadeSeconds = config.crossfadeTailSeconds
        if (!transitionChosen) {
            val filled = CrossfadeTransitionStrategy(
                leadSeconds = config.crossfadeLeadSeconds,
                tailSeconds = config.crossfadeTailSeconds,
            )
            super.setTransitionStrategy(filled)
        }
    }

    // Overridden only to remember that the choice was made. A consumer who picks
    // gapless has picked something, and setup must not read that as a gap.
    override fun setTransitionStrategy(strategy: TransitionStrategy) {
        transitionChosen = true
        super.setTransitionStrategy(strategy)
    }

    public companion object {
        private const val MILLIS_PER_SECOND = 1000.0

        private const val ALREADY_TRANSITIONING = "already-transitioning"

        // The players the factory built, by id.
        //
        // Only the factory's. A consumer who constructs one directly is doing
        // something deliberate — a preview scrubber, a second engine under test
        // — and a library that silently added it to a shared registry would hand
        // it to the next caller asking for a player by that name.
        private val live: MutableMap<String, NMMusicPlayer> = mutableMapOf()

        public fun instances(): List<NMMusicPlayer> = live.values.toList()

        public fun byId(id: String): NMMusicPlayer? = live[id]

        internal fun register(player: NMMusicPlayer) {
            live[player.playerId] = player
        }

        // For a host tearing a player down, and for tests, which would otherwise
        // leak an instance into every later one through a process-wide map.
        public fun forget(id: String) {
            live.remove(id)
        }
    }
}
