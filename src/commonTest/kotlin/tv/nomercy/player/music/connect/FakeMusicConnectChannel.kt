// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.music.connect

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow

// A hub in memory, which several devices can share.
//
// Sharing one instance between two plugins is the point: that is what makes a
// handoff testable, and a handoff is where the double-play this subsystem exists
// to remove actually happens. A fake per device could never show it.
class FakeMusicConnectChannel(
    override val deviceId: String = "device-a",
    var serverTime: Long? = 1_000_000,
) : MusicConnectChannel {

    // What each answer costs on the way there and back, in the units the test's
    // own clock counts in. A list rather than a number because the offset is a
    // best-of-N: one uncontended trip among several slow ones is the sample the
    // measurement is supposed to keep, and a fake with a single latency cannot
    // tell keeping the best from keeping the last.
    var roundTripCosts: List<Long> = emptyList()

    // Advanced by each answer's cost, so a test's clock can be this device's.
    var elapsedMs: Long = 0
        private set

    var answered: Int = 0
        private set

    val sent: MutableList<String> = mutableListOf()

    val reported: MutableList<Long> = mutableListOf()

    // No replay: a device that joins late should not be handed a frame from
    // before it was listening, because a real hub does not do that either.
    private val broadcasts = MutableSharedFlow<MusicPlayerState>(replay = 0, extraBufferCapacity = 64)

    override val frames: Flow<MusicPlayerState> = broadcasts

    override suspend fun playbackCommand(command: String, dataSeconds: Double?) {
        sent += if (dataSeconds == null) command else "$command:$dataSeconds"
    }

    override suspend fun changeDevice(targetDeviceId: String) {
        sent += "changeDevice:$targetDeviceId"
    }

    override suspend fun reportPosition(positionMs: Long) {
        reported += positionMs
    }

    override suspend fun serverTimeMs(): Long? {
        // Cycled, so a round of measurements costs the same whichever round it
        // is. A plugin measures once on its own the moment it starts, and a list
        // read straight through would leave every later round free.
        elapsedMs += if (roundTripCosts.isEmpty()) 0 else roundTripCosts[answered % roundTripCosts.size]
        answered += 1
        return serverTime
    }

    // The server telling everyone what is true now.
    suspend fun broadcast(state: MusicPlayerState) {
        broadcasts.emit(state)
    }
}
