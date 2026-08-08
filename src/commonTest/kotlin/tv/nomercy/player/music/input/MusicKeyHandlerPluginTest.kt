// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.music.input

import tv.nomercy.player.core.input.KeyCombo
import tv.nomercy.player.core.player.RepeatState
import tv.nomercy.player.core.player.ShuffleState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private class RecordingMusicCommands(
    var repeat: RepeatState = RepeatState.OFF,
    var shuffle: ShuffleState = ShuffleState.OFF,
) : MusicCommands {

    val calls: MutableList<String> = mutableListOf()

    override fun next() {
        calls += "next"
    }

    override fun previous() {
        calls += "previous"
    }

    override fun repeatState(): RepeatState = repeat

    override fun repeatState(value: RepeatState) {
        repeat = value
        calls += "repeat:$value"
    }

    override fun shuffleState(): ShuffleState = shuffle

    override fun shuffleState(value: ShuffleState) {
        shuffle = value
        calls += "shuffle:$value"
    }
}

// MUSIC_KEY_BINDINGS and the two cycle functions were here and unit-tested
// before this existed, and no key press could reach any of them. These press
// keys.
class MusicKeyHandlerPluginTest {

    /**
     * A clock that moves, because the binding table has a cooldown.
     *
     * It was `{ 0L }`, which froze time inside [KeyBindingTable]'s 300ms
     * key-repeat cooldown: the second press of the SAME key was swallowed as a
     * held key, so `r` pressed twice cycled repeat once and the two cases that
     * press a key twice failed. The cooldown is right — it is what stops a held
     * key running the queue off the end — and a listener pressing r twice does
     * it seconds apart, not in the same millisecond.
     */
    private fun handler(commands: MusicCommands): MusicKeyHandlerPlugin {
        var now = 0L
        val plugin = MusicKeyHandlerPlugin(commands, nowMs = { now += BETWEEN_PRESSES_MS; now })
        plugin.use()
        return plugin
    }

    @Test
    fun nAndPMoveTheQueue() {
        val commands = RecordingMusicCommands()
        val plugin = handler(commands)

        assertTrue(plugin.handle(KeyCombo("n")), "n was not bound")
        assertTrue(plugin.handle(KeyCombo("p")), "p was not bound")

        assertEquals(listOf("next", "previous"), commands.calls.toList())
    }

    @Test
    fun rCyclesRepeatInTheWebsOrderRatherThanTheEnums() {
        // OFF to ALL to ONE. A listener who has learned it presses r twice for
        // "repeat one", and a port that cycled OFF to ONE to ALL would be wrong
        // in a way that feels like a fault in their memory rather than the
        // player's.
        val commands = RecordingMusicCommands()
        val plugin = handler(commands)

        plugin.handle(KeyCombo("r"))
        assertEquals(RepeatState.ALL, commands.repeat)

        plugin.handle(KeyCombo("r"))
        assertEquals(RepeatState.ONE, commands.repeat)

        plugin.handle(KeyCombo("r"))
        assertEquals(RepeatState.OFF, commands.repeat, "the cycle did not come back round")
    }

    @Test
    fun sTogglesShuffleBothWays() {
        val commands = RecordingMusicCommands()
        val plugin = handler(commands)

        plugin.handle(KeyCombo("s"))
        assertEquals(ShuffleState.ON, commands.shuffle)

        plugin.handle(KeyCombo("s"))
        assertEquals(ShuffleState.OFF, commands.shuffle, "shuffle only went one way")
    }

    @Test
    fun aKeyThisHandlerDoesNotOwnIsLeftForThePlatform() {
        // handle() answering true for an unbound key is how a device stops
        // responding to its own back button.
        val commands = RecordingMusicCommands()
        val plugin = handler(commands)

        assertFalse(plugin.handle(KeyCombo("q")))
        assertEquals(emptyList(), commands.calls.toList())
    }

    @Test
    fun theIdIsTheWebsSoConsumerCodeCarriesOver() {
        assertEquals("key-handler", MusicKeyHandlerPlugin.Manifest.id)
    }

    private companion object {
        // Past the binding table's 300ms cooldown, so each press is a press.
        const val BETWEEN_PRESSES_MS = 500L
    }
}
