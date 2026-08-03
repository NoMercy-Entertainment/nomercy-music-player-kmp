// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.music.queue

import tv.nomercy.player.core.media.PlaylistItem

/**
 * Port for "find similar items given a seed item."
 *
 * Reserved for a future radio mode or "more like this", and for
 * [SmartShuffleGenerator] when tag-based similarity is not enough. Nothing wires
 * it today and no adapter ships: a consumer supplies its own, whether that is
 * the NoMercy media server's recommendation endpoint, audio features, tags, an
 * embedding, or an outside service.
 *
 * Defined rather than deferred because the shape is the part that is expensive
 * to change later — a feature that arrives to find no contract designs one under
 * time pressure, and every consumer that already implemented something else has
 * to move.
 */
public interface SimilarityEngine<T : PlaylistItem> {

    /** Human-readable identifier. Used in logging and debug tooling. */
    public val id: String

    /**
     * Items similar to [seed], most similar first.
     *
     * Empty when there is nothing to offer, which is an answer rather than a
     * failure: a radio mode that stops is better than one that repeats.
     */
    public suspend fun findSimilar(seed: T, options: SimilarityQuery = SimilarityQuery()): List<T>
}

/** Tuning knobs for a [SimilarityEngine.findSimilar] query. */
public data class SimilarityQuery(
    /** Most results to return. The default is the implementation's own. */
    val limit: Int? = null,
    /** Items already in the queue, which a result must not repeat. */
    val excludeIds: List<String> = emptyList(),
    /** Least similarity worth returning, 0 to 1 on the implementation's scale. */
    val minScore: Double? = null,
)
