// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.conformance

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import kotlin.test.assertTrue

// The vendored contract, read once.
//
// Three suites had their own copy of this — the same path, the same existence
// assertion, the same parse — which is three places to update when the file
// moves and three chances for one of them to keep passing against a contract
// that is no longer there.
//
// It lives in the kit rather than beside the tests because video and music read
// the same file for the same reason, and a third and fourth copy is how the
// three repos come to disagree about what the contract says.
public object ContractFixture {

    // Relative on purpose. Every repo vendors the file to the same place, so a
    // path that resolved against one checkout would be a path the other two
    // cannot use.
    public const val PATH: String = "contract/contract.json"

    public fun read(path: String = PATH): JsonObject {
        val file = File(path)
        // Named with its absolute path, because "no vendored contract" from a
        // task whose working directory is not what you assumed is a confusing
        // half-hour and the path ends it.
        assertTrue(file.exists(), "no vendored contract at ${file.absolutePath} — run tools/sync-conformance.sh")

        return Json.parseToJsonElement(file.readText()).jsonObject
    }

    // Every method the contract says a given player exposes.
    public fun methods(player: String, path: String = PATH): Set<String> =
        read(path).getValue("methods").jsonArray
            .map { it.jsonObject }
            .filter { it["player"]?.jsonPrimitive?.content == player }
            .map { it.getValue("name").jsonPrimitive.content }
            .toSet()

    // What the two players share. Derived rather than declared: the contract
    // tags each method with the player that exposes it, and a method on both is
    // one they inherit.
    public fun baseMethods(path: String = PATH): Set<String> =
        methods("video", path) intersect methods("music", path)

    // Plain strings in the contract, not objects. Read as objects this threw a
    // parse error that reads like a corrupt file rather than a wrong assumption.
    public fun errorCodes(path: String = PATH): Set<String> =
        read(path).getValue("errors").jsonArray
            .map { it.jsonPrimitive.content }
            .toSet()

    // Every event in one of the contract's maps.
    //
    // The map is required rather than defaulted. Beside an overload that took
    // only a path, eventNames("base") bound to the path one and reported a
    // missing file called "base" — a wrong answer dressed as a setup problem.
    //
    // The map matters: base is what core owns, and video and music each own
    // theirs. A gate comparing a library against the whole contract would
    // demand that core declare a subtitle event and that video declare a
    // now-playing one, which is not a hole — it is the boundary working.
    public fun eventNames(map: String, path: String = PATH): Set<String> =
        read(path).getValue("events").jsonArray
            .map { it.jsonObject }
            .filter { it.getValue("map").jsonPrimitive.content == map }
            .map { it.getValue("name").jsonPrimitive.content }
            .toSet()

    public fun version(path: String = PATH): String =
        read(path).getValue("version").jsonPrimitive.content
}
