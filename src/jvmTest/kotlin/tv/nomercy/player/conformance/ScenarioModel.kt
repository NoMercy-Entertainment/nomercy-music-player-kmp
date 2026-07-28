// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.conformance

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonPrimitive
import java.io.File

// A scenario, as the web harness wrote it down.
//
// The half of the conformance engine that belongs to no repository: what a
// scenario file says, and how two event orders are compared. Driving a player
// with it is per-repo — a video item is not a music item and a fake backend is
// per-engine — and that half stays where the player is.
//
// The split matters because the alternative is a copy in each of three repos,
// and three copies of a comparison are three answers to "did this port
// diverge".

@Serializable
public data class ScenarioAction(
    val method: String? = null,
    val args: List<JsonElement> = emptyList(),
    val preventVia: String? = null,
    val backend: String? = null,
)

@Serializable
public data class Scenario(
    val id: String,
    val name: String,
    val medium: String,
    val playlist: List<Map<String, JsonElement>> = emptyList(),
    val actions: List<ScenarioAction> = emptyList(),
    val expect: List<String> = emptyList(),
)

@Serializable
public data class ScenarioFile(
    val contractVersion: String,
    val scenarios: List<Scenario>,
)

public data class ScenarioResult(
    val id: String,
    val ok: Boolean,
    val expected: List<String>,
    val observed: List<String>,
    val reason: String? = null,
)

private val json = Json { ignoreUnknownKeys = true }

public fun loadScenarios(path: String = "scenarios/scenarios.json"): ScenarioFile {
    val file = File(path)
    check(file.exists()) { "no vendored scenarios at ${file.absolutePath} — run tools/sync-conformance.sh" }
    return json.decodeFromString(ScenarioFile.serializer(), file.readText())
}

// What a scenario's playlist entry says, before anybody turns it into an item.
//
// The kit stops here on purpose: each repo has its own item type, and building
// one is the repo's job while reading the scenario is the kit's.
public data class ScenarioItem(
    val id: String,
    val url: String,
    val title: String?,
)

public fun scenarioItems(scenario: Scenario): List<ScenarioItem> =
    scenario.playlist.map { entry ->
        ScenarioItem(
            id = entry["id"]?.jsonPrimitive?.content ?: "item",
            url = entry["url"]?.jsonPrimitive?.content ?: "https://example.test/item",
            title = entry["title"]?.jsonPrimitive?.content,
        )
    }

// Where an observed order first stops matching an expected one.
//
// A subsequence rather than an equality: a port firing an extra event the
// scenario does not name is not a divergence, and a port firing the named ones
// out of order is. Returns the index of the first expectation that never
// arrived, or -1 when they all did.
public fun firstUnmatched(expected: List<String>, observed: List<String>): Int {
    var cursor = 0
    for (index in expected.indices) {
        if (cursor > observed.size) return index
        val found = observed.subList(cursor, observed.size).indexOf(expected[index])
        if (found == -1) return index
        cursor += found + 1
    }
    return -1
}
