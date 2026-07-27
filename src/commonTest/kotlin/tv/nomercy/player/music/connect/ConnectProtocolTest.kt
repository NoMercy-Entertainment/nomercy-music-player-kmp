// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.music.connect

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

// The two decisions every inbound frame passes through.
//
// Pure, and tested here rather than through the plugin, so the applier's own
// tests can assert what it did rather than re-deriving whether it should have.
class ConnectProtocolTest {

    @Test
    fun aNewerFrameIsAppliedAndMovesTheBar() {
        assertEquals(7L, nextAppliedSeqOrNull(seq = 7, lastAppliedSeq = 5))
    }

    @Test
    fun aFrameAlreadySeenIsDropped() {
        // A hub redelivers on reconnect. Applying the same frame twice is
        // usually harmless and occasionally is a track jumping back.
        assertNull(nextAppliedSeqOrNull(seq = 5, lastAppliedSeq = 5))
    }

    @Test
    fun aFrameFromBeforeTheLastOneIsDropped() {
        // Two commands issued close together can be broadcast in the order the
        // server processed them rather than the order they were sent. Acting on
        // the older one undoes the newer.
        assertNull(nextAppliedSeqOrNull(seq = 4, lastAppliedSeq = 5))
    }

    @Test
    fun anUnsequencedFrameIsAlwaysAppliedAndLeavesTheBarAlone() {
        // A server old enough not to sequence its broadcasts. Dropping these
        // means refusing to work with it rather than degrading against it, and
        // moving the bar to zero would then drop every real frame after it.
        assertEquals(5L, nextAppliedSeqOrNull(seq = 0, lastAppliedSeq = 5))
        assertEquals(0L, nextAppliedSeqOrNull(seq = 0, lastAppliedSeq = 0))
    }

    @Test
    fun theFirstFrameOfASessionIsApplied() {
        assertEquals(1L, nextAppliedSeqOrNull(seq = 1, lastAppliedSeq = 0))
    }

    @Test
    fun thisDeviceIsActiveWhenTheServerNamesIt() {
        assertEquals(DeviceRole.ACTIVE, resolveRole("dev-a", "dev-a"))
    }

    @Test
    fun identityIsComparedWithoutCase() {
        // The identifiers come from a stored preference, a handshake and a
        // server record, and one of them upper-casing a hexadecimal id would
        // leave a device passive on its own session — silent while it believed
        // it was playing.
        assertEquals(DeviceRole.ACTIVE, resolveRole("DEV-A", "dev-a"))
        assertEquals(DeviceRole.ACTIVE, resolveRole("dev-a", "DEV-A"))
    }

    @Test
    fun anotherDeviceMakesThisOnePassive() {
        assertEquals(DeviceRole.PASSIVE, resolveRole("dev-b", "dev-a"))
    }

    @Test
    fun noActiveDeviceIsItsOwnAnswerRatherThanPassive() {
        // Nothing is playing anywhere. A device that read that as passive would
        // start mirroring a session that does not exist.
        assertEquals(DeviceRole.NONE, resolveRole(null, "dev-a"))
    }

    @Test
    fun anIdThatMerelyContainsAnothersIsNotAMatch() {
        // "dev-a" and "dev-ab" are different devices, and a prefix comparison
        // would make one of them permanently believe it was the other.
        assertEquals(DeviceRole.PASSIVE, resolveRole("dev-ab", "dev-a"))
    }
}
