// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.music

import tv.nomercy.player.core.ports.AudioBackend
import tv.nomercy.player.core.ports.VlcjAudioBackend

// libVLC, which needs nothing installed first: it finds its own native libraries
// through the factory it builds.
public actual fun defaultAudioBackend(): AudioBackend = VlcjAudioBackend()
