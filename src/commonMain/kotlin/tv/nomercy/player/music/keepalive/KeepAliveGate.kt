// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.music.keepalive

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

// When the keep-awake tone actually runs — ported case-for-case from the
// deprecated app's `KeepAliveStore` (R6), which earned this exact gate
// against a real regression, not designed fresh:
//
// userEnabled && foregrounded && !videoPlaying && !musicPlaying, held for
// [IDLE_THRESHOLD_MS] before the tone actually starts, and only ONE Connect
// device's local speakers are ever real: `musicPlaying` means "isPlaying AND
// this device is the active one" — a passive device mirrors `isPlaying` for
// its own UI while the audio comes out of a different device entirely, and
// must keep the tone running or ITS OWN soundbar sleeps mid-session while
// audio it never emits is reported as playing.
//
// Screensaver state is deliberately not a gate input, same as the app: a
// soundbar is most likely to auto-sleep during the deepest idle, which is
// exactly when the tone must keep running through it.
public class KeepAliveGate(
    private val tone: KeepAliveTone,
    dispatcher: CoroutineDispatcher = Dispatchers.Default,
) {
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + dispatcher)

    private val userEnabledFlow = MutableStateFlow(false)
    private val foregroundedFlow = MutableStateFlow(false)
    private val videoPlayingFlow = MutableStateFlow(false)
    private val musicPlayingFlow = MutableStateFlow(false)

    private val activeMutable = MutableStateFlow(false)
    public val active: StateFlow<Boolean> = activeMutable.asStateFlow()

    private var idleJob: Job? = null
    private var conditionJob: Job? = null
    private var playbackJob: Job? = null

    init {
        conditionJob = scope.launch {
            combine(
                userEnabledFlow, foregroundedFlow, videoPlayingFlow, musicPlayingFlow,
            ) { userOn, foregrounded, videoPlaying, musicPlaying ->
                userOn && foregrounded && !videoPlaying && !musicPlaying
            }.collect { shouldIdle -> onIdleConditionChanged(shouldIdle) }
        }
    }

    // [isActiveDevice] mirrors the app's own reasoning above: a
    // Connect-aware caller passes its player's own `isPlaying` and
    // `isActiveDevice` flows; a caller with no Connect concept at all can
    // pass `isActiveDevice = MutableStateFlow(true)` so `isPlaying` alone
    // decides it, the single-device case the gate degenerates to correctly.
    public fun attachPlayback(isPlaying: StateFlow<Boolean>, isActiveDevice: StateFlow<Boolean>) {
        playbackJob?.cancel()
        playbackJob = scope.launch {
            combine(isPlaying, isActiveDevice) { playing, active -> playing && active }
                .collect { locallyAudible -> musicPlayingFlow.value = locallyAudible }
        }
    }

    public fun setVideoPlaying(playing: Boolean) {
        videoPlayingFlow.value = playing
    }

    public fun setUserEnabled(enabled: Boolean) {
        userEnabledFlow.value = enabled
    }

    public fun setForegrounded(foregrounded: Boolean) {
        foregroundedFlow.value = foregrounded
    }

    private fun onIdleConditionChanged(conditionsMet: Boolean) {
        if (!conditionsMet) {
            idleJob?.cancel()
            idleJob = null
            deactivate()
            return
        }
        if (idleJob?.isActive != true) {
            idleJob = scope.launch {
                delay(IDLE_THRESHOLD_MS)
                activate()
            }
        }
    }

    private fun activate() {
        if (activeMutable.value) return
        activeMutable.value = true
        tone.start()
    }

    private fun deactivate() {
        if (!activeMutable.value && !tone.isRunning) return
        activeMutable.value = false
        tone.stop()
    }

    public fun dispose() {
        conditionJob?.cancel()
        playbackJob?.cancel()
        idleJob?.cancel()
        tone.stop()
    }

    public companion object {
        // How long the idle condition must hold before the tone actually
        // starts — long enough that a brief pause between tracks never
        // triggers it, short enough that a soundbar's own (typically
        // several-minute) auto-sleep timer never gets ahead of it.
        public const val IDLE_THRESHOLD_MS: Long = 30_000L
    }
}
