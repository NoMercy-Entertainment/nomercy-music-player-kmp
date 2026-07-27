// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.music.connect

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import tv.nomercy.player.core.events.BeforeEvent
import tv.nomercy.player.core.events.CoreEvents
import tv.nomercy.player.core.events.SeekPosition
import tv.nomercy.player.core.player.ActionOptions
import tv.nomercy.player.core.player.ActionSource
import tv.nomercy.player.core.plugin.Plugin
import tv.nomercy.player.core.plugin.PluginManifest

// Music across several devices, with one of them making sound.
//
// The server decides which. This plugin sends what the viewer did to the server
// and does what the server says back — it never decides for itself who is
// playing, because two devices each deciding is exactly the double-play this
// replaces.
//
// Outbound has two shapes and the difference is the whole design. The active
// device tells the server and carries on: it is already the one playing, so
// stopping it to wait for a round trip would put a gap in the audio for the
// person holding it. A passive device tells the server and stops: it is not the
// one playing, and letting it start would be the second stream.
public open class MusicConnectPlugin(
    private val channel: MusicConnectChannel,
    private val scope: CoroutineScope,
) : Plugin<Unit>() {

    public companion object Manifest : PluginManifest {
        override val id: String = "music-connect"
        override val version: String = "2.0.0"
    }

    override val manifest: PluginManifest get() = Manifest

    // Who the server last said was playing. Null until a frame arrives, which
    // is the honest starting state: this device does not yet know.
    protected var activeDeviceId: String? = null
        private set

    protected var lastAppliedSeq: Long = 0
        private set

    private var subscription: Job? = null

    public open val role: DeviceRole get() = resolveRole(activeDeviceId, channel.deviceId)

    public open val isActiveDevice: Boolean get() = role == DeviceRole.ACTIVE

    override fun use() {
        on(CoreEvents.BeforePlay) { event -> guard(event, ConnectCommand.PLAY) }
        on(CoreEvents.BeforePause) { event -> guard(event, ConnectCommand.PAUSE) }
        on(CoreEvents.BeforeStop) { event -> guard(event, ConnectCommand.STOP) }
        on(CoreEvents.BeforeNext) { event -> guard(event, ConnectCommand.NEXT) }
        on(CoreEvents.BeforePrevious) { event -> guard(event, ConnectCommand.PREVIOUS) }

        on(CoreEvents.BeforeSeek, ::guardSeek)

        // Collected on the scope this plugin was given rather than on the
        // player's own, so one scope owns both directions of the conversation
        // and a caller can see where it ends. Cancelled explicitly below,
        // because a subscription outliving the plugin keeps answering a hub on
        // behalf of a player that is gone.
        subscription = scope.launch {
            channel.frames.collect { frame -> applyServerFrame(frame) }
        }
    }

    override fun dispose() {
        subscription?.cancel()
        subscription = null
    }

    // What the server said, once the gate has let it through.
    //
    // Open so the later halves — the passive mirror, the active reconciliation —
    // extend it rather than replacing this dispatch.
    protected open fun applyServerFrame(frame: MusicPlayerState) {
        val nextSeq: Long = nextAppliedSeqOrNull(frame.seq, lastAppliedSeq) ?: return

        lastAppliedSeq = nextSeq
        activeDeviceId = frame.deviceId
    }

    // Its own function rather than a labelled return inside the subscription,
    // which reads as a jump out of a lambda and is one more thing to hold while
    // reading the six hooks above it.
    private fun guardSeek(event: BeforeEvent<SeekPosition>) {
        if (isEcho(event.data.source)) return

        if (!isActiveDevice) event.preventDefault()
        val seconds: Double = event.data.time
        scope.launch { channel.playbackCommand(ConnectCommand.SEEK, seconds) }
    }

    private fun guard(event: BeforeEvent<ActionOptions>, command: String) {
        if (isEcho(event.data.source)) return

        if (!isActiveDevice) event.preventDefault()
        scope.launch { channel.playbackCommand(command) }
    }

    // An action the server itself caused. Sending it back would be this device
    // asking the server to do what the server just told it had happened, which
    // on a hub with several listeners is a loop rather than a duplicate.
    private fun isEcho(source: String?): Boolean = source == ActionSource.REMOTE
}
