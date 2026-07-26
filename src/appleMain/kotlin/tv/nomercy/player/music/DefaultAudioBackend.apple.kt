// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.music

import tv.nomercy.player.core.ports.AVPlayerAudioBackend
import tv.nomercy.player.core.ports.AudioBackend

// AVFoundation, which needs no context either — the audio session is the app's
// to configure, and a library that configured it would be deciding on the app's
// behalf whether music ducks a phone call.
public actual fun defaultAudioBackend(): AudioBackend = AVPlayerAudioBackend()
