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

// libmpv, which arrives with the library rather than being installed first: the
// payload store unpacks it on demand and the backend loads it from there.
//
// One engine on the desktop now. It was two while libmpv was proven against
// libVLC, and music picked whichever the video registry said was available.
public actual fun defaultAudioBackend(): AudioBackend = MpvAudioBackend()
