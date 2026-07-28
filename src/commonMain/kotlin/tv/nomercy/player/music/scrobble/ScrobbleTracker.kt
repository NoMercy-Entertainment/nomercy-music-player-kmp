// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.music.scrobble

import kotlin.math.min

// When a track counts as listened to.
//
// From the web's `scrobble` plugin. The reporting backend is somebody else's —
// Last.fm, ListenBrainz, the NoMercy server's own activity endpoint — and what
// is here is the part every one of them needs and none of them defines: how
// much of a track a person has to actually hear before it counts.
//
// The rules are Last.fm's, because they are the ones every scrobbling service
// converged on and a listener with two services connected should not see
// different history in each:
//
//   - half the track, or four minutes, whichever comes first
//   - nothing under thirty seconds is ever reported
//   - once per track, however long it stays on screen after that
//
// Deliberately no network and no backend interface. This decides; something
// else sends.

/** The thresholds, all overridable, all defaulting to Last.fm's. */
public data class ScrobbleRules(
    val thresholdRatio: Double = LASTFM_RATIO,
    val thresholdSeconds: Double = LASTFM_CAP_SECONDS,
    val minDurationSeconds: Double = LASTFM_FLOOR_SECONDS,
)

/**
 * Accumulates listened time for one track at a time.
 *
 * Fed position updates rather than reading a clock, so the decision is
 * testable without one and identical on every platform.
 */
public class ScrobbleTracker(private val rules: ScrobbleRules = ScrobbleRules()) {

    private var trackId: String? = null
    private var listened: Double = 0.0
    private var lastPosition: Double = 0.0
    private var reported: Boolean = false

    /** Seconds actually heard of the current track. */
    public fun listenedSeconds(): Double = listened

    /** Whether this track has already been reported. */
    public fun hasScrobbled(): Boolean = reported

    public fun currentTrack(): String? = trackId

    /** A new track. Everything resets, including the once-per-track latch. */
    public fun startTrack(id: String) {
        trackId = id
        listened = 0.0
        lastPosition = 0.0
        reported = false
    }

    /**
     * A position update.
     *
     * Only advances the counter by a plausible tick. A normal update moves the
     * position by well under a second; anything larger is a seek or a track
     * change, and counting it would let somebody scrobble an album by dragging
     * the scrubber across it.
     *
     * Backwards movement counts as nothing rather than as negative time — a
     * seek back is not un-listening to what was already heard.
     */
    public fun advanceTo(positionSeconds: Double) {
        val delta: Double = positionSeconds - lastPosition
        if (delta > 0.0 && delta < MAX_PLAUSIBLE_TICK_SECONDS) {
            listened += delta
        }
        lastPosition = positionSeconds
    }

    /**
     * Whether this track should be reported now, latching so it is reported
     * once.
     *
     * Returns true exactly once per track, on the update that crosses the
     * threshold. A caller that asks again gets false, which is what makes it
     * safe to ask on every tick and on `ended`.
     */
    public fun shouldScrobble(durationSeconds: Double): Boolean {
        if (!crossedThreshold(durationSeconds)) return false

        reported = true
        return true
    }

    // Split out so the latch above is one line and cannot be skipped by a
    // future early return added among the conditions.
    private fun crossedThreshold(durationSeconds: Double): Boolean {
        if (trackId == null || reported) return false
        if (!durationSeconds.isFinite() || durationSeconds < rules.minDurationSeconds) return false

        val threshold: Double = min(
            durationSeconds * rules.thresholdRatio,
            rules.thresholdSeconds,
        )
        return listened >= threshold
    }

    /** Clears everything, for a player being torn down. */
    public fun reset() {
        trackId = null
        listened = 0.0
        lastPosition = 0.0
        reported = false
    }
}

/** Half the track. */
public const val LASTFM_RATIO: Double = 0.5

/** Or four minutes, whichever comes first. */
public const val LASTFM_CAP_SECONDS: Double = 240.0

/** And never anything shorter than this. */
public const val LASTFM_FLOOR_SECONDS: Double = 30.0

/**
 * The largest position jump counted as listening.
 *
 * A player ticks several times a second, so anything approaching two is a seek.
 * Without this, dragging the scrubber across a track scrobbles it.
 */
public const val MAX_PLAUSIBLE_TICK_SECONDS: Double = 2.0
