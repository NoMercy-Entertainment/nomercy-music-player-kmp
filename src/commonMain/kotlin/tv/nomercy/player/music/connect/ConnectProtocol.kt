// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.music.connect

import tv.nomercy.player.core.player.ActionSource

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

// Where the active device should actually be by now.
//
// A frame describes a moment that has already passed by the time it arrives, so
// seeking to the number in it lands behind — and on a handoff that is audible as
// the new device starting a beat late.
//
// Where the server says when the position was taken, the correction is the time
// since that instant. Where it does not, the frame's own send time is the best
// anchor there is and half of the round trip is the usual guess at one-way
// latency. An unstamped frame is not corrected at all, because a made-up
// correction is worse than a known-stale number.
internal fun adjustedPositionMs(frame: MusicPlayerState, serverNowMs: Long): Long {
    val capturedAtMs: Long? = frame.positionCapturedAtMs
    val elapsedMs: Long = when {
        capturedAtMs != null -> (serverNowMs - capturedAtMs).coerceAtLeast(0)
        frame.timestamp > 0 -> ((serverNowMs - frame.timestamp) / 2).coerceAtLeast(0)
        else -> 0
    }

    val moved: Long = frame.progressMs + elapsedMs
    return if (frame.durationMs > 0) moved.coerceAtMost(frame.durationMs) else moved
}

// The same number in the unit a seek is asked for.
internal fun adjustedSeekSeconds(frame: MusicPlayerState, serverNowMs: Long): Double =
    adjustedPositionMs(frame, serverNowMs) / MILLIS_PER_SECOND

// An action the server itself caused. Sending it back would be this device
// asking the server to do what the server just told it had happened, which on a
// hub with several listeners is a loop rather than a duplicate.
internal fun isEcho(source: String?): Boolean = source == ActionSource.REMOTE

// This device's own remembered level, out of the map the server keeps per
// device.
//
// Never a mirrored value. A phone at thirty percent and a television at eighty
// are both correct, and adopting the other's would make one of them wrong every
// time the session moved — which is why this is read for every device whatever
// its role, while the session's single volume figure is not.
//
// Matched without case for the same reason the role is: the identifiers come
// from a stored preference, a handshake and a server record, and one of them
// upper-casing a hexadecimal id would silently leave the level unapplied.
internal fun ownVolumeIn(frame: MusicPlayerState, deviceId: String): Int? =
    frame.deviceVolumes.entries
        .firstOrNull { it.key.equals(deviceId, ignoreCase = true) }
        ?.value
        ?.coerceIn(0, MAX_VOLUME_PERCENT)

// How long a device that has just been promoted ignores a pause.
//
// Taking over is two messages: this device says it is taking the session, the
// old one says it has let go. They cross, and the release arrives after the
// promotion — so a device that applied it would pause the track it had just
// been handed, about half a second after the viewer moved it.
//
// Only the pause direction is suppressed. A stale frame still saying "playing"
// catches up harmlessly, and suppressing a real pause for half a second is a
// button that answers late rather than a session that stops on its own.
internal const val SETTLEMENT_MS = 500L

private const val MAX_VOLUME_PERCENT = 100

// Far enough out that a viewer hears it. Correcting anything smaller would make
// every frame a seek, and a seek on a music engine is an audible gap — so the
// tolerance exists to keep the common case silent, not to save work.
internal const val DRIFT_TOLERANCE_SECONDS = 5.0

private const val MILLIS_PER_SECOND = 1000.0

private const val UNSEQUENCED = 0L

// How far the server's clock is from this device's, from one round trip.
//
// The reference instant is the midpoint of the exchange, not either end: the
// server's answer describes a moment somewhere between the request leaving and
// the reply arriving, and the midpoint is the only point equally wrong in both
// directions. Anchoring on the send or the receive instead builds the whole
// round trip into the offset, which on a slow connection is a bar seconds out.
internal fun clockOffsetMs(serverMs: Long, sentAtMs: Long, receivedAtMs: Long): Long =
    serverMs - (sentAtMs + receivedAtMs) / 2

// Enough rounds to have a good chance of one uncontended trip, few enough that a
// device does not spend its first second on the network asking what time it is.
internal const val CLOCK_SAMPLES = 5

// Half a minute. Clocks drift slowly, and a device that has been asleep gets a
// fresh answer from the reconnect rather than from this.
internal const val CLOCK_SYNC_PERIOD_MS = 30_000L
