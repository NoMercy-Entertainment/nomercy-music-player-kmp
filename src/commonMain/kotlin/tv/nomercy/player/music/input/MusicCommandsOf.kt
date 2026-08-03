// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.music.input

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import tv.nomercy.player.core.player.RepeatState
import tv.nomercy.player.core.player.ShuffleState
import tv.nomercy.player.music.NMMusicPlayer

// The key contract, bound to an actual player.
//
// The scope is the caller's, for the reason the video adapter gives: next,
// previous and both state writes are suspending and a key press is not, so
// something has to hold the coroutines, and a binding that made its own scope
// would keep running after the screen it belongs to has gone.
public fun musicCommandsOf(player: NMMusicPlayer, scope: CoroutineScope): MusicCommands =
    PlayerMusicCommands(player, scope)

private class PlayerMusicCommands(
    private val player: NMMusicPlayer,
    private val scope: CoroutineScope,
) : MusicCommands {

    override fun next() {
        scope.launch { player.next() }
    }

    override fun previous() {
        scope.launch { player.previous() }
    }

    // The reads are direct because they are not suspending, and because a key
    // handler asking what the state is and then writing the next one has to see
    // the current answer rather than one a coroutine will deliver later.
    override fun repeatState(): RepeatState = player.repeatState()

    override fun repeatState(value: RepeatState) {
        scope.launch { player.repeatState(value) }
    }

    override fun shuffleState(): ShuffleState = player.shuffleState()

    override fun shuffleState(value: ShuffleState) {
        scope.launch { player.shuffleState(value) }
    }
}
