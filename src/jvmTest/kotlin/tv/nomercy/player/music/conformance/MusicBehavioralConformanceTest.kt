// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.music.conformance

import kotlinx.coroutines.test.runTest
import tv.nomercy.player.conformance.Scenario
import tv.nomercy.player.conformance.ScenarioAction
import tv.nomercy.player.conformance.firstUnmatched
import tv.nomercy.player.conformance.loadScenarios
import tv.nomercy.player.conformance.scenarioItems
import tv.nomercy.player.core.events.BeforeEvent
import tv.nomercy.player.core.events.CoreEvents
import tv.nomercy.player.core.events.EventKey
import tv.nomercy.player.music.TwoTrackBackend
import tv.nomercy.player.music.NMMusicPlayer
import tv.nomercy.player.music.Track
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

// The music player against the same scenarios the web harness runs.
//
// The shape gate says the methods exist. This says they do the same thing in the
// same order, which is the only claim that makes "a native port of the web
// library" mean anything. A name that exists and emits nothing passes the first
// gate and fails a viewer.
class MusicBehavioralConformanceTest {

    private val file = loadScenarios()

    // Scenarios naming a capability this library does not own yet. Listed by id
    // rather than skipped by a rule, because a "skip what fails" rule grows
    // silently and a list can only be shortened.
    // Scenarios naming a capability this library does not own yet. Listed by id
    // rather than skipped by a rule, because a "skip what fails" rule grows
    // silently and a list can only be shortened.
    private val notYetOwned = setOf<String>()

    private fun relevant(): List<Scenario> =
        file.scenarios.filter { it.medium == "both" }
            .filterNot { it.id in notYetOwned }

    private class Capture(player: NMMusicPlayer) {
        private val order: MutableList<String> = mutableListOf()

        init {
            player.context.emitter.onAll { name, _ -> order.add(name) }

            // The firehose is fed by emit() alone and the before-dispatch does
            // not go through it, so without these the entire cancellable seam is
            // invisible — every scenario naming a beforeX fails with the event
            // simply absent. Core's capture says so in a comment and this test
            // was written without it, which is how the comment was earned twice.
            for (name in CoreEvents.all.map { it.name }.filter { it.startsWith("before") }) {
                player.context.emitter.on(EventKey<Any?>(name)) { order.add(name) }
            }
        }

        fun seen(): List<String> = order.toList()
    }

    // The scenario being driven, so a queue action can re-read its playlist.
    private lateinit var current: Scenario

    private suspend fun drive(player: NMMusicPlayer, backend: TwoTrackBackend, action: ScenarioAction) {
        // A cancellation is registered, not called: the scenario says "this next
        // command will be prevented", so the listener has to be in place before
        // the command runs. Ignored, every prevented scenario reports the
        // command succeeding, which is the opposite of what it asserts.
        if (action.preventVia != null) {
            player.context.emitter.on(EventKey<Any?>(action.preventVia!!)) { event ->
                (event as? BeforeEvent<*>)?.preventDefault()
            }
            return
        }

        // What the engine did on its own. A scenario about the film ending is
        // about an event the backend raises, not a method anybody called.
        if (action.backend != null) {
            // Bare, unlike video's. No music scenario carries a payload with a
            // backend event, and a stub that always returned null would be a
            // seam suggesting one exists.
            backend.fire(action.backend!!)
            return
        }

        when (action.method) {
            null -> Unit
            "play" -> player.play()
            "pause" -> player.pause()
            "stop" -> player.stop()
            "next" -> player.next()
            "previous" -> player.previous()
            "time" -> player.time(action.args.firstOrNull()?.toString()?.trim('"')?.toDouble() ?: 0.0)
            "volume" -> player.volume(action.args.firstOrNull()?.toString()?.trim('"')?.toDouble()?.toInt() ?: 0)
            "mute" -> player.mute()
            "playbackRate" -> player.playbackRate(action.args.firstOrNull()?.toString()?.trim('"')?.toDouble() ?: 1.0)
            // Re-queued, not ignored. The scenario is about replacing what is
            // loaded, and a driver that treated it as already-done would assert
            // an event nothing emitted.
            "queue" -> player.queue(
                scenarioItems(current).map { Track(id = it.id, url = it.url, title = it.title) },
            )
            // Never silently skipped. A verb nobody implemented that passes by
            // doing nothing is a scenario asserting an event order produced by
            // an empty run.
            else -> error("scenario verb '${action.method}' is not driven here")
        }
    }

    private suspend fun run(scenario: Scenario): List<String> {
        current = scenario

        val backend = TwoTrackBackend()
        val player = NMMusicPlayer(backend, backend)
        player.setup()
        player.ready().await()

        val items = scenarioItems(scenario).map { Track(id = it.id, url = it.url, title = it.title) }
        if (items.isNotEmpty()) player.queue(items)

        // After the queue, exactly as core does it. Subscribing earlier would
        // record the setup traffic every scenario shares and none of them names,
        // and the comparison is a subsequence so it would pass anyway — which is
        // the kind of noise that hides a real ordering change.
        val capture = Capture(player)
        for (action in scenario.actions) {
            drive(player, backend, action)
        }

        return capture.seen()
    }

    @Test
    fun everyScenarioProducesTheCanonicalEventOrder() = runTest {
        val failures: MutableList<String> = mutableListOf()

        for (scenario in relevant()) {
            val observed = run(scenario)
            val at = firstUnmatched(scenario.expect, observed)
            if (at != -1) {
                failures += "${scenario.id}: expected '${scenario.expect[at]}' after " +
                    scenario.expect.take(at).joinToString(", ") + " — observed ${observed.joinToString(", ")}"
            }
        }

        assertTrue(failures.isEmpty(), failures.joinToString("\n"))
    }

    @Test
    fun theComparisonRejectsAReorderedExpectation() {
        // The runner has to be seen failing. A comparison that accepts any order
        // is a green tick over a port that emits everything backwards.
        val one = "one"
        val two = "two"
        val three = "three"

        assertEquals(-1, firstUnmatched(listOf(one, two, three), listOf(one, "extra", two, three)))
        assertTrue(firstUnmatched(listOf(two, one), listOf(one, two)) != -1)
    }

    @Test
    fun thereAreScenariosToRun() {
        // A filter that matched nothing would pass forever.
        assertTrue(relevant().size >= 10, "only ${relevant().size} scenario(s) reached the music player")
    }
}
