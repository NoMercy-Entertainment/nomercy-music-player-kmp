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
import kotlin.reflect.KVisibility
import kotlin.reflect.full.memberFunctions
import kotlin.reflect.full.memberProperties
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

// Methods the web music player has because the web has a DOM, or because
// JavaScript needs a workaround Kotlin does not.
private val WEB_ONLY_METHODS = setOf(
    "addClasses",
    "removeClasses",
    "container",
    "createButton",
    "createElement",
    "createSVG",
    // The Web Audio graph handle. There is no AudioContext off the web; the
    // equivalent here is the audio backend, which the player already exposes.
    "audioContext",
    // A runtime method-override table. The web needs one because a
    // mixin-composed player cannot be subclassed; every member here is already
    // open, so a consumer replacing one behaviour overrides one method.
    "experimental",
)

// Music-specific methods this library has not reached.
//
// Written out rather than derived, for the same reason core's was: a set
// difference that quietly got smaller for the wrong reason looks like progress.
//
// Empty. The first version of this list was six names I guessed at, and the
// gate's own "excused but not in the contract" check rejected every one of them
// — which is exactly what that check is for.
private val NOT_YET_PORTED_METHODS: Set<String> = emptySet()

// The music player's own surface against the contract's.
//
// Core already proves the shared surface; this asks the narrower question this
// repo is responsible for: does the music player carry the methods the contract
// tags as music, and does it invent any the contract does not name.
//
// A method core provides counts. The music player is a core player plus
// crossfade, so inheriting a method is having it — a consumer reading a name in
// the docs finds it on the object either way.
class MusicSurfaceConformanceTest {

    private fun contractMethods(player: String): Set<String> {
        val file = File("contract/contract.json")
        assertTrue(file.exists(), "no vendored contract at ${file.absolutePath}")

        return Json.parseToJsonElement(file.readText()).jsonObject
            .getValue("methods").jsonArray
            .map { it.jsonObject }
            .filter { it["player"]?.jsonPrimitive?.content == player }
            .map { it.getValue("name").jsonPrimitive.content }
            .toSet()
    }

    private fun musicMethods(): Set<String> = contractMethods(MUSIC)

    private fun videoMethods(): Set<String> = contractMethods(VIDEO)

    private fun playerSurface(): Set<String> {
        val functions: List<String> = NMMusicPlayer::class.memberFunctions
            .filter { it.visibility == KVisibility.PUBLIC }
            .map { it.name }
        val properties: List<String> = NMMusicPlayer::class.memberProperties
            .filter { it.visibility == KVisibility.PUBLIC }
            .map { it.name }

        return (functions + properties).toSet() - OBJECT_METHODS
    }

    @Test
    fun theContractNamesAMusicSurfaceRatherThanNothing() {
        // If the extractor ever stops tagging music methods this gate would pass
        // by comparing an empty set against everything.
        val music: Set<String> = musicMethods()

        assertTrue(music.size > MINIMUM_MUSIC_SURFACE, "only ${music.size} methods are tagged music")
        assertTrue(music.contains("crossfadeTo"), "the music surface has no crossfade")
    }

    @Test
    fun theMusicPlayerInventsNoMethodTheContractDoesNotHave() {
        val invented: Set<String> = playerSurface() - musicMethods() - videoMethods() - NATIVE_ONLY

        assertEquals(emptySet(), invented, "the music player exposes methods the contract does not name")
    }

    @Test
    fun everyMusicMethodTheLibraryLacksHasAStatedReason() {
        val missing: Set<String> = musicMethods() - playerSurface()
        val accounted: Set<String> = WEB_ONLY_METHODS + NOT_YET_PORTED_METHODS

        assertEquals(
            emptySet(),
            missing - accounted,
            "contract music methods this library neither has nor accounts for: each needs a reason, not a blank",
        )
    }

    @Test
    fun theReasonListsDoNotOutliveTheMethodsTheyExcuse() {
        val accounted: Set<String> = WEB_ONLY_METHODS + NOT_YET_PORTED_METHODS

        assertEquals(
            emptySet(),
            accounted intersect playerSurface(),
            "these methods exist on the music player and are still excused as missing",
        )
        assertEquals(
            emptySet(),
            accounted - musicMethods(),
            "these methods are excused but are not in the contract's music surface",
        )
    }

    @Test
    fun crossfadeIsTheMusicDomainAndItIsHere() {
        // The one thing this library exists to add. If it ever leaves the
        // surface, the repo has no reason to be separate from core.
        assertTrue(playerSurface().contains("crossfadeTo"))
        assertTrue(playerSurface().contains("isTransitioning"), "nothing guards a crossfade from stacking")
    }

    private companion object {
        const val MUSIC = "music"
        const val VIDEO = "video"
        const val MINIMUM_MUSIC_SURFACE = 100

        val OBJECT_METHODS = setOf("equals", "hashCode", "toString")

        // What native has that the web needs no name for: the controllers the
        // core player is composed of, and the Kotlin-shaped state surface.
        val NATIVE_ONLY = setOf(
            "context", "transport", "volume", "time", "state", "lifecycle", "bridge", "activity",
            "cueParsers", "streamFactories", "metrics", "bandwidth", "plugins", "queue",
            "stateFlow", "rootLogger", "rootStorage", "emit", "on", "onAll", "once", "off",
            "dispatchBefore", "fetch", "websocket", "report", "aspectRatio", "formatTitle",
            "pluginList", "contributions", "coreVersion", "playerId",
            // Music-specific and native-shaped: announceBackend tells listeners
            // which engine took over, which the web infers from the DOM.
            "announceBackend", "crossfadeEnabled", "configureCrossfade",
        )
    }
}
