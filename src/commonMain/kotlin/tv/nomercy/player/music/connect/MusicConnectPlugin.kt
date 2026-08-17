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
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import tv.nomercy.player.core.controllers.ComposedPlayer
import tv.nomercy.player.core.events.BeforeEvent
import tv.nomercy.player.core.events.CoreEvents
import tv.nomercy.player.core.events.SeekPosition
import tv.nomercy.player.core.events.Subscription
import tv.nomercy.player.core.media.PlaylistItem
import tv.nomercy.player.core.player.ActionOptions
import tv.nomercy.player.core.player.ActionSource
import tv.nomercy.player.core.player.PlayState
import tv.nomercy.player.core.player.ShuffleState
import tv.nomercy.player.core.plugin.Plugin
import tv.nomercy.player.core.plugin.PluginManifest
import tv.nomercy.player.music.MusicEvents
import kotlin.math.abs

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

    private var clockSync: Job? = null

    private var positionReports: Job? = null

    private val ticker = ConnectMirrorTicker(scope)

    private var loadContinuation: List<Subscription> = emptyList()

    private var playingShield: OptimisticShield? = null

    // Until when a freshly promoted device ignores a pause. Zero means it is not
    // settling, which is the state it spends nearly all its life in.
    private var settlingUntilMs: Long = 0

    // Mirror of settlingUntilMs for the opposite direction: armed by a local
    // Pause/Stop, ignores an incoming stale isPlaying=true. Can't share a
    // field with settlingUntilMs — that one must let a fresh promotion play.
    private var pauseIntentUntilMs: Long = 0

    // The track a local crossfade has already swapped the audio to, while the
    // server is still broadcasting the one before it. Null whenever no crossfade
    // is outstanding.
    private var crossfadeTargetId: String? = null

    // When this device last moved itself past a track. The hub keeps sending the
    // old one for a moment afterwards, and those frames have to be told apart
    // from a viewer deliberately choosing that track from somewhere else — which
    // is a question about when the server sent it, not about which track it is.
    private var advanceShield: OptimisticShield? = null

    // Until when this device's own start is protected from a session end.
    //
    // Deliberately not settlingUntilMs, which a SERVER-driven promotion also
    // arms: a device the server promoted was promoted with a track, so a frame
    // with no item after it is a real session end and has to stop. This window
    // is armed only by a start that began here, where the server has not been
    // told what is playing yet and its own answer carries no item.
    private var localStartUntilMs: Long = 0

    // What a passive device draws. Empty on the active one, which renders from
    // its own player because it is the thing actually playing.
    public val mirror: StateFlow<ConnectMirror> get() = ticker.mirror

    public open val role: DeviceRole get() = resolveRole(activeDeviceId, channel.deviceId)

    public open val isActiveDevice: Boolean get() = role == DeviceRole.ACTIVE

    // How far the server's clock is from this one. Zero until measured, which
    // makes an unmeasured device fall back to local time rather than refuse to
    // mirror — a transport that cannot answer is a reason to be approximate, not
    // a reason to stop.
    internal var serverClockOffsetMs: Long = 0
        private set

    // The server's clock as this device best understands it. Everything that
    // compares a frame against "now" goes through here: the shield deciding
    // whether a frame predates a press, and the interpolation deciding how far
    // the bar has moved since the position was taken. Both are wrong by the
    // whole skew if it reads the local clock instead, and on a phone whose time
    // is a few seconds out that is a bar in the wrong place all session.
    protected open fun serverNowMs(): Long = nowMs() + serverClockOffsetMs

    // One measurement round, kept public so a host can re-measure after a
    // reconnect — the offset it took before the network changed is the one thing
    // that will not have survived it.
    //
    // Best of several rather than an average. A sample that went out and came
    // back quickly was queued behind less, so its guess at one-way latency is
    // closer to true; averaging it with a slow one moves the answer toward the
    // worse sample rather than the better.
    public suspend fun syncClock() {
        var bestRtt: Long = Long.MAX_VALUE

        // Starts from the offset already held, which is what makes a round where
        // nothing answered leave it alone. A measurement that failed is not a
        // measurement of zero, and treating it as one swings every judgement by
        // the whole skew until the next success.
        var bestOffset: Long = serverClockOffsetMs

        repeat(CLOCK_SAMPLES) {
            val sentAtMs: Long = nowMs()
            val serverMs: Long? = channel.serverTimeMs()
            val receivedAtMs: Long = nowMs()

            val rtt: Long = receivedAtMs - sentAtMs
            if (serverMs != null && rtt < bestRtt) {
                bestRtt = rtt
                bestOffset = clockOffsetMs(serverMs, sentAtMs, receivedAtMs)
            }
        }

        serverClockOffsetMs = bestOffset
    }

    // This device's own clock, which is what bounds the shield. Separate from
    // the server's on purpose: a shield measured on a clock the server controls
    // would never expire while that clock was wrong, and the point of the bound
    // is to recover from exactly that.
    protected open fun nowMs(): Long = player.now()

    override fun use() {
        on(CoreEvents.BeforePlay) { event -> guard(event, ConnectCommand.PLAY) }
        on(CoreEvents.BeforePause) { event -> guard(event, ConnectCommand.PAUSE) }
        on(CoreEvents.BeforeStop) { event -> guard(event, ConnectCommand.STOP) }
        on(CoreEvents.BeforeNext) { event -> guard(event, ConnectCommand.NEXT) }
        on(CoreEvents.BeforePrevious) { event -> guard(event, ConnectCommand.PREVIOUS) }

        on(CoreEvents.BeforeSeek, ::guardSeek)

        // Watched rather than reported to. A crossfade is started through the
        // player, and requiring the caller to also tell this plugin about it
        // would make correctness depend on two calls being kept in step by
        // whoever wires them up.
        on(MusicEvents.CrossfadeStart) { crossfade ->
            crossfadeTargetId = crossfade.to.id
            advanceShield = armed()
        }

        // Collected on the scope this plugin was given rather than on the
        // player's own, so one scope owns both directions of the conversation
        // and a caller can see where it ends. Cancelled explicitly below,
        // because a subscription outliving the plugin keeps answering a hub on
        // behalf of a player that is gone.
        subscription = scope.launch {
            channel.frames.collect { frame -> applyServerFrame(frame) }
        }

        // Measured once now and again on a cadence, because a clock that agreed
        // at connect time drifts, and a device suspended in a pocket comes back
        // with a different answer than it went in with.
        scope.launch { syncClock() }
        clockSync = interval(CLOCK_SYNC_PERIOD_MS) { scope.launch { syncClock() } }
        positionReports = interval(POSITION_REPORT_PERIOD_MS) { reportPositionNow() }
    }

    override fun dispose() {
        subscription?.cancel()
        subscription = null
        clockSync?.cancel()
        clockSync = null
        positionReports?.cancel()
        positionReports = null
        cancelLoadContinuation()
        ticker.dispose()
    }

    // What the server said, once the gate has let it through.
    protected open fun applyServerFrame(frame: MusicPlayerState) {
        val nextSeq: Long = nextAppliedSeqOrNull(frame.seq, lastAppliedSeq) ?: return
        lastAppliedSeq = nextSeq

        // No item is the session ending, and it ends everywhere at once. The
        // device that was playing stops and every other one stops mirroring,
        // which is why this happens before the role is reconciled — after it,
        // the device that just stopped being active would take the passive
        // branch and start following a session that no longer exists.
        val item: PlaylistItem = frame.item ?: run {
            // Unless the server produced it before it heard this device start.
            // Then it is not the session ending, it is the state from before the
            // session began, and adopting it stops the track the viewer just
            // pressed play on — measured on an SM-A137F as the decoder going
            // RUNNING and RELEASED within eight milliseconds, no audio, no
            // error. An unstamped frame is held too: a server too old to stamp
            // cannot be placed either side of the press, and silently killing
            // playback is the worse of the two ways to be wrong.
            // Unless this device just started playing and the server is only
            // now acknowledging it.
            //
            // Measured on an SM-A137F: the frame that killed playback arrived
            // thirty milliseconds AFTER the press, named this very device, and
            // carried no item — the server had processed the device claim and
            // not yet the track. Answering that with a stop tore down the audio
            // the viewer had just started, decoder RUNNING to RELEASED in eight
            // milliseconds with no error anywhere. A null item means the session
            // is over everywhere EXCEPT in the gap this device opened itself.
            if (frame.deviceId == channel.deviceId && nowMs() < localStartUntilMs) return

            activeDeviceId = null
            cancelLoadContinuation()
            ticker.clear()
            scope.launch { player.stop(remote) }
            return
        }

        // A frame carrying an item is the server caught up, whatever its clock
        // says — the window has done its job and holding it open longer would
        // only delay a real session end.
        localStartUntilMs = 0

        val wasActive: Boolean = role == DeviceRole.ACTIVE
        activeDeviceId = frame.deviceId

        // This device's own level, applied before the staleness gate below.
        // Living in applyUniversalSettings meant a frame dropped as stale — one
        // naming a track this device had already moved past — took the volume
        // change with it, so a level sent to a device that was mid-advance was
        // silently discarded (measured on the living-room TV: the phone
        // addressed SetDeviceVolumeCommand correctly and nothing moved).
        ownVolumeIn(frame, channel.deviceId)?.let { own -> scope.launch { player.volume(own, remote) } }

        // One coroutine for the whole frame, in order. Two would race: the queue
        // is written by the settings and read by the load, and a load that
        // arrived first would look for a track the player has not been given.
        scope.launch {
            // Read before the settings rewrite the queue. Afterwards the current
            // item IS the frame's item, so a track change compared against it
            // always looks like no change — the engine keeps the old song while
            // every other device moves on.
            val heldItemId: String? = player.item()?.id

            // A frame the server sent before it heard this device move on is
            // dropped whole rather than partly applied. Letting it through to
            // the settings would rewrite the queue around the song that just
            // ended, and the next frame would rewrite it back.
            val overtaken: Boolean = heldItemId != item.id && item.id != crossfadeTargetId
            val stale: Boolean = overtaken && advanceShield.precedes(frame.serverTimeMs, nowMs())

            if (!stale) {
                applyUniversalSettings(frame)
                if (role == DeviceRole.ACTIVE) {
                    applyActiveFrame(frame, item, justBecameActive = !wasActive, heldItemId = heldItemId)
                } else {
                    applyPassiveFrame(frame, item)
                }
            }
        }
    }

    // A device that is not playing follows without ever loading anything.
    //
    // That is the invariant the whole subsystem rests on, and it is structural:
    // nothing on this path touches the engine except to keep it quiet, so a
    // passive device cannot become a second stream however the frames arrive.
    //
    // It pauses rather than stops, because a stop tears the bar down and a
    // viewer watching another room's playback wants to see where it has got to.
    protected open suspend fun applyPassiveFrame(frame: MusicPlayerState, item: PlaylistItem) {
        val held: Boolean = playingShield.holds(frame.serverTimeMs, nowMs())
        if (!held) playingShield = null

        // The cursor moves, the engine is asked for nothing — the same verb the
        // crossfade path uses. Without it a passive device kept the track it
        // held when it went passive: its notification, lock screen and every
        // reader of player.item() named the wrong song for the rest of the
        // session.
        val index: Int = player.queue().indexOfFirst { it.id == item.id }
        if (index >= 0) player.seekToIndex(index + 1)

        ticker.show(
            ConnectMirror(
                item = item,
                isPlaying = if (held) mirror.value.isPlaying else frame.isPlaying,
                // Corrected the same way the active device's seek is, and for
                // the same reason: the frame describes a moment that has already
                // passed. A bar drawn at the raw number is always behind by
                // however long the frame took to arrive, which on a device with
                // a skewed clock is behind by the whole skew as well.
                positionMs = adjustedPositionMs(frame, serverNowMs()),
                durationMs = frame.durationMs,
            ),
        )

        player.pause(remote)
    }

    // The device that is actually playing, brought back in line with the server.
    //
    // Three cases, and they are not interchangeable. Taking over from another
    // device, or a track change, means the engine holds the wrong source or none
    // — so it loads and waits, because seeking a source that is not set yet does
    // nothing and playing it starts the wrong track. The same track already
    // loaded is a correction: seek only if the drift is audible, then match the
    // server's play or pause.
    protected open suspend fun applyActiveFrame(
        frame: MusicPlayerState,
        item: PlaylistItem,
        justBecameActive: Boolean,
        heldItemId: String?,
    ) {
        ticker.clear()
        playingShield = null
        if (justBecameActive) settlingUntilMs = nowMs() + SETTLEMENT_MS

        val target: Double = adjustedSeekSeconds(frame, serverNowMs())
        val isTrackChange: Boolean = heldItemId != item.id

        // The server catching up to a crossfade this device already performed.
        // The audio is on the new track, so reloading it would restart the song
        // the viewer is a few seconds into — the cursor moves and nothing else.
        if (isTrackChange && item.id == crossfadeTargetId) {
            crossfadeTargetId = null

            // The cursor moves and the engine is asked for nothing, which is the
            // whole point: the audio is already there.
            val index: Int = player.queue().indexOfFirst { it.id == item.id }
            if (index >= 0) player.seekToIndex(index + 1)

            matchPlaybackTo(frame)
            return
        }

        if (justBecameActive || isTrackChange) {
            // Armed before the load, not after. The load reports readiness the
            // moment the engine holds the source, and against a fast engine that
            // is inside this call — a continuation armed afterwards would wait
            // for a signal that had already gone past.
            armLoadContinuation(frame, target)
            player.item(item.id)
            return
        }

        if (abs(player.time() - target) > DRIFT_TOLERANCE_SECONDS) player.time(target, remote)
        matchPlaybackTo(frame)
    }

    // What happens once the engine has the track.
    //
    // Both arms end the continuation, including the failure one: a load that
    // errored is never going to report ready, and leaving the subscription armed
    // means the NEXT track's readiness runs this frame's stale seek.
    private fun armLoadContinuation(frame: MusicPlayerState, seekSeconds: Double) {
        cancelLoadContinuation()

        val ready: Subscription = on(CoreEvents.MediaReady) {
            cancelLoadContinuation()
            scope.launch {
                player.time(seekSeconds, remote)
                matchPlaybackTo(frame)
            }
        }
        // Both failure surfaces. A stream that would not open reports one, a
        // playlist that would not resolve reports the other, and either of them
        // means the readiness this is waiting for is never coming.
        val streamFailed: Subscription = on(CoreEvents.StreamError) { cancelLoadContinuation() }
        val failed: Subscription = on(CoreEvents.Error) { cancelLoadContinuation() }

        loadContinuation = listOf(ready, streamFailed, failed)
    }

    private fun cancelLoadContinuation() {
        loadContinuation.forEach { it.dispose() }
        loadContinuation = emptyList()
    }

    // Only when it differs. Playing a player that is already playing is an event
    // a chrome renders as a fresh start, and on a hub that broadcasts several
    // times a second it would render one per frame.
    private suspend fun matchPlaybackTo(frame: MusicPlayerState) {
        val playing: Boolean = player.playState() == PlayState.PLAYING

        // A pause this device is willing to hear. Inside the settlement window
        // it is a release from the device that just let go, arriving after the
        // promotion it crossed with.
        val pauseIsWanted: Boolean = !frame.isPlaying && nowMs() >= settlingUntilMs

        // A resume this device is willing to hear — see pauseIntentUntilMs.
        val playIsWanted: Boolean = frame.isPlaying && nowMs() >= pauseIntentUntilMs

        if (playIsWanted && !playing) player.play(remote)
        else if (pauseIsWanted && playing) player.pause(remote)
    }

    // What every device follows, whichever role it is in.
    //
    // Repeat, shuffle and the queue are the session rather than the playback: a
    // passive device showing a different queue from the one playing is a viewer
    // looking at the wrong list, and it becomes the wrong list to play from the
    // moment they take over.
    protected open suspend fun applyUniversalSettings(frame: MusicPlayerState) {
        val upcoming: List<PlaylistItem> = listOfNotNull(frame.item) + frame.playlist

        player.queue(upcoming)
        player.repeatState(frame.repeatState, remote)
        player.shuffleState(
            if (frame.shuffleState) ShuffleState.ON else ShuffleState.OFF,
            remote,
        )
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

        // PASSIVE — a real Connect frame named another device active — blocks.
        // NONE does not: it means no frame has ever arrived (never connected,
        // still negotiating, or the socket is down), and treating "unknown"
        // the same as "refused by another device" locked local playback out
        // permanently on any client that hadn't yet completed its first
        // Connect handshake (confirmed live, real TV, 2026-08-12 — playback
        // never advanced past TransportController's BeforePlay gate even
        // though nothing else was actually claiming the device).
        if (role == DeviceRole.PASSIVE) event.preventDefault()
        val seconds: Double = event.data.time

        // Same settlement window applyActiveFrame arms for justBecameActive —
        // a seek just told the server where this device now is, and the
        // server's own broadcast of that hasn't landed yet. A frame already
        // in flight (describing wherever this device was BEFORE the seek)
        // can still arrive in the gap and, unprotected, its isPlaying read
        // matchPlaybackTo() as a real pause request — a genuinely playing
        // local seek left paused by its own stale echo. Confirmed live, real
        // device, 2026-08-12: FAST_FORWARD advanced the position correctly
        // and the transport still ended up PAUSED.
        settlingUntilMs = nowMs() + SETTLEMENT_MS
        scope.launch { channel.playbackCommand(ConnectCommand.SEEK, seconds) }
    }

    private fun guard(event: BeforeEvent<ActionOptions>, command: String) {
        if (isEcho(event.data.source)) return

        // A genuine local PLAY with no device yet named active claims this one —
        // the old MusicPlayerStore.claimActiveForLocalPlaybackStart() equivalent.
        // Without this, a device that only ever started LOCAL playback (never
        // named by a server frame) stays DeviceRole.NONE forever: isActiveDevice
        // reads false while it is genuinely playing, which made
        // NoMercyApplication.onStop() disconnect the music hub on backgrounding
        // mid-playback, and left it exposed to applyServerFrame's session-end
        // branch on the next frame — confirmed live, real device, 2026-08-12
        // (radio and regular music both stopped silently while backgrounded).
        if (command == ConnectCommand.PLAY && role == DeviceRole.NONE) {
            claimActiveForLocalPlaybackStart()
        }

        // See pauseIntentUntilMs: guards against a stale isPlaying=true frame
        // resuming what this Pause/Stop just stopped.
        if (command == ConnectCommand.PAUSE || command == ConnectCommand.STOP) {
            pauseIntentUntilMs = nowMs() + SETTLEMENT_MS
        }

        if (isActiveDevice && command in ADVANCING_COMMANDS) advanceShield = armed()

        // See guardSeek's comment: only a confirmed PASSIVE role blocks.
        if (role == DeviceRole.PASSIVE) {
            showIntentBeforeTheServerAnswers(command)
            event.preventDefault()
        }
        scope.launch { channel.playbackCommand(command) }
    }

    // Optimistic, ahead of the round trip — the same reasoning as the deleted
    // MusicPlayerStore's own version: flipping activeDeviceId here keeps every
    // observer keyed off role/isActiveDevice correct immediately, even if the
    // server's ChangeDevice broadcast is delayed. A later frame naming another
    // device still demotes normally through applyServerFrame; this only sets
    // the optimistic starting point, never bypasses that.
    //
    // Also arms settlingUntilMs, same as applyActiveFrame does for a
    // server-driven promotion — without it the confirming frame's own
    // justBecameActive check reads false, since this flip already happened.
    private fun claimActiveForLocalPlaybackStart() {
        settlingUntilMs = nowMs() + SETTLEMENT_MS
        // A round trip's worth, the same budget the optimistic shield uses, and
        // for the same reason: it has to survive a bad connection.
        localStartUntilMs = nowMs() + OPTIMISTIC_SHIELD_MS
        activeDeviceId = channel.deviceId
        scope.launch { channel.changeDevice(channel.deviceId) }
    }

    /**
     * A genuine user-initiated start of a list on THIS device: claim the
     * session, then tell the server what is playing.
     *
     * Without the second half the server's session has no item, and an itemless
     * session is broadcast as an ended one — the device that started the music
     * stopped itself on the very next frame (play, seek, or the ~5s cadence),
     * and a handoff moved an empty session to a device that then had nothing to
     * play. Measured on two phones, 2026-08-17.
     *
     * Only a real tap calls this. A passive device mirroring the active one's
     * auto-advance must never claim, which is why this is not driven off the
     * item-changed event.
     */
    public fun startPlayback(type: String, listId: String, trackId: String) {
        // The server answers the claim before it has processed the start, and
        // that answer still names the PREVIOUS track. Applied, it loads and
        // plays the song the viewer just moved away from. This is the same
        // shield a local track change already uses, armed for the same reason.
        advanceShield = armed()
        claimActiveForLocalPlaybackStart()
        scope.launch { channel.startPlayback(type, listId, trackId) }
    }

    // Only the device the server considers active, and only while it is really
    // playing: a paused device is not stale, and a passive one reporting would
    // be telling the server about a position it got FROM the server.
    internal fun reportPositionNow() {
        if (!isActiveDevice) return
        if (player.playState() != PlayState.PLAYING) return
        val itemId: String = player.item()?.id ?: return
        scope.launch { channel.reportPosition(player.time(), itemId) }
    }

    private fun armed() = OptimisticShield(sentAtServerMs = serverNowMs(), sentAtLocalMs = nowMs())

    // A press on a device that is not the one playing still has to look like it
    // did something. The button flips now and is defended for a moment against
    // frames the server produced before it heard the press; a later frame is a
    // real answer, including a refusal, and it wins.
    private fun showIntentBeforeTheServerAnswers(command: String) {
        val intent: Boolean = when (command) {
            ConnectCommand.PLAY -> true
            ConnectCommand.PAUSE -> false
            else -> return
        }

        playingShield = armed()
        ticker.intend(intent)
    }
}

// The two that move this device off the track the server last named.
private val ADVANCING_COMMANDS = setOf(ConnectCommand.NEXT, ConnectCommand.PREVIOUS)
