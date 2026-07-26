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
import tv.nomercy.player.core.ports.LoadOptions
import tv.nomercy.player.core.ports.MediaBackend
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

internal open class SilentBackend : MediaBackend {
    var playCount: Int = 0

    // Recorded rather than discarded. A fake that drops seeks cannot tell a
    // refused one from an accepted one, which is the whole question a
    // before-hook test asks.
    val seekedTo: MutableList<Double> = mutableListOf()
    override suspend fun load(url: String, opts: LoadOptions) = Unit
    override suspend fun play() { playCount += 1 }
    override fun pause() = Unit
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
    override fun on(event: String, fn: (Any?) -> Unit) = Unit
    override fun off(event: String, fn: (Any?) -> Unit) = Unit
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

