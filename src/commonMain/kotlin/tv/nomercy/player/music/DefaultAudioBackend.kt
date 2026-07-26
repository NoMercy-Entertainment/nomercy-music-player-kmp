// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.music

import tv.nomercy.player.core.ports.AudioBackend

// The audio engine this platform plays music with.
//
// Media3 on Android, AVQueuePlayer on Apple, libVLC on the desktop — each held
// as a pair, because one engine cannot crossfade: the outgoing track has to keep
// decoding while the incoming one starts, and a single pipeline cannot do both.
//
// A function rather than a value, because building one opens native handles and
// a consumer that never plays music should not pay for them at class-load time.
public expect fun defaultAudioBackend(): AudioBackend
