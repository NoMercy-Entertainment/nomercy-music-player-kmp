// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.music.connect

// A viewer's press held against the frame that has not heard about it yet.
//
// Pressing play on a device that is not the one playing sends the intent to the
// server and waits. In that gap the hub is still broadcasting the old state, and
// a client that adopted it would flip the button back under the viewer's finger
// and then flip it forward again a moment later.
//
// So the intent is shown immediately and defended, but only against frames the
// server produced BEFORE it heard the press — a later frame is a real answer,
// including a refusal, and adopting it is the point.
internal data class OptimisticShield(
    val sentAtServerMs: Long,
    val sentAtLocalMs: Long,
)

// Two thousand milliseconds. Long enough to cover a round trip on a bad
// connection, short enough that a lost command corrects itself before a viewer
// presses again.
internal const val OPTIMISTIC_SHIELD_MS = 2_000L

// A frame with no server stamp cannot be placed either side of the press, so it
// is held. The alternative is adopting it and flickering, and the shield expires
// on its own either way.
internal fun OptimisticShield?.holds(frameServerTimeMs: Long?, nowMs: Long): Boolean = when {
    this == null -> false
    nowMs - sentAtLocalMs >= OPTIMISTIC_SHIELD_MS -> false
    frameServerTimeMs != null -> frameServerTimeMs < sentAtServerMs
    else -> true
}

// The same question asked about a track rather than a button, and stricter by
// one case: an unstamped frame is applied here rather than held.
//
// The asymmetry is deliberate. Holding a button through an unstamped frame costs
// a flicker and corrects itself; dropping a track change costs the wrong song
// playing, so a server too old to stamp its frames gets the benefit of the doubt
// on this side and not on the other.
internal fun OptimisticShield?.precedes(frameServerTimeMs: Long?, nowMs: Long): Boolean = when {
    this == null -> false
    nowMs - sentAtLocalMs >= OPTIMISTIC_SHIELD_MS -> false
    frameServerTimeMs == null -> false
    else -> frameServerTimeMs < sentAtServerMs
}
