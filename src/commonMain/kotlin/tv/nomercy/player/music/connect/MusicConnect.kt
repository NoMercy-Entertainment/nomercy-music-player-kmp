// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.music.connect

import kotlinx.coroutines.CoroutineScope
import tv.nomercy.player.core.controllers.ComposedPlayer

// Join a Connect session and start following it.
//
// A function beside the player rather than a method on it. The player's own
// method surface is measured against the web player's and has to match it
// exactly, and the web player has no connect method — there the application
// installs the plugin the same way it installs any other. Adding one here would
// make the two libraries different shapes for no gain, and the conformance gate
// says so out loud.
//
// It is also how casting reads in the video library, which matters more than
// either: someone who has wired one should recognise the other.
//
// The scope is the caller's because the plugin talks to a hub for as long as
// that scope lives, and a library inventing one would outlive the screen it
// belongs to.
public suspend fun connectMusic(
    player: ComposedPlayer,
    channel: MusicConnectChannel,
    scope: CoroutineScope,
    enabled: Boolean = true,
): MusicConnectPlugin {
    val plugin = MusicConnectPlugin(player, channel, scope)
    player.addPlugin(plugin)
    if (!enabled) plugin.disable("not joined yet")
    return plugin
}
