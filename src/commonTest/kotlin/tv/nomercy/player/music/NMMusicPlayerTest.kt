// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.music

import kotlinx.coroutines.test.runTest
import tv.nomercy.player.core.player.PlayState
import tv.nomercy.player.core.ports.BackendState
import tv.nomercy.player.core.ports.LoadOptions
import tv.nomercy.player.core.ports.MediaBackend
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

private class SilentBackend : MediaBackend {
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

class NMMusicPlayerTest {

    private suspend fun player(): Pair<NMMusicPlayer, SilentBackend> {
        val backend = SilentBackend()
        val subject = NMMusicPlayer(backend)
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
        assertEquals(0.0, subject.crossfadeDuration())
        assertFalse(subject.crossfadeEnabled())
    }

    @Test
    fun aNegativeDurationIsClampedRatherThanTrusted() = runTest {
        val (subject, _) = player()

        subject.crossfadeDuration(-4.0)

        assertEquals(0.0, subject.crossfadeDuration())
    }

    @Test
    fun crossfadingAnnouncesTheStartAndThenTheCompletion() = runTest {
        val (subject, _) = player()
        subject.crossfadeDuration(3.0)
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
        subject.crossfadeDuration(3.0)
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
        subject.crossfadeDuration(8.0)
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
        subject.crossfadeDuration(4.0)
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
        subject.crossfadeDuration(3.0)
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
}
