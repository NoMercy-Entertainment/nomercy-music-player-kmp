// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.music

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

// The registry against the contract it mirrors.
//
// A hand-written registry drifts the moment the contract moves, and the symptom
// is a listener that silently never fires. This turns that into a set
// difference with the names printed.
//
// JVM-only: reading a file is trivial here and pointless to make work on six
// other targets for the same answer.
class MusicEventsRegistryTest {

    private fun contractNames(map: String): Set<String> {
        // Vendored so this repo runs standalone in CI, where the generator's
        // output does not exist.
        val file = File("contract/contract.json")
        assertTrue(file.exists(), "no vendored contract at ${file.absolutePath}")

        val root = Json.parseToJsonElement(file.readText()).jsonObject
        return root.getValue("events").jsonArray
            .map { it.jsonObject }
            .filter { it["map"]?.jsonPrimitive?.content == map }
            .map { it.getValue("name").jsonPrimitive.content }
            .toSet()
    }

    @Test
    fun theRegistryCoversExactlyTheContractsMusicEvents() {
        val declared: Set<String> = MusicEvents.all.map { it.name }.toSet()
        val contract: Set<String> = contractNames("music")

        assertEquals(
            emptySet(),
            contract - declared,
            "the contract has music events the registry does not: a listener for one would never fire",
        )
        assertEquals(
            emptySet(),
            declared - contract,
            "the registry has events the contract does not: nothing on the other ecosystems emits them",
        )
    }

    @Test
    fun theRegistryIsNotEmpty() {
        assertTrue(MusicEvents.all.isNotEmpty())
    }
}
