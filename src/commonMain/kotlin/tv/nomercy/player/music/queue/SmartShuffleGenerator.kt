// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.music.queue

import tv.nomercy.player.core.media.PlaylistItem

// A track that says what it is.
//
// The reference declares this structurally, as a TaggedItem shape it casts an
// item to, so the tags are optional on purpose: a queue whose items carry none
// still shuffles, uniformly. An interface rather than fields on
// MusicPlaylistItem for the same reason — a host with no genre data should not
// have to implement two properties in order to say it has none.
public interface TaggedMusicItem : PlaylistItem {
    public val genres: List<String> get() = emptyList()

    // A string rather than a number, matching the reference's `number | string`
    // once both ends are compared by equality. "1980s" and 1980 are the same
    // fact and neither is more correct; what matters is that two items agree.
    public val decade: String? get() = null
}

// Shuffle that avoids playing the same genre or decade back to back.
//
// Ported from the reference's smart-shuffle. NonRepeatingShuffleGenerator says
// in its own documentation that the tag-aware half was not ported because
// nothing carried the tags, and SimilarityEngine's documentation names this
// class and says nothing wires it. Both were true; this is the missing half.
//
// The algorithm, in the reference's order, because the ORDER is what makes the
// result reproducible against it:
//
//  1. every index except the current one is a candidate
//  2. each is penalised once for sharing a genre with the current track and
//     once for sharing its decade
//  3. a small random amount is added, so equally-penalised candidates do not
//     come back in queue order
//  4. everything within a tenth of the best score is the pool, and the pick is
//     uniform inside it
//
// Step four is why the jitter is 0.5 and the band is 0.1: the jitter is large
// enough to reorder ties and small enough that it can never lift a penalised
// candidate above an unpenalised one, and the band then keeps the pool to
// candidates that genuinely tied rather than to whatever the jitter favoured.
//
// [random] is injected for the reason NonRepeatingShuffleGenerator gives: a
// generator reaching for a global random can only be asserted statistically,
// which in practice means not asserted.
public class SmartShuffleGenerator(
    private val random: () -> Double = { kotlin.random.Random.nextDouble() },
) : PlaylistGenerator {

    override val id: String = "smart-shuffle"

    private val played: MutableList<Int> = mutableListOf()

    // Without the items there is nothing to score, so this falls back to the
    // uniform pick rather than pretending. Stated rather than silent: a
    // consumer calling the count-only form gets a plain shuffle and should be
    // able to find out why from here.
    override fun next(size: Int, currentIndex: Int): Int? = uniformPick(size, currentIndex)

    override fun previous(size: Int, currentIndex: Int): Int? = backlogPick(size)

    override fun next(items: List<PlaylistItem>, currentIndex: Int): Int? {
        if (items.isEmpty()) return null
        if (items.size == 1) return 0

        // No empty-candidates guard: past the size checks above there are at
        // least two indices and removing one leaves at least one.
        val candidates: List<Int> = items.indices.filter { it != currentIndex }

        val current: TaggedMusicItem? = items.getOrNull(currentIndex) as? TaggedMusicItem
        val currentGenres: Set<String> = current?.genres.orEmpty().toSet()
        val currentDecade: String? = current?.decade

        val scored: List<Pair<Int, Double>> = candidates
            .map { index -> index to score(items[index], currentGenres, currentDecade) }
            .sortedByDescending { it.second }

        val best: Double = scored.first().second
        val pool: List<Int> = scored.filter { it.second >= best - TIE_BAND }.map { it.first }

        if (currentIndex >= 0) played += currentIndex

        return pool[(random() * pool.size).toInt().coerceIn(0, pool.size - 1)]
    }

    /**
     * Back through what was actually played.
     *
     * The reference falls back to a random index when the backlog is empty,
     * which is what this does too: on a shuffled queue the track BEFORE this
     * one in the list is one the listener has never heard, so going there is
     * worse than going somewhere new.
     */
    override fun previous(items: List<PlaylistItem>, currentIndex: Int): Int? = backlogPick(items.size)

    private fun score(item: PlaylistItem, currentGenres: Set<String>, currentDecade: String?): Double {
        val tagged: TaggedMusicItem? = item as? TaggedMusicItem
        val sharesGenre: Boolean = tagged?.genres.orEmpty().any { it in currentGenres }
        // Only when both are known. Two untagged tracks are not "the same
        // decade", they are two tracks nobody said anything about, and
        // penalising that would make an untagged queue score every candidate
        // identically and shuffle worse than a uniform one.
        val sharesDecade: Boolean = tagged?.decade != null && tagged.decade == currentDecade

        val penalty: Int = (if (sharesGenre) 1 else 0) + (if (sharesDecade) 1 else 0)

        return -penalty + random() * JITTER
    }

    private fun uniformPick(size: Int, currentIndex: Int): Int? {
        if (size <= 0) return null
        if (size == 1) return 0

        val candidates: List<Int> = (0 until size).filter { it != currentIndex }

        if (currentIndex >= 0) played += currentIndex

        return candidates[(random() * candidates.size).toInt().coerceIn(0, candidates.size - 1)]
    }

    private fun backlogPick(size: Int): Int? {
        if (size <= 0) return null

        val fromBacklog: Int? = played.removeLastOrNull()
        if (fromBacklog != null && fromBacklog < size) return fromBacklog

        return (random() * size).toInt().coerceIn(0, size - 1)
    }
}

// Large enough to reorder candidates that tied, small enough that it can never
// lift a penalised candidate over an unpenalised one.
private const val JITTER: Double = 0.5

// What counts as having tied.
private const val TIE_BAND: Double = 0.1
