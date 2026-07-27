// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.music.connect

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import tv.nomercy.player.music.ConnectBackend
import tv.nomercy.player.music.NMMusicPlayer
import tv.nomercy.player.music.Track
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

// The one call an application makes.
//
// It is a convenience over the ordinary plugin, so what is worth testing is that
// it really is the ordinary plugin: registered where the registry can see it,
// hooks live, and torn down with the player rather than left holding a hub.
class ConnectRegistrationTest {

    private fun TestScope.eager(): CoroutineScope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))

    private suspend fun TestScope.player(): Pair<NMMusicPlayer, ConnectBackend> {
        val backend = ConnectBackend()
        val player = NMMusicPlayer(backend)
        player.setup()
        return player to backend
    }

    @Test
    fun connectingRegistersOneConnectPluginWithThePlayer() = runTest {
        val (player, _) = player()

        val plugin: MusicConnectPlugin = connectMusic(player, FakeMusicConnectChannel(deviceId = "dev-a"), eager())

        assertEquals(
            listOf(MusicConnectPlugin.id),
            player.pluginList().map { registered -> registered.manifest.id },
        )
        assertEquals(plugin, player.getPlugin(MusicConnectPlugin.id))
        assertTrue(plugin.enabled(), "the plugin came back disabled")
    }

    @Test
    fun theHooksAreLiveWithoutAnyFurtherWiring() = runTest {
        // The whole reason the convenience exists is that an application should
        // not have to know which events to subscribe to. If registering does not
        // also route a press, it has saved a line and hidden a step.
        val (player, backend) = player()
        val channel = FakeMusicConnectChannel(deviceId = "dev-a")
        connectMusic(player, channel, eager())

        channel.broadcast(MusicPlayerState(deviceId = "dev-b", seq = 1, item = Track("a")))
        testScheduler.runCurrent()
        player.play()

        assertEquals(listOf("play"), channel.sent)
        assertEquals(0, backend.playCount, "a passive device produced sound")
    }

    @Test
    fun joiningLaterIsRegisteredButSilent() = runTest {
        // An application that wants the session on a switch rather than on
        // startup. It is registered, so enabling it is one call and not a
        // reconstruction, and until then it neither follows nor reports.
        val (player, _) = player()
        val channel = FakeMusicConnectChannel(deviceId = "dev-a")

        val plugin: MusicConnectPlugin = connectMusic(player, channel, eager(), enabled = false)
        channel.broadcast(MusicPlayerState(deviceId = "dev-b", seq = 1, item = Track("a")))
        testScheduler.runCurrent()
        player.play()

        assertTrue(!plugin.enabled())
        assertEquals(emptyList(), channel.sent, "a disabled plugin talked to the hub")
    }

    @Test
    fun disposingThePlayerStopsTheDeviceFollowingTheHub() = runTest {
        // A plugin outliving its player keeps applying frames on behalf of
        // something that is gone, and on an application that opens a player per
        // screen that is one live listener per screen ever opened.
        val (player, _) = player()
        val channel = FakeMusicConnectChannel(deviceId = "dev-a")
        val plugin: MusicConnectPlugin = connectMusic(player, channel, eager())

        player.dispose()
        channel.broadcast(MusicPlayerState(deviceId = "dev-a", seq = 5, item = Track("a")))
        testScheduler.runCurrent()

        assertEquals(DeviceRole.NONE, plugin.role, "a disposed player was still following the hub")
    }
}
