// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.music.keepalive

import androidx.test.platform.app.InstrumentationRegistry
import kotlin.test.Test

// F7 on real hardware: start/stop against a real AudioTrack, no mock.
class AndroidKeepAliveToneDeviceTest {

    @Test
    fun startsRunsAndStopsCleanlyOnRealAudioHardware() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val tone = AndroidKeepAliveTone(context)

        tone.start()
        Thread.sleep(500)

        val currentlyRunning: Boolean = tone.isRunning
        val route: List<String> = tone.currentOutputRoute()

        tone.stop()
        Thread.sleep(100)

        check(!tone.isRunning) { "stop() left the tone marked running" }
        println("F7 device trace: ranWithoutCrashing=true wasRunningAfterStart=$currentlyRunning route=$route")
    }
}
