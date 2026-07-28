// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.music.input

import tv.nomercy.player.core.player.RepeatState
import tv.nomercy.player.core.player.ShuffleState
import kotlin.test.Test
import kotlin.test.assertEquals

class MusicKeyBindingsTest {

    @Test
    fun theWebsFourKeysAreBound() {
        assertEquals(setOf("n", "p", "r", "s"), MUSIC_KEY_COMBOS)
    }

    // Neither alphabetical nor the order most people reach for. A listener who
    // has learned it presses r twice to get "repeat one", and a port cycling
    // OFF -> ONE -> ALL is wrong in a way that feels like a bug in their memory
    // rather than in the player.
    @Test
    fun repeatCyclesOffAllOne() {
        assertEquals(RepeatState.ALL, nextRepeatState(RepeatState.OFF))
        assertEquals(RepeatState.ONE, nextRepeatState(RepeatState.ALL))
        assertEquals(RepeatState.OFF, nextRepeatState(RepeatState.ONE))
    }

    @Test
    fun twoPressesReachRepeatOne() {
        assertEquals(RepeatState.ONE, nextRepeatState(nextRepeatState(RepeatState.OFF)))
    }

    @Test
    fun threePressesReturnToOff() {
        val afterThree: RepeatState =
            nextRepeatState(nextRepeatState(nextRepeatState(RepeatState.OFF)))

        assertEquals(RepeatState.OFF, afterThree)
    }

    // A toggle, not a cycle. Shuffle has two states and the web treats anything
    // that is not ON as OFF.
    @Test
    fun shuffleToggles() {
        assertEquals(ShuffleState.ON, nextShuffleState(ShuffleState.OFF))
        assertEquals(ShuffleState.OFF, nextShuffleState(ShuffleState.ON))
    }
}
