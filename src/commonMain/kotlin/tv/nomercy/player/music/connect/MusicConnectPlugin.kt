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
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import tv.nomercy.player.core.events.BeforeEvent
import tv.nomercy.player.core.events.CoreEvents
import tv.nomercy.player.core.events.SeekPosition
import tv.nomercy.player.core.media.PlaylistItem
import tv.nomercy.player.core.player.ActionOptions
import tv.nomercy.player.core.player.ActionSource
import tv.nomercy.player.core.plugin.Plugin
import tv.nomercy.player.core.plugin.PluginManifest
import tv.nomercy.player.core.player.RepeatState
import tv.nomercy.player.core.player.ShuffleState
import tv.nomercy.player.core.controllers.ComposedPlayer

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
    private val player: ComposedPlayer,
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

    private var ticking: Job? = null

    private val mirrored = MutableStateFlow(ConnectMirror())

    // What a passive device draws. Empty on the active one, which renders from
    // its own player because it is the thing actually playing.
    public val mirror: StateFlow<ConnectMirror> = mirrored.asStateFlow()

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
        stopMirroring()
    }

    // What the server said, once the gate has let it through.
    //
    // Open so the later halves — the passive mirror, the active reconciliation —
    // extend it rather than replacing this dispatch.
    protected open fun applyServerFrame(frame: MusicPlayerState) {
        val nextSeq: Long = nextAppliedSeqOrNull(frame.seq, lastAppliedSeq) ?: return
        lastAppliedSeq = nextSeq

        // No item is the session ending, and it ends everywhere at once. The
        // device that was playing stops and every other one stops mirroring,
        // which is why this happens before the role is reconciled — after it,
        // the device that just stopped being active would take the passive
        // branch and start following a session that no longer exists.
        if (frame.item == null) {
            activeDeviceId = null
            stopMirroring()
            scope.launch { player.stop(remote) }
            return
        }

        activeDeviceId = frame.deviceId
        applyUniversalSettings(frame)

        if (role == DeviceRole.ACTIVE) stopMirroring() else applyPassiveFrame(frame)
    }

    // A device that is not playing follows without ever loading anything.
    //
    // That is the invariant the whole subsystem rests on, and it is structural:
    // nothing on this path touches the engine, so a passive device cannot become
    // a second stream however the frames arrive. What it shows instead is the
    // active device's track and a progress bar moved between frames.
    //
    // It pauses rather than stops, because a stop tears the bar down and a
    // viewer watching another room's playback wants to see where it has got to.
    protected open fun applyPassiveFrame(frame: MusicPlayerState) {
        mirrored.value = ConnectMirror(
            item = frame.item,
            isPlaying = frame.isPlaying,
            positionMs = frame.progressMs,
            durationMs = frame.durationMs,
        )

        scope.launch { player.pause(remote) }

        if (frame.isPlaying) startMirroring() else stopTicking()
    }

    // Between frames, the bar moves on its own.
    //
    // A server broadcasts on change rather than continuously, so a bar drawn
    // only from frames sits still for a whole track and then jumps. This walks
    // it forward and every arriving frame corrects it, which is the same thing
    // the platforms' own lock screens do with a position and a rate.
    //
    // It ends the moment the bar can no longer move — paused, or arrived at the
    // end of the track. A ticker that kept waking four times a second to write
    // the value it already holds is a wakeup per second per idle device, and on
    // a phone that is battery spent drawing nothing.
    private fun startMirroring() {
        if (ticking?.isActive == true) return

        ticking = scope.launch {
            while (isActive && mirrored.value.isAdvancing) {
                delay(MIRROR_TICK_MS)
                mirrored.value = mirrored.value.advancedBy(MIRROR_TICK_MS)
            }
        }
    }

    private fun stopTicking() {
        ticking?.cancel()
        ticking = null
    }

    private fun stopMirroring() {
        stopTicking()
        mirrored.value = ConnectMirror()
    }

    // What every device follows, whichever role it is in.
    //
    // Repeat, shuffle and the queue are the session rather than the playback: a
    // passive device showing a different queue from the one playing is a viewer
    // looking at the wrong list, and it becomes the wrong list to play from the
    // moment they take over.
    protected open fun applyUniversalSettings(frame: MusicPlayerState) {
        val upcoming: List<PlaylistItem> = listOfNotNull(frame.item) + frame.playlist

        scope.launch {
            player.queue(upcoming)
            player.repeatState(frame.repeatState, remote)
            player.shuffleState(
                if (frame.shuffleState) ShuffleState.ON else ShuffleState.OFF,
                remote,
            )
        }
    }

    // Marked as the server's doing, which is what stops every one of these
    // becoming an outbound command. The guards read the source and the applier
    // is the only thing that sets it.
    private val remote = ActionOptions(source = ActionSource.REMOTE)

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

// Four times a second, which is what a progress bar needs to look continuous and
// is cheap enough to run on a device that is otherwise doing nothing.
private const val MIRROR_TICK_MS = 250L
