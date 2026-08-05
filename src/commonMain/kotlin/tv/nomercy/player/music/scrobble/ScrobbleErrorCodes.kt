// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.music.scrobble

// Why a listen did not reach the service.
//
// Two codes because they cost different things: a refused nowPlaying is a badge
// that did not light up, and a refused scrobble is a play the listener's
// history will never show.
public object ScrobbleErrorCodes {
    public const val NOW_PLAYING_FAILED: String = "plugin:scrobble/now-playing-failed"
    public const val SCROBBLE_FAILED: String = "plugin:scrobble/scrobble-failed"

    public val all: Set<String> = setOf(NOW_PLAYING_FAILED, SCROBBLE_FAILED)
}
