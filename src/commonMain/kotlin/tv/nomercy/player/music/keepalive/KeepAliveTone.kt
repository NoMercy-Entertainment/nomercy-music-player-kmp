// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.music.keepalive

// The signal that stops an AV receiver or soundbar auto-sleeping while a
// viewer is idle in the app with nothing audible playing.
//
// Ported per R6 ("the shipped Android app is a source, not a cross-check" —
// this row is "Must build": "Android TV music is unusable without it"), not
// derived from anything in the web trio, which has no HDMI-ARC sleep problem
// to solve. Extracted as its own interface — matching the deprecated app's
// own split — so [KeepAliveGate]'s idle/gate logic is testable with a fake,
// never a real audio device.
public interface KeepAliveTone {
    public fun start()
    public fun stop()
    public val isRunning: Boolean
}

// The platform with nothing to keep awake through an HDMI-ARC/AVR link —
// desktop speakers and phone speakers do not sleep the way a television's
// audio return channel does.
public object NoKeepAliveTone : KeepAliveTone {
    override fun start(): Unit = Unit
    override fun stop(): Unit = Unit
    override val isRunning: Boolean = false
}
