// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.music


import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import tv.nomercy.player.core.player.PlayState
import tv.nomercy.player.core.ports.BackendState
import tv.nomercy.player.core.ports.CrossfadeCurve
import tv.nomercy.player.core.ports.TransitionBackend
import tv.nomercy.player.core.ports.CanonicalBackendEvent
import tv.nomercy.player.core.ports.LoadOptions
import tv.nomercy.player.core.ports.MediaBackend
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

// A backend that answers on its own event stream, the way a real one does.
//
// It was silent before, and that silence meant every test in this repo drove
// the player without the bridge ever hearing back. The whole difference between
// play and playing lives in that return path — a chrome bound to playing shows
// a spinner forever while audio comes out of the speakers — so a fake that never
// confirmed anything was leaving the most important seam untested.
internal open class SilentBackend : MediaBackend {
    var playCount: Int = 0

    // Recorded rather than discarded. A fake that drops seeks cannot tell a
    // refused one from an accepted one, which is the whole question a
    // before-hook test asks.
    val seekedTo: MutableList<Double> = mutableListOf()

    private val listeners: MutableMap<String, MutableList<(Any?) -> Unit>> = mutableMapOf()

    fun fire(event: String, data: Any? = null) {
        listeners[event]?.toList()?.forEach { it(data) }
    }

    override suspend fun load(url: String, opts: LoadOptions) {
        // What a real engine reports once it has read the container.
        fire(CanonicalBackendEvent.LOAD_START)
        fire(CanonicalBackendEvent.LOADED_METADATA)
        fire(CanonicalBackendEvent.CAN_PLAY)
    }

    override suspend fun play() {
        playCount += 1
        fire(CanonicalBackendEvent.PLAY)
        fire(CanonicalBackendEvent.PLAYING)
    }

    override fun pause() {
        fire(CanonicalBackendEvent.PAUSE)
    }

    override fun stop() = Unit
    override fun currentTime(): Double = 0.0
    override fun currentTime(seconds: Double) {
        seekedTo += seconds
    }
    override fun duration(): Double = 0.0
    override fun volume(): Float = 1.0f
    override fun volume(value: Float) = Unit
    override fun mute() = Unit
    override fun unmute() = Unit
    override fun buffered(): Double = 0.0
    override fun playbackRate(): Double = 1.0
    override fun playbackRate(rate: Double) = Unit
    override fun state(): BackendState = BackendState.IDLE
    override fun on(event: String, fn: (Any?) -> Unit) {
        listeners.getOrPut(event) { mutableListOf() }.add(fn)
    }

    override fun off(event: String, fn: (Any?) -> Unit) {
        listeners[event]?.remove(fn)
    }
}

// An engine that can hold two tracks, recording what the fade asked it to do.
//
// The library's own crossfade is proven in core against the real curve; what
// this checks is the sequence the music player drives — load the next track,
// prime it, then fade — because loading after the fade starts is a transition
// that begins with silence and nothing reports it.
internal class TwoTrackBackend : SilentBackend(), TransitionBackend {
    val calls: MutableList<String> = mutableListOf()
    var fadedForMs: Long = 0
        private set
    var canCrossfade: Boolean = true

    override fun supportsCrossfade(): Boolean = canCrossfade

    override suspend fun loadSecondary(url: String) {
        calls += "loadSecondary:$url"
    }

    override suspend fun primeSecondary(seekMs: Long) {
        calls += "primeSecondary"
    }

    override suspend fun crossfade(durationMs: Long, curve: CrossfadeCurve) {
        calls += "crossfade:$curve"
        fadedForMs = durationMs
    }

    override fun disposeSecondary() {
        calls += "disposeSecondary"
    }

    override fun secondaryGain(): Float = 0f

    override fun secondaryGain(value: Float) = Unit
}

// A backend that stays inside its crossfade until released, so a second call
// arrives while the first is still running — which is the only way to test the
// re-entrancy guard.
// An engine that fails partway through, which real ones do: a device
// disappearing mid-fade takes the secondary buffer with it.
internal class ThrowingFadeBackend : SilentBackend(), TransitionBackend {
    override fun supportsCrossfade(): Boolean = true
    override suspend fun loadSecondary(url: String) = Unit
    override suspend fun primeSecondary(seekMs: Long) = Unit
    override suspend fun crossfade(durationMs: Long, curve: CrossfadeCurve): Unit =
        error("the output device went away")
    override fun disposeSecondary() = Unit
    override fun secondaryGain(): Float = 0f
    override fun secondaryGain(value: Float) = Unit
}

internal class SlowFadeBackend : SilentBackend(), TransitionBackend {
    val gate: CompletableDeferred<Unit> = CompletableDeferred()
    var fades: Int = 0
        private set

    override fun supportsCrossfade(): Boolean = true
    override suspend fun loadSecondary(url: String) = Unit
    override suspend fun primeSecondary(seekMs: Long) = Unit

    override suspend fun crossfade(durationMs: Long, curve: CrossfadeCurve) {
        fades += 1
        gate.await()
    }

    override fun disposeSecondary() = Unit
    override fun secondaryGain(): Float = 0f
    override fun secondaryGain(value: Float) = Unit
}

