// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.music

import tv.nomercy.player.core.ports.AudioBackend

// The music player for [id], built once.
//
// The same id gives back the same player. That is not a convenience: two music
// players in one app fight over the audio focus, and the second one wins by
// silencing the first. An app that navigates back to a now-playing screen and
// asks for its player should get the one that is playing.
//
// The constructor stays public beside this. A consumer who wants two players on
// purpose — a preview scrubber beside the main one — is doing something
// deliberate and the library should not be the thing that stops them. Guidance,
// not walls.
public fun nmMusicPlayer(id: String = "nmmusic", backend: AudioBackend? = null): NMMusicPlayer =
    NMMusicPlayer.byId(id) ?: NMMusicPlayer(
        backend = backend,
        transitions = backend,
        id = id,
    ).also(NMMusicPlayer::register)
