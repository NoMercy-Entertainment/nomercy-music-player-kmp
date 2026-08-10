// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.music.keepalive

// Scoped to Android per R6's own framing ("Android TV music is unusable
// without it") — the deprecated app never built an Apple/tvOS equivalent, so
// there is no oracle to port from here. tvOS driving a receiver over HDMI-ARC
// may hit the identical soundbar-auto-sleep problem; unproven either way,
// and a real answer needs an AVAudioEngine tap this repo has no Apple
// toolchain to write or compile-check.
public actual fun defaultKeepAliveTone(): KeepAliveTone = NoKeepAliveTone
