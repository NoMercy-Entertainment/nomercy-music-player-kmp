// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.music.keepalive

// No HDMI-ARC/AVR sleep problem to solve on desktop — a laptop or desktop's
// own speakers, or a plain analog/USB DAC, never auto-sleep the way a
// television's audio return channel does. Scoped to Android per R6, same as
// [tv.nomercy.player.music.keepalive.KeepAlivePlatform.apple.kt]'s appleMain actual.
public actual fun defaultKeepAliveTone(): KeepAliveTone = NoKeepAliveTone
