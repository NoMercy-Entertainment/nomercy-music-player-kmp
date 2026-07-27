// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.music.ui

// A position, as a clock reads it.
//
// Hours only when there are any. A three-minute song written 0:03:12 makes a
// listener parse a field that is always zero, and an eleven-hour mix written
// 47:12 is a lie about which one they are looking at.
public fun formatTime(seconds: Double): String {
    if (seconds.isNaN() || seconds < 0.0) return "0:00"

    val total: Long = seconds.toLong()
    val hours: Long = total / SECONDS_PER_HOUR
    val minutes: Long = (total % SECONDS_PER_HOUR) / SECONDS_PER_MINUTE
    val remainder: Long = total % SECONDS_PER_MINUTE

    return if (hours > 0) {
        "$hours:${pad(minutes)}:${pad(remainder)}"
    } else {
        "$minutes:${pad(remainder)}"
    }
}

private fun pad(value: Long): String = if (value < TEN) "0$value" else "$value"

private const val SECONDS_PER_HOUR = 3600L
private const val SECONDS_PER_MINUTE = 60L
private const val TEN = 10L
