// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.music

import kotlinx.coroutines.test.runTest
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive
import tv.nomercy.player.core.events.BeforeEvent
import tv.nomercy.player.core.events.CoreEvents
import tv.nomercy.player.core.events.EventKey
import tv.nomercy.player.core.media.PlaylistItem
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@Serializable
private data class ScenarioAction(
    val method: String? = null,
    val args: List<JsonElement> = emptyList(),
    val preventVia: String? = null,
    val backend: String? = null,
)

@Serializable
private data class Scenario(
    val id: String,
    val name: String,
    val medium: String,
    val playlist: List<Map<String, JsonElement>> = emptyList(),
    val actions: List<ScenarioAction> = emptyList(),
    val expect: List<String> = emptyList(),
)

@Serializable
private data class ScenarioFile(
    val contractVersion: String,
    val scenarios: List<Scenario>,
)

// The shared scenarios, driven through the music player.
//
// The same file the core suite and the web harness run. That is the point: a
// scenario passing on web and on core but failing here is a real divergence in
// the music player, not three suites disagreeing about what to measure.
//
// The music player is a core player plus crossfade, so it must honour every
// sequence core does. A crossfade that broke the ordinary transport would be a
// library that is worse than the thing it composes.
class MusicBehavioralConformanceTest {

    private val json = Json { ignoreUnknownKeys = true }

    private fun scenarios(): ScenarioFile {
        val file = File("scenarios/scenarios.json")
        assertTrue(file.exists(), "no vendored scenarios at ${file.absolutePath}")
        return json.decodeFromString(ScenarioFile.serializer(), file.readText())
    }

    // Video-map scenarios name events the music player has no engine for. The
    // ones a music player owns are the transport, queue, mode and lifecycle
    // families, and those are exactly the ones it inherits.
    private fun ownedByMusic(scenario: Scenario): Boolean =
        scenario.medium != "video" && scenario.id !in NOT_A_MUSIC_CONCERN

    @Test
    fun theVendoredScenariosAreTheSameOnesCoreRuns() {
        // Two copies of a file that must not drift. If this ever fails, one repo
        // has been updated and the other has not, and the suites have quietly
        // stopped measuring the same thing.
        val here = File("scenarios/scenarios.json")
        val core = File("../nomercy-player-core-kmp/scenarios/scenarios.json")

        if (!core.exists()) return

        assertEquals(
            core.readText().replace("\r\n", "\n"),
            here.readText().replace("\r\n", "\n"),
            "the vendored scenarios have drifted from core's copy",
        )
    }

    @Test
    fun everyScenarioAMusicPlayerOwnsPasses() = runTest {
        val owned: List<Scenario> = scenarios().scenarios.filter(::ownedByMusic)

        assertTrue(owned.isNotEmpty(), "no scenarios were selected, so this gate measures nothing")

        val failures: List<String> = owned
            .map { runScenario(it) }
            .filterNot { it.second }
            .map { it.first }

        assertEquals(emptyList(), failures)
    }

    private suspend fun runScenario(scenario: Scenario): Pair<String, Boolean> {
        val backend = TwoTrackBackend()
        val player = NMMusicPlayer(backend, backend)
        player.setup()
        player.ready().await()
        if (scenario.playlist.isNotEmpty()) player.queue(playlistOf(scenario))

        val observed: MutableList<String> = mutableListOf()
        player.context.emitter.onAll { name, _ -> observed += name }
        for (name in CoreEvents.all.map { it.name }.filter { it.startsWith("before") }) {
            player.context.emitter.on(EventKey<Any?>(name)) { observed += name }
        }

        for (action in scenario.actions) applyAction(player, backend, scenario, action)

        val missing: Int = firstUnmatched(scenario.expect, observed)
        return "${scenario.id}: expected ${scenario.expect} but saw $observed" to (missing == -1)
    }

    private suspend fun applyAction(
        player: NMMusicPlayer,
        backend: TwoTrackBackend,
        scenario: Scenario,
        action: ScenarioAction,
    ) {
        when {
            action.method == "queue" -> player.queue(playlistOf(scenario))
            action.preventVia != null -> player.context.emitter.on(EventKey<Any?>(action.preventVia)) { event ->
                (event as? BeforeEvent<*>)?.preventDefault()
            }
            action.backend != null -> backend.fire(action.backend)
            else -> applyMethod(player, action)
        }
    }

    @Suppress("CyclomaticComplexMethod")
    private suspend fun applyMethod(player: NMMusicPlayer, action: ScenarioAction) {
        val args = action.args
        when (action.method) {
            "play" -> player.play()
            "pause" -> player.pause()
            "stop" -> player.stop()
            "next" -> player.next()
            "previous" -> player.previous()
            "time" -> player.time(args[0].asDouble())
            "volume" -> player.volume(args[0].asDouble().toInt())
            "mute" -> if (args.isEmpty() || args[0].asBoolean()) player.mute() else player.unmute()
            "playbackRate" -> player.playbackRate(args[0].asDouble())
            else -> throw IllegalArgumentException("scenario calls ${action.method}(), which this player lacks")
        }
    }

    private fun playlistOf(scenario: Scenario): List<PlaylistItem> =
        scenario.playlist.map { entry ->
            Track(
                id = entry["id"]?.jsonPrimitive?.content ?: "item",
                url = entry["url"]?.jsonPrimitive?.content ?: "https://example.test/item",
            )
        }

    private fun JsonElement.asDouble(): Double = (this as JsonPrimitive).content.toDouble()

    private fun JsonElement.asBoolean(): Boolean = (this as JsonPrimitive).content.toBooleanStrict()

    // A subsequence, like the web runner and core's. A scenario says what must
    // happen and in what order, not that nothing else may happen — what else
    // happens legitimately differs between engines.
    private fun firstUnmatched(expected: List<String>, observed: List<String>): Int {
        var cursor = 0
        for (index in expected.indices) {
            val found = observed.subList(cursor, observed.size).indexOf(expected[index])
            if (found == -1) return index
            cursor += found + 1
        }
        return -1
    }

    private companion object {
        // Scenarios about a picture. A music player has no video track, and a
        // gate that ran them would be asserting on events nothing can emit.
        val NOT_A_MUSIC_CONCERN = setOf(
            "lifecycle/video-reports-duration-and-canplay",
            "backend/level-switched",
        )
    }
}
