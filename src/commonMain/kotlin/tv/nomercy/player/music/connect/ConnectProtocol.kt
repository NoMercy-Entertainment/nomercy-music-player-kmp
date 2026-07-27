// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.music.connect

// Whether to act on a frame, and what the bar becomes if so.
//
// Frames arrive out of order and more than once. A hub redelivers on reconnect,
// and two commands issued close together can be broadcast in the order the
// server processed them rather than the order they were sent — so a device that
// applied everything it received would end up acting on a state that has already
// been superseded, which shows up as a track that jumps back a second after
// someone skips.
//
// Zero means a server old enough not to sequence its broadcasts at all. Those
// are always applied and leave the bar alone, because dropping them would mean
// refusing to work with an older server rather than degrading against one.
internal fun nextAppliedSeqOrNull(seq: Long, lastAppliedSeq: Long): Long? {
    if (seq != UNSEQUENCED && seq <= lastAppliedSeq) return null

    return if (seq != UNSEQUENCED) seq else lastAppliedSeq
}

// Whether this device is the one making sound.
//
// Compared without case, because the identifiers come from several places — a
// stored preference, a handshake, a server record — and one of them upper-casing
// a hexadecimal id would make a device passive on its own session and silent
// while it believed it was playing.
internal fun resolveRole(frameDeviceId: String?, myDeviceId: String): DeviceRole = when {
    frameDeviceId == null -> DeviceRole.NONE
    frameDeviceId.equals(myDeviceId, ignoreCase = true) -> DeviceRole.ACTIVE
    else -> DeviceRole.PASSIVE
}

private const val UNSEQUENCED = 0L
