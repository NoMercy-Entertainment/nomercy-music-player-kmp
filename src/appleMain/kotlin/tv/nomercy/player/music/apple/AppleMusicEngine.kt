// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.music.apple

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import tv.nomercy.player.core.media.PlaylistItem
import tv.nomercy.player.core.player.ActionOptions
import tv.nomercy.player.core.player.PlayState
import tv.nomercy.player.core.player.PlayerConfig
import tv.nomercy.player.core.player.PlayerState
import tv.nomercy.player.core.player.RepeatState
import tv.nomercy.player.core.player.ShuffleState
import tv.nomercy.player.core.plugin.Plugin
import tv.nomercy.player.core.ports.AudioBackend
import tv.nomercy.player.music.NMMusicPlayer
import tv.nomercy.player.music.item.MusicPlaylistItem
import tv.nomercy.player.music.defaultAudioBackend

/**
 * One track, in the shape the SwiftUI chrome's `NowPlaying` is built from.
 *
 * Flattened out of PlaylistItem on purpose. Every property Swift reads crosses
 * the Objective-C bridge, and a row redrawing at sixty frames reading four
 * getters off a queue of thirty items is thirty-six hundred crossings a second
 * for a list that changed once.
 */
public data class AppleTrack(
    val id: String,
    val title: String,
    val artist: String?,
    val album: String?,
    val artworkUrl: String?,
)

/**
 * Everything the music chrome reads, in one value.
 *
 * The same reasoning as the video snapshot: the row and the full player render
 * from one consistent picture, and a UI reading six getters at six moments can
 * draw a position from one frame beside a duration from another.
 */
public data class AppleMusicSnapshot(
    val playing: Boolean,
    val positionSeconds: Double,
    val durationSeconds: Double,
    val volume: Int,
    val muted: Boolean,
    val queue: List<AppleTrack>,
    val queueIndex: Int,
    val shuffled: Boolean,
    val repeatMode: String,
    val nowPlaying: AppleTrack?,
)

/**
 * The music engine, assembled for Apple and handed to Swift as plain calls.
 *
 * The counterpart of AppleVideoEngine, and it was the piece that made the whole
 * music route unreachable on this platform. The Swift package ships a finished
 * chrome — a full player, a mini row, a queue list, a scrubber, a television
 * view — bound to the `MusicChromePlayer` protocol, and nothing anywhere
 * conformed to it against a real engine. So the testbed's music pane said there
 * was no SwiftUI surface to mount, which read as a missing chrome when what was
 * missing was the twenty lines that hand it a player.
 *
 * A Kotlin facade rather than Swift reaching into NMMusicPlayer directly, for
 * the reason the video one gives: `Player.on` is generic over `EventKey<T>` and
 * the state is a `StateFlow`, and neither survives the Objective-C bridge in a
 * shape a Swift file wants to write.
 *
 * It decides nothing. No shuffle policy, no repeat ordering, no queue
 * behaviour — those live in core where both toolkits already share them, so a
 * difference between Apple and Compose has exactly one place it could have come
 * from and this is not it.
 */
