// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.music.connect

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

// The progress bar a device draws for playback happening somewhere else.
//
// Its own thing rather than three methods on the plugin, because it owns a
// running coroutine and the rule about when that coroutine may exist is the
// whole of its behaviour: it runs exactly while the bar has somewhere to go.
//
// Nothing here touches the engine. That is what makes a second stream impossible
// on a passive device rather than merely unlikely.
internal class ConnectMirrorTicker(private val scope: CoroutineScope) {

    private val state = MutableStateFlow(ConnectMirror())

    val mirror: StateFlow<ConnectMirror> = state.asStateFlow()

    private var ticking: Job? = null

    // Every arriving frame lands here, and the frame is the truth: the bar is
    // set from it rather than added to, so a client interpolating between frames
    // is corrected each time instead of walking further away from the server.
    fun show(next: ConnectMirror) {
        state.value = next
        if (next.isAdvancing) start() else stop()
    }

    // A viewer's own press, before the server has answered.
    //
    // The button flips now and the bar does not move yet. Interpolating from a
    // position nobody has confirmed compounds a guess on a guess, and the
    // confirming frame arrives about a round trip later carrying the real one —
    // at which point the bar starts from the truth.
    //
    // Pausing stops it immediately, because that direction is safe without an
    // answer: a bar that has stopped early is corrected by the next frame, and a
    // bar still running after the viewer pressed pause looks broken.
    fun intend(isPlaying: Boolean) {
        state.value = state.value.copy(isPlaying = isPlaying)
        if (!isPlaying) stop()
    }

    fun clear() {
        stop()
        state.value = ConnectMirror()
    }

    fun dispose() {
        stop()
    }

    // A server broadcasts on change rather than continuously, so a bar drawn only
    // from frames sits still for a whole track and then jumps. This walks it
    // forward between them, which is what the platforms' own lock screens do with
    // a position and a rate.
    //
    // It ends the moment the bar cannot move — paused, or arrived at the end.
    // Left unbounded it is a wakeup four times a second per idle device, and on a
    // phone that is battery spent drawing nothing.
    private fun start() {
        if (ticking?.isActive == true) return

        ticking = scope.launch {
            while (isActive && state.value.isAdvancing) {
                delay(MIRROR_TICK_MS)
                state.value = state.value.advancedBy(MIRROR_TICK_MS)
            }
        }
    }

    private fun stop() {
        ticking?.cancel()
        ticking = null
    }
}

// Four times a second, which is what a progress bar needs to look continuous and
// is cheap enough to run on a device that is otherwise doing nothing.
private const val MIRROR_TICK_MS = 250L
