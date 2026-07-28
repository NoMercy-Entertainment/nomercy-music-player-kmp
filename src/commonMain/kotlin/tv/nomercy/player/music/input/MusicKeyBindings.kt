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

/**
 * The four keys the web music player binds.
 *
 * From `nomercy-music-player/src/plugins/key-handler/index.ts`. Four, against
 * the video player's fifty-three, because a music player is listened to rather
 * than watched and the things a listener reaches for are next, previous,
 * repeat and shuffle.
 *
 * The native music library had none at all.
 */
public enum class MusicKeyAction {
    NEXT,
    PREVIOUS,
    CYCLE_REPEAT,
    TOGGLE_SHUFFLE,
}

public data class MusicKeyBinding(val combo: String, val action: MusicKeyAction)

public val MUSIC_KEY_BINDINGS: List<MusicKeyBinding> = listOf(
    MusicKeyBinding("n", MusicKeyAction.NEXT),
    MusicKeyBinding("p", MusicKeyAction.PREVIOUS),
    MusicKeyBinding("r", MusicKeyAction.CYCLE_REPEAT),
    MusicKeyBinding("s", MusicKeyAction.TOGGLE_SHUFFLE),
)

public val MUSIC_KEY_COMBOS: Set<String> = MUSIC_KEY_BINDINGS.map { it.combo }.toSet()

/**
 * The next repeat state, in the web's order.
 *
 * `OFF -> ALL -> ONE`, which is worth writing down because it is neither
 * alphabetical nor the order most people would reach for. A listener who has
 * learned it presses `r` twice to get "repeat one", and a port that cycled
 * `OFF -> ONE -> ALL` would be wrong in a way that feels like a bug in their
 * memory rather than in the player.
 *
 * The enum happens to declare the same order, which is a coincidence worth not
 * relying on: this function is what the web guarantees, and the enum is free to
 * gain a state.
 */
public fun nextRepeatState(current: RepeatState): RepeatState = when (current) {
    RepeatState.OFF -> RepeatState.ALL
    RepeatState.ALL -> RepeatState.ONE
    RepeatState.ONE -> RepeatState.OFF
}

/** Shuffle is a toggle rather than a cycle. */
public fun nextShuffleState(current: ShuffleState): ShuffleState =
    if (current == ShuffleState.ON) ShuffleState.OFF else ShuffleState.ON