public class AppleMusicEngine(
    public val backend: AudioBackend,
) {

    /**
     * The AVFoundation backend, for a caller with no reason to choose.
     *
     * A secondary constructor rather than a default argument. Kotlin/Native
     * drops defaults on the way out, so `AppleMusicEngine()` would be marked
     * unavailable in Swift and every call site would have to name the platform
     * backend by its generated file-class — which is `DefaultAudioBackend.apple.kt`
     * mangled, and not a name anybody should have to know.
     */
    public constructor() : this(defaultAudioBackend())

    // Main, because everything downstream of a snapshot is a SwiftUI update and
    // SwiftUI is not thread-safe. The same choice AppleVideoEngine makes.
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    public val player: NMMusicPlayer = NMMusicPlayer(audio = backend)

    // Every caller gets its own collector and they all run.
    //
    // A single slot here is what left the video clock reading 0:00 on iOS: the
    // second observer replaced the first, and the first was the one driving the
    // chrome. Registering in a SwiftUI body would accumulate one per redraw, so
    // register where the thing owning the engine is built and let dispose() end
    // them.
    private val collectors: MutableList<Job> = mutableListOf()

    public fun setup() {
        setup(PlayerConfig())
    }

    public fun setup(config: PlayerConfig) {
        scope.launch { player.setup(config) }
    }

    // Any, because Plugin is generic over its options and a generic Kotlin type
    // does not cross to Swift in a form a call site can name. The cast fails
    // loudly rather than silently doing nothing.
    //
    // The registry, not the player's suspending addPlugin. Launching it would
    // return before the plugin was registered, and the caller reads the id list
    // back off the registry on the next line to prove what it got — so the list
    // would come back empty from an engine that had registered five things a
    // moment later. The same seam AppleVideoEngine uses, for the same reason.
    @Suppress("UNCHECKED_CAST")
    public fun addPlugin(plugin: Any) {
        val registrable: Plugin<Any> = plugin as? Plugin<Any>
            ?: error("addPlugin takes a Plugin; got ${plugin::class.simpleName}")
        player.plugins.register(registrable)
    }

    public fun queue(items: List<PlaylistItem>) {
        scope.launch { player.queue(items) }
    }

    public fun load(item: PlaylistItem) {
        scope.launch { player.load(item) }
    }

    public fun observe(onState: (AppleMusicSnapshot) -> Unit) {
        collectors += scope.launch {
            player.stateFlow.collect { state: PlayerState -> onState(snapshotOf(state)) }
        }
    }

    public fun play() {
        scope.launch { player.play() }
    }

    public fun pause() {
        scope.launch { player.pause() }
    }

    public fun togglePlayPause() {
        scope.launch { if (player.state().playState == PlayState.PLAYING) player.pause() else player.play() }
    }

    public fun seek(seconds: Double) {
        scope.launch { player.time(seconds) }
    }

    public fun next() {
        scope.launch { player.next() }
    }

    public fun previous() {
        scope.launch { player.previous() }
    }

    // By loading the item at that position, because there is no jump-to-index on
    // the player and inventing one here would be a queue behaviour living
    // outside the queue. Out of range is ignored rather than clamped: a row the
    // chrome no longer shows is a stale tap, and playing its neighbour is worse
    // than doing nothing.
    public fun playQueueIndex(index: Int) {
        scope.launch { player.queue().getOrNull(index)?.let { item: PlaylistItem -> player.load(item) } }
    }

    public fun setShuffled(shuffled: Boolean) {
        scope.launch {
            player.shuffleState(if (shuffled) ShuffleState.ON else ShuffleState.OFF, ActionOptions())
        }
    }

    // The token, not the enum. A Kotlin enum crosses as an ObjC class whose
    // cases Swift cannot switch over exhaustively, and the token is the same
    // string the web sends over Connect — so the two clients say "one" the same
    // way rather than each mapping an ordinal.
    public fun setRepeat(mode: String) {
        scope.launch { player.repeatState(RepeatState.fromToken(mode), ActionOptions()) }
    }

    public fun setVolume(percent: Int) {
        scope.launch { player.volume(percent, ActionOptions()) }
    }

    public fun setMuted(muted: Boolean) {
        scope.launch { if (muted) player.mute() else player.unmute() }
    }

    public fun dispose() {
        collectors.forEach { collector: Job -> collector.cancel() }
        collectors.clear()
        scope.cancel()
    }

    private fun snapshotOf(state: PlayerState): AppleMusicSnapshot {
        val tracks: List<AppleTrack> = player.queue().map { item: PlaylistItem -> trackOf(item) }
        return AppleMusicSnapshot(
            playing = state.playState == PlayState.PLAYING,
            positionSeconds = state.time,
            durationSeconds = state.duration,
            volume = state.volume,
            muted = state.muted,
            queue = tracks,
            queueIndex = state.index,
            shuffled = state.shuffleState == ShuffleState.ON,
            repeatMode = state.repeatState.token,
            nowPlaying = state.item?.let { item: PlaylistItem -> trackOf(item) },
        )
    }

    // A music item carries the artist, the album and the cover; a bare
    // PlaylistItem carries none of them and is still a legal thing to queue.
    // Reading through the narrower interface where it is one, rather than
    // requiring the wider type, keeps a host's own row playable.
    //
    // image before cover, which is the order the web item reads them in — cover
    // is the older name hosts already populate.
    private fun trackOf(item: PlaylistItem): AppleTrack {
        val track: MusicPlaylistItem? = item as? MusicPlaylistItem
        return AppleTrack(
            id = item.id,
            title = track?.name ?: item.title.orEmpty(),
            artist = track?.artist,
            album = track?.album,
            artworkUrl = track?.image ?: track?.cover,
        )
    }
}
