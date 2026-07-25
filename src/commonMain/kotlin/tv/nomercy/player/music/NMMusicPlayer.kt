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
import tv.nomercy.player.core.ports.MediaBackend

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
) : ComposedPlayer(backend) {

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
        val from: PlaylistItem? = item()
        val outcome = dispatchBefore(
            MusicEvents.BeforeCrossfade,
            Crossfade(from = from, to = next, duration = crossfadeSeconds),
        )

        if (outcome.prevented) {
            emit(MusicEvents.CrossfadePrevented, CrossfadePrevented(outcome.reason))
            return false
        }

        // The listener may have shortened it to nothing, which is a refusal
        // spelled differently and deserves the same answer.
        val resolved: Crossfade = outcome.data
        if (resolved.duration <= 0.0) {
            emit(MusicEvents.CrossfadePrevented, CrossfadePrevented("zero-duration"))
            return false
        }

        emit(MusicEvents.CrossfadeStart, resolved)
        emit(MusicEvents.CrossfadeComplete, CrossfadeComplete(resolved.to))
        return true
    }

    public open fun announceBackend(kind: String) {
        emit(MusicEvents.BackendChanged, AudioBackendChange(kind))
    }

    public companion object {
        private val live: MutableList<NMMusicPlayer> = mutableListOf()

        public fun instances(): List<NMMusicPlayer> = live.toList()

        private fun register(player: NMMusicPlayer) {
            live.add(player)
        }
    }
}
