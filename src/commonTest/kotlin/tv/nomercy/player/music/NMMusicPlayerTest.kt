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
import tv.nomercy.player.core.errors.CoreErrorCodes
import tv.nomercy.player.core.errors.MediaFormatError
import tv.nomercy.player.core.errors.StateError
import kotlin.test.assertFailsWith
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

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
    fun crossfadeArrivesWithSetupAndNotBeforeIt() = runTest {
        // A music player that crossfades is what everyone expects, and the
        // library fills that in at setup. What must not happen is it appearing
        // on a player nobody has set up — a consumer inspecting one before
        // configuring it should see what it actually has, not what it is going
        // to get.
        assertFalse(NMMusicPlayer(SilentBackend()).crossfadeEnabled())

        val (subject, _) = player()

        assertTrue(subject.crossfadeEnabled())
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
    fun aDirectlyBuiltPlayerIsNotAddedToTheSharedRegistry() = runTest {
        // Constructing one directly is deliberate — a preview scrubber, a second
        // engine under test — and a library that added it to a shared registry
        // would hand it to the next caller asking for a player by that name.
        val before = NMMusicPlayer.instances().size

        val (subject, _) = player()

        assertEquals(before, NMMusicPlayer.instances().size)
        assertTrue(!NMMusicPlayer.instances().contains(subject))
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
        // This used to come back as false with a crossfadePrevented reason,
        // alongside the refusals a listener or a zero duration produce. It is
        // not the same kind of answer: those are decisions, and asking an
        // engine that holds one track to hold two is a mistake in the calling
        // code. The web raises core:player/crossfade-unsupported for it, and a
        // consumer that ported a catch found a silent false instead.
        val (subject, backend) = player()
        backend.canCrossfade = false
        subject.configureCrossfade(3.0)
        subject.queue(listOf(Track("a")))
        subject.item("a")

        val raised = assertFailsWith<StateError> { subject.crossfadeTo(Track("b")) }

        assertEquals(CoreErrorCodes.CROSSFADE_UNSUPPORTED, raised.code)
        assertTrue(backend.calls.isEmpty(), "an engine that refused was still driven")
    }

    @Test
    fun aTrackWithNoUrlIsRefusedBeforeAnyFadeStarts() = runTest {
        val (subject, backend) = player()
        subject.configureCrossfade(3.0)
        subject.queue(listOf(Track("a")))
        subject.item("a")

        val raised = assertFailsWith<MediaFormatError> {
            subject.crossfadeTo(Track("b").copy(url = ""))
        }

        assertEquals(CoreErrorCodes.MISSING_URL, raised.code)
        assertTrue(backend.calls.isEmpty(), "the engine was driven with nothing to play")
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
