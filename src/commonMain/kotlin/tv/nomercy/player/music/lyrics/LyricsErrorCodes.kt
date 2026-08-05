// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.music.lyrics

// Why the lyrics for a track did not arrive.
//
// Declared in the plugin that raises them, which is what the plugin: namespace
// is for — a plugin owns its ids without asking core for a slot. Two codes
// because the answers differ: a file that would not fetch may work on the next
// track, and a format nobody registered a parser for is a missing line of the
// consumer's setup.
public object LyricsErrorCodes {
    public const val FETCH_FAILED: String = "plugin:lyrics/fetch-failed"
    public const val NO_PARSER: String = "plugin:lyrics/no-parser"

    public val all: Set<String> = setOf(FETCH_FAILED, NO_PARSER)
}
