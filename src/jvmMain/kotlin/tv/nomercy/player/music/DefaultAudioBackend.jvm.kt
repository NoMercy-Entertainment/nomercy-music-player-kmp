// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.music

import tv.nomercy.player.core.ports.AudioBackend
import tv.nomercy.player.core.ports.MpvAudioBackend
import tv.nomercy.player.core.ports.VlcjAudioBackend
import tv.nomercy.player.core.ports.engines.MpvVideoEngineProvider

// Whichever engine this machine has, mpv first.
//
// The same preference order the video registry uses, and for the same reason:
// mpv is the proven engine and libVLC is the fallback for a machine with no
// libmpv payload. Music asks the video registry rather than keeping a second
// opinion, because two places deciding which engine is available is how they
// come to disagree.
public actual fun defaultAudioBackend(): AudioBackend =
    if (MpvVideoEngineProvider.isAvailable()) MpvAudioBackend() else VlcjAudioBackend()
