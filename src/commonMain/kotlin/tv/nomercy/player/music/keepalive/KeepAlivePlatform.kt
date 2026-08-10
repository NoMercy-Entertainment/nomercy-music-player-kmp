// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.music.keepalive

// Which tone this platform can actually offer — only Android TV has an
// HDMI-ARC/AVR sleep problem to solve; desktop and phone speakers never
// auto-sleep the way a television's audio return channel does.
public expect fun defaultKeepAliveTone(): KeepAliveTone
