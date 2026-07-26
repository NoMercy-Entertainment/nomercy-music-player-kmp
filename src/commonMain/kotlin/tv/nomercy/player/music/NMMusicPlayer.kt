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
import tv.nomercy.player.core.ports.CrossfadeCurve
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
) : ComposedPlayer(backend) {

    // An audio backend is both, so a caller with one says so once.
    public constructor(audio: AudioBackend) : this(audio, audio)

    private var crossfadeSeconds: Double = 0.0

    init {
        register(this)
    }

    // Zero means off, which is the default and the right one: crossfading by
    // surprise is worse than not crossfading.
    public open fun crossfadeDuration(): Double = crossfadeSeconds

    public open fun crossfadeDuration(seconds: Double) {
        crossfadeSeconds = seconds.coerceAtLeast(0.0)
    }

    public open fun crossfadeEnabled(): Boolean = crossfadeSeconds > 0.0

    // Fades from what is playing into [next] and reports what happened.
    //
    // Returns true when the crossfade ran. A refusal is not a failure — it means
    // something knew better — so the caller gets false and plays the next track
    // the ordinary way.
    public open suspend fun crossfadeTo(next: PlaylistItem): Boolean {
        val outcome = dispatchBefore(
            MusicEvents.BeforeCrossfade,
            Crossfade(from = item(), to = next, duration = crossfadeSeconds),
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

        engine.loadSecondary(resolved.to.url)
        engine.primeSecondary()
        engine.crossfade((resolved.duration * MILLIS_PER_SECOND).toLong(), CrossfadeCurve.EQUAL_POWER)

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

    public companion object {
        private const val MILLIS_PER_SECOND = 1000.0

        private val live: MutableList<NMMusicPlayer> = mutableListOf()

        public fun instances(): List<NMMusicPlayer> = live.toList()

        private fun register(player: NMMusicPlayer) {
            live.add(player)
        }
    }
}
