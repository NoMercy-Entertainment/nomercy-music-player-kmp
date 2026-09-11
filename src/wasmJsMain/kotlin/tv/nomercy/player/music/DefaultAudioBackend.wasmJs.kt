// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.music

import tv.nomercy.player.core.ports.AudioBackend
import tv.nomercy.player.core.ports.Html5AudioBackend

// A pair of `<audio>` elements, which is the browser's answer to the same
// problem Media3 and AVQueuePlayer solve elsewhere: one element plays one thing,
// and a crossfade needs the outgoing track still decoding while the incoming one
// starts.
public actual fun defaultAudioBackend(): AudioBackend = Html5AudioBackend()
