// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.music.ui

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import tv.nomercy.player.core.player.RepeatState
import tv.nomercy.player.core.player.ShuffleState
import tv.nomercy.player.music.NMMusicPlayer

// What the music chrome does.
//
// A seam rather than the player, so a row of buttons can be driven from a
// recorder in a test and so a host embedding the row in something larger can
// route the same presses at whatever it already has.
public interface MusicCommands {

    // Explicit rather than a toggle. A row drawn from state that has since
    // changed would toggle to the wrong thing, and the listener pressed the
    // button they could see.
    public fun setPlaying(playing: Boolean)

    public fun next()

    public fun previous()

    // Absolute, in seconds. A progress line knows where it was dropped, and a
    // delta computed from a position that has moved since would land elsewhere.
    public fun seekTo(seconds: Double)

    public fun setVolume(percent: Int)

    public fun setMuted(muted: Boolean)

    public fun setShuffled(shuffled: Boolean)

    // Named rather than stepped, so a chrome that draws three distinct icons and
    // one that cycles a single button both say what they mean.
    public fun setRepeat(repeat: RepeatState)
}

public fun musicCommandsOf(player: NMMusicPlayer, scope: CoroutineScope): MusicCommands =
    PlayerMusicCommands(player, scope)

// Every call is launched because half the player's transport suspends and a
// button press does not. The scope is the caller's, so the coroutines go when
// the screen goes.
private class PlayerMusicCommands(
    private val player: NMMusicPlayer,
    private val scope: CoroutineScope,
) : MusicCommands {

    override fun setPlaying(playing: Boolean) {
        scope.launch { if (playing) player.play() else player.pause() }
    }

    override fun next() {
        scope.launch { player.next() }
    }

    override fun previous() {
        scope.launch { player.previous() }
    }

    override fun seekTo(seconds: Double) {
        scope.launch { player.time(seconds) }
    }

    override fun setVolume(percent: Int) {
        scope.launch { player.volume(percent) }
    }

    override fun setMuted(muted: Boolean) {
        scope.launch { if (muted) player.mute() else player.unmute() }
    }

    override fun setShuffled(shuffled: Boolean) {
        scope.launch { player.shuffleState(if (shuffled) ShuffleState.ON else ShuffleState.OFF) }
    }

    override fun setRepeat(repeat: RepeatState) {
        scope.launch { player.repeatState(repeat) }
    }
}
