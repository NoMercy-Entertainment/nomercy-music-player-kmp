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

private open class SilentBackend : MediaBackend {
    var playCount: Int = 0
    override suspend fun load(url: String, opts: LoadOptions) = Unit
    override suspend fun play() { playCount += 1 }
    override fun pause() = Unit
    override fun stop() = Unit
    override fun currentTime(): Double = 0.0
    override fun currentTime(seconds: Double) = Unit
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
private class TwoTrackBackend : SilentBackend(), TransitionBackend {
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
private class ThrowingFadeBackend : SilentBackend(), TransitionBackend {
    override fun supportsCrossfade(): Boolean = true
    override suspend fun loadSecondary(url: String) = Unit
    override suspend fun primeSecondary(seekMs: Long) = Unit
    override suspend fun crossfade(durationMs: Long, curve: CrossfadeCurve): Unit =
        error("the output device went away")
    override fun disposeSecondary() = Unit
    override fun secondaryGain(): Float = 0f
    override fun secondaryGain(value: Float) = Unit
}

private class SlowFadeBackend : SilentBackend(), TransitionBackend {
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

class NMMusicPlayerTest {

    @Test
    fun aSecondCrossfadeWhileOneIsRunningIsRefused() = runTest {
        // Two overlapping crossfades drive the same gain and the loser wins: one
        // ends by setting the outgoing track to silence while the other is still
        // fading it up, so a track vanishes mid-song.
        val backend = SlowFadeBackend()
        val subject = NMMusicPlayer(backend, backend)
        subject.setup()
        subject.ready().await()
        subject.configureCrossfade(3.0)
        subject.queue(listOf(Track("a"), Track("b"), Track("c")))
        subject.play()

        val first = async { subject.crossfadeTo(Track("b")) }
        runCurrent()

        assertTrue(subject.isTransitioning(), "the player did not report the crossfade it was running")
        assertFalse(subject.crossfadeTo(Track("c")), "a stacked crossfade was accepted")

        backend.gate.complete(Unit)
        assertTrue(first.await())
        assertEquals(1, backend.fades, "the refused crossfade reached the engine anyway")
        assertFalse(subject.isTransitioning())
    }

    @Test
    fun anEngineThatThrowsMidFadeDoesNotStrandThePlayer() = runTest {
        // Left set, every crossfade for the rest of the session is refused —
        // and nothing says why, because the refusal looks exactly like a
        // listener declining one.
        val backend = ThrowingFadeBackend()
        val subject = NMMusicPlayer(backend, backend)
        subject.setup()
        subject.ready().await()
        subject.configureCrossfade(3.0)
        subject.queue(listOf(Track("a"), Track("b")))
        subject.play()

        assertFails { subject.crossfadeTo(Track("b")) }

        assertFalse(subject.isTransitioning(), "a failed crossfade left the player transitioning forever")
    }


    private suspend fun player(): Pair<NMMusicPlayer, TwoTrackBackend> {
        val backend = TwoTrackBackend()
        val subject = NMMusicPlayer(backend, backend)
        subject.setup()
        subject.ready().await()
        return subject to backend
    }

    @Test
    fun itIsACorePlayerBeforeItIsAMusicOne() = runTest {
        val (subject, backend) = player()
        subject.queue(listOf(Track("a"), Track("b")))

        subject.play()
        subject.next()

        assertEquals(PlayState.PLAYING, subject.state().playState)
        assertEquals("b", subject.item()?.id)
        assertTrue(backend.playCount >= 1)
    }

    @Test
    fun crossfadeIsOffUntilSomebodyAsksForIt() = runTest {
        val (subject, _) = player()

        // Crossfading by surprise is worse than not crossfading.
        assertFalse(subject.crossfadeEnabled())
    }

    @Test
    fun aNegativeDurationIsClampedRatherThanTrusted() = runTest {
        // Read through the behaviour rather than a getter: a negative duration
        // reaching the engine is the failure, and a clamped one refuses the
        // crossfade for the same reason zero does.
        val (subject, _) = player()

        subject.configureCrossfade(-4.0)

        assertFalse(subject.crossfadeEnabled())
        assertFalse(subject.crossfadeTo(Track("b")))
    }

    @Test
    fun crossfadingAnnouncesTheStartAndThenTheCompletion() = runTest {
        val (subject, _) = player()
        subject.configureCrossfade(3.0)
        subject.queue(listOf(Track("a"), Track("b")))
        subject.play()
        val seen = mutableListOf<String>()
        subject.on(MusicEvents.CrossfadeStart) { seen += "start:${it.from?.id}->${it.to.id}" }
        subject.on(MusicEvents.CrossfadeComplete) { seen += "complete:${it.item.id}" }

        val ran = subject.crossfadeTo(Track("b"))

        assertTrue(ran)
        assertEquals(listOf("start:a->b", "complete:b"), seen)
    }

