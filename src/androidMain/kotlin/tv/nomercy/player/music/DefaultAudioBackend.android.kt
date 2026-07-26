// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.music

import tv.nomercy.player.core.ports.AudioBackend
import tv.nomercy.player.core.ports.ExoPlayerAudioBackend
import tv.nomercy.player.core.ports.PlatformEnvironment

// Media3, which does need a Context — an ExoPlayer cannot exist without one.
//
// Taken from the installed platform context rather than asked for here, so the
// music library's entry point looks the same on every target. A host that has
// not installed one gets the named failure PlatformEnvironment already raises,
// which says what to install, rather than a NullPointerException from inside a
// constructor.
public actual fun defaultAudioBackend(): AudioBackend =
    ExoPlayerAudioBackend(PlatformEnvironment.requireContext().androidContext)
