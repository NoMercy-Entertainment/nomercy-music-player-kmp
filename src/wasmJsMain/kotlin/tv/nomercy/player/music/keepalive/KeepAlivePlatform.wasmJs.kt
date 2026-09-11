// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.music.keepalive

// No HDMI-ARC sleep problem to solve in a browser tab, and a tone a visitor
// never asked for is one a browser's autoplay policy would refuse anyway. Scoped
// to Android, the same as the jvm and apple actuals.
public actual fun defaultKeepAliveTone(): KeepAliveTone = NoKeepAliveTone
