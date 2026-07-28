// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.music.queue

// What comes next, as a decision the consumer can replace.
//
// From the web's `auto-advance` plugin and its IPlaylistGenerator port. The
// advance itself is the boring half; this is the half worth having, because
// "what plays after this" is where radio mode, server recommendations and
// mood ordering live, and a player that hardcoded `index + 1` closes all of
// them off.
//
// Indices rather than items, exactly as on the web. A generator that returned
// an item could return one that is not in the queue, and then the player is
// playing something the queue does not know about — which is how a "next"
// button and a now-playing list end up disagreeing.

/**
 * Resolves the next and previous index for a queue.
 *
 * [currentIndex] is -1 when nothing is playing. Null back means end of queue,
 * not an error: a finished playlist is a normal outcome and a generator that
 * threw would make it an exceptional one.
 */
public interface PlaylistGenerator {
    /** Named, because it shows up in a log and in the testbed's plugin tree. */
    public val id: String

    public fun next(size: Int, currentIndex: Int): Int?

    public fun previous(size: Int, currentIndex: Int): Int?
}

/**
 * In order, which is what a player does without being told otherwise.
 *
 * This is the implicit default: the plugin with no generator behaves
 * identically by delegating to the player's own next().
 */
public class LinearPlaylistGenerator : PlaylistGenerator {
    override val id: String = "linear"

    override fun next(size: Int, currentIndex: Int): Int? =
        (currentIndex + 1).takeIf { it in 0 until size }

    override fun previous(size: Int, currentIndex: Int): Int? =
        (currentIndex - 1).takeIf { it in 0 until size }
}

/**
 * Shuffle that does not repeat itself.
 *
 * The web's smart shuffle scores candidates by genre and decade to avoid two
 * of the same back to back. Those fields live on the item, and this library
 * has no item type carrying them yet — so this ports the part that does not
 * need them: never returning an index already played until the queue is
 * exhausted.
 *
 * That is deliberately less than the web does, and it is written down rather
 * than quietly approximated. A uniform random shuffle repeats tracks within a
 * short window often enough that people notice and call it broken; a shuffle
 * that plays two songs by the same artist in a row is a preference. The first
 * is worth fixing without the item model, the second is not possible without
 * it.
 *
 * [random] is injected so a shuffle can be tested at all. A generator reaching
 * for a global random is one whose behaviour can only be asserted
 * statistically, which in practice means not asserted.
 */
public class NonRepeatingShuffleGenerator(
    private val random: (Int) -> Int = { bound -> kotlin.random.Random.nextInt(bound) },
) : PlaylistGenerator {
    override val id: String = "shuffle"

    private val played: MutableList<Int> = mutableListOf()

    override fun next(size: Int, currentIndex: Int): Int? {
        if (size <= 0) return null
        if (currentIndex in 0 until size) played += currentIndex

        val remaining: List<Int> = (0 until size).filter { it !in played }
        if (remaining.isEmpty()) {
            // The queue is exhausted. Clearing here rather than returning null
            // means a repeating playlist starts a fresh shuffle instead of
            // replaying the same order, which is what a listener expects the
            // second time round.
            played.clear()
            return null
        }

        return remaining[random(remaining.size)]
    }

    /**
     * Back through what was actually played, not back through the queue.
     *
     * Pressing previous on a shuffled queue and getting the track before it in
     * the LIST is the single most confusing thing a shuffle can do: the track
     * it plays is one the listener has never heard.
     */
    override fun previous(size: Int, currentIndex: Int): Int? =
        played.lastOrNull { it != currentIndex && it in 0 until size }
}
