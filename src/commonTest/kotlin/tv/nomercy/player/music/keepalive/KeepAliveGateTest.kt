// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.music.keepalive

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

// Ported case-for-case from the deprecated app's KeepAliveStoreTest — same
// inputs, same expected outcomes, only the harness (JUnit -> kotlin.test)
// and the dispatcher-injection call site (this port takes it in the
// constructor the same way the app's version did) changed.
@OptIn(ExperimentalCoroutinesApi::class)
class KeepAliveGateTest {

    private class FakeTone : KeepAliveTone {
        var startCount = 0
        var stopCount = 0
        override var isRunning: Boolean = false
            private set

        override fun start() {
            startCount++
            isRunning = true
        }

        override fun stop() {
            stopCount++
            isRunning = false
        }
    }

    @Test
    fun passiveDeviceMirroringRemotePlaybackKeepsToneRunning() = runTest {
        // Regression: a passive Connect device mirrors isPlaying=true for its
        // UI while the audio physically plays on ANOTHER device. The tone
        // must run or ITS soundbar auto-sleeps mid-session.
        val tone = FakeTone()
        val gate = KeepAliveGate(tone, StandardTestDispatcher(testScheduler))
        val isPlaying = MutableStateFlow(true)
        val isActiveDevice = MutableStateFlow(false)

        gate.setUserEnabled(true)
        gate.setForegrounded(true)
        gate.attachPlayback(isPlaying, isActiveDevice)
        advanceUntilIdle()

        advanceTimeBy(KeepAliveGate.IDLE_THRESHOLD_MS + 1)
        advanceUntilIdle()

        assertTrue(tone.isRunning, "tone must run while this device is passive")
        gate.dispose()
    }

    @Test
    fun activeDevicePlayingSuppressesTone() = runTest {
        val tone = FakeTone()
        val gate = KeepAliveGate(tone, StandardTestDispatcher(testScheduler))
        val isPlaying = MutableStateFlow(true)
        val isActiveDevice = MutableStateFlow(true)

        gate.setUserEnabled(true)
        gate.setForegrounded(true)
        gate.attachPlayback(isPlaying, isActiveDevice)
        advanceUntilIdle()

        advanceTimeBy(KeepAliveGate.IDLE_THRESHOLD_MS + 1)
        advanceUntilIdle()

        assertFalse(tone.isRunning, "tone must not run while this device emits real audio")
        assertEquals(0, tone.startCount)
        gate.dispose()
    }

    @Test
    fun transferToThisDeviceStopsRunningTone() = runTest {
        val tone = FakeTone()
        val gate = KeepAliveGate(tone, StandardTestDispatcher(testScheduler))
        val isPlaying = MutableStateFlow(true)
        val isActiveDevice = MutableStateFlow(false)

        gate.setUserEnabled(true)
        gate.setForegrounded(true)
        gate.attachPlayback(isPlaying, isActiveDevice)
        advanceUntilIdle()
        advanceTimeBy(KeepAliveGate.IDLE_THRESHOLD_MS + 1)
        advanceUntilIdle()
        assertTrue(tone.isRunning)

        // ChangeDevice lands here: this device becomes the active engine.
        isActiveDevice.value = true
        advanceUntilIdle()

        assertFalse(tone.isRunning, "tone must stop the moment this device starts emitting audio")
        gate.dispose()
    }

    @Test
    fun pausedActiveDeviceActivatesToneAfterIdleThreshold() = runTest {
        val tone = FakeTone()
        val gate = KeepAliveGate(tone, StandardTestDispatcher(testScheduler))
        val isPlaying = MutableStateFlow(false)
        val isActiveDevice = MutableStateFlow(true)

        gate.setUserEnabled(true)
        gate.setForegrounded(true)
        gate.attachPlayback(isPlaying, isActiveDevice)
        advanceUntilIdle()

        advanceTimeBy(KeepAliveGate.IDLE_THRESHOLD_MS + 1)
        advanceUntilIdle()

        assertTrue(tone.isRunning, "tone must run while playback is paused")
        gate.dispose()
    }
}
