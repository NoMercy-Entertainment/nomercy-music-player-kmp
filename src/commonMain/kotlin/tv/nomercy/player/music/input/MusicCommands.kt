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

// Everything a music key press can ask the player to do.
//
// A contract rather than the player, for the reason the video side gives: a key
// handler holding a player could reach anything on it, and what a keyboard is
// allowed to do is deliberately smaller. Not split five ways like the video
// one, because there are four bindings and one interface of four methods is not
// a checklist.
//
// Repeat and shuffle are read AND written here, rather than exposed as a
// cycleRepeat() the host implements. The order OFF to ALL to ONE is the thing
// that must not drift between clients, so it stays in the library, in
// [nextRepeatState], and a host supplies only the state it has.
public interface MusicCommands {

    public fun next()

    public fun previous()

    public fun repeatState(): RepeatState

    public fun repeatState(value: RepeatState)

    public fun shuffleState(): ShuffleState

    public fun shuffleState(value: ShuffleState)
}