    @Test
    fun aRefusedCrossfadeIsNotAFailureAndSaysWhy() = runTest {
        val (subject, _) = player()
        subject.configureCrossfade(3.0)
        subject.queue(listOf(Track("a")))
        subject.play()
        subject.on(MusicEvents.BeforeCrossfade) { it.preventDefault() }
        var reason: String? = null
        subject.on(MusicEvents.CrossfadePrevented) { reason = it.reason }
        var started = 0
        subject.on(MusicEvents.CrossfadeStart) { started += 1 }

        val ran = subject.crossfadeTo(Track("b"))

        // A refusal means something knew better — a gapless album, say — so the
        // caller plays the next track the ordinary way.
        assertFalse(ran)
        assertEquals("listener-prevented", reason)
        assertEquals(0, started)
    }

    @Test
    fun aListenerCanShortenACrossfadeRatherThanOnlyRefusingIt() = runTest {
        val (subject, _) = player()
        subject.configureCrossfade(8.0)
        subject.queue(listOf(Track("a")))
        subject.play()
        subject.on(MusicEvents.BeforeCrossfade) { it.data = it.data.copy(duration = 1.5) }
        var announced: Double? = null
        subject.on(MusicEvents.CrossfadeStart) { announced = it.duration }

        subject.crossfadeTo(Track("b"))

        assertEquals(1.5, announced)
    }

    @Test
    fun shorteningACrossfadeToNothingIsARefusalSpelledDifferently() = runTest {
        val (subject, _) = player()
        subject.configureCrossfade(4.0)
        subject.queue(listOf(Track("a")))
        subject.play()
        subject.on(MusicEvents.BeforeCrossfade) { it.data = it.data.copy(duration = 0.0) }
        var reason: String? = null
        subject.on(MusicEvents.CrossfadePrevented) { reason = it.reason }

        val ran = subject.crossfadeTo(Track("b"))

        assertFalse(ran)
        assertEquals("zero-duration", reason)
    }

    @Test
    fun theFirstTrackOfASessionCrossfadesFromNothing() = runTest {
        val (subject, _) = player()
        subject.configureCrossfade(3.0)
        var from: Any? = "unset"
        subject.on(MusicEvents.CrossfadeStart) { from = it.from }

        subject.crossfadeTo(Track("a"))

        assertNull(from)
    }

    @Test
    fun aBackendSwapIsAnnouncedSoAConsumerCanTellWhichEngineIsPlaying() = runTest {
        val (subject, _) = player()
        var kind: String? = null
        subject.on(MusicEvents.BackendChanged) { kind = it.kind }

        subject.announceBackend("gapless")

        assertEquals("gapless", kind)
    }

    @Test
    fun everyPlayerBuiltIsFindableWithoutThreadingAReference() = runTest {
        val before = NMMusicPlayer.instances().size

        val (subject, _) = player()

        assertEquals(before + 1, NMMusicPlayer.instances().size)
        assertTrue(NMMusicPlayer.instances().contains(subject))
    }

    @Test
    fun theNextTrackIsLoadedAndPrimedBeforeTheFadeStarts() = runTest {
        // The order is the whole job. Loading after the fade begins is a
        // transition that starts with silence, and nothing reports it — the
        // events fire in the right order either way.
        val (subject, backend) = player()
        subject.configureCrossfade(3.0)
        subject.queue(listOf(Track("a")))
        subject.item("a")

        val ran: Boolean = subject.crossfadeTo(Track("b"))

        assertTrue(ran)
        assertEquals(
            listOf("loadSecondary:${Track("b").url}", "primeSecondary", "crossfade:EQUAL_POWER"),
            backend.calls,
        )
    }

    @Test
    fun theFadeRunsForTheDurationTheListenerAgreedTo() = runTest {
        // A listener may shorten it. The engine has to be told the resolved
        // duration, not the configured one, or the hook is decorative.
        val (subject, backend) = player()
        subject.configureCrossfade(4.0)
        subject.queue(listOf(Track("a")))
        subject.item("a")
        subject.on(MusicEvents.BeforeCrossfade) { it.data = it.data.copy(duration = 1.5) }

        subject.crossfadeTo(Track("b"))

        assertEquals(1_500L, backend.fadedForMs)
    }

    @Test
    fun anEngineThatCannotCrossfadeSaysSoRatherThanPretending() = runTest {
        // Emitting a start and a completion with nothing between them is what
        // this did before the transition seam existed, and it looked exactly
        // like a working crossfade.
        val (subject, backend) = player()
        backend.canCrossfade = false
        subject.configureCrossfade(3.0)
        subject.queue(listOf(Track("a")))
        subject.item("a")
        var refusal: String? = null
        subject.on(MusicEvents.CrossfadePrevented) { refusal = it.reason }

        val ran: Boolean = subject.crossfadeTo(Track("b"))

        assertFalse(ran)
        assertEquals("backend-cannot-crossfade", refusal)
        assertTrue(backend.calls.isEmpty(), "an engine that refused was still driven")
    }

    @Test
    fun aPlayerWithNoTransitionBackendRefusesRatherThanThrowing() = runTest {
        // An ordinary MediaBackend cannot hold two tracks. A music player built
        // on one still works; it just does not crossfade.
        val subject = NMMusicPlayer(SilentBackend())
        subject.setup()
        subject.ready().await()
        subject.configureCrossfade(3.0)
        var refusal: String? = null
        subject.on(MusicEvents.CrossfadePrevented) { refusal = it.reason }

        val ran: Boolean = subject.crossfadeTo(Track("b"))

        assertFalse(ran)
        assertEquals("no-transition-backend", refusal)
    }
}
