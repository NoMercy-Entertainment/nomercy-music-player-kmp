// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.music.input

import tv.nomercy.player.core.input.KeyHandlerPlugin
import tv.nomercy.player.core.input.keyCombo
import tv.nomercy.player.core.plugin.PluginManifest

// The four keys a listener reaches for, installed.
//
// MUSIC_KEY_BINDINGS, nextRepeatState and nextShuffleState were all here and
// unit-tested, and nothing put them into a binding table: the arithmetic was
// proven and no key press could reach it. That is the shape this port keeps
// producing, a feature built at both ends and joined at neither, and a passing
// test on either end alone does not see it.
//
// Four bindings against the video player's fifty-three, because a music player
// is listened to rather than watched.
//
// The id is "key-handler", the web's, not "music-key-handler". A renamed plugin
// id is worse than a missing one: a consumer carrying working web code gets
// getPlugin("key-handler") returning nothing from a library that does have one.
public open class MusicKeyHandlerPlugin(
    protected val commands: MusicCommands,
    nowMs: () -> Long,
) : KeyHandlerPlugin<Unit>(nowMs) {

    public companion object Manifest : PluginManifest {
        override val id: String = "key-handler"
        override val version: String = "2.0.0"
    }

    override val manifest: PluginManifest get() = Manifest

    // Built from the shared list rather than restated, so the bindings and the
    // table cannot disagree about which keys exist.
    override fun addDefaults() {
        for (binding in MUSIC_KEY_BINDINGS) {
            bindings.bind(keyCombo(binding.combo)) { apply(binding.action) }
        }
    }

    private fun apply(action: MusicKeyAction) {
        when (action) {
            MusicKeyAction.NEXT -> commands.next()
            MusicKeyAction.PREVIOUS -> commands.previous()
            MusicKeyAction.CYCLE_REPEAT -> commands.repeatState(nextRepeatState(commands.repeatState()))
            MusicKeyAction.TOGGLE_SHUFFLE -> commands.shuffleState(nextShuffleState(commands.shuffleState()))
        }
    }
}
