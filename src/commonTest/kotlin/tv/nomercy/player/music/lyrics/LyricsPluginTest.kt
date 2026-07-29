// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.music.lyrics

import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import tv.nomercy.player.core.events.CoreEvents
import tv.nomercy.player.core.events.CueEvent
import tv.nomercy.player.core.events.ItemChange
import tv.nomercy.player.core.events.TimeUpdate
import tv.nomercy.player.core.media.MediaItem
import tv.nomercy.player.core.ports.CueParser
import tv.nomercy.player.core.ports.CueParserRegistry
import tv.nomercy.player.testing.FakePlayer
import tv.nomercy.player.testing.testPlugin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

// Two lines of LRC, parsed by a real parser through the real registry.
//
// A stub that returned cues directly would skip the half most likely to be
// wrong: whether the url reaches a parser at all. This registers one and lets
// the plugin find it, which is the path a consumer takes.
private class LrcParser : CueParser {
    override val id: String = "lrc"

    override fun canParse(url: String, contentType: String?): Boolean = url.endsWith(".lrc")

    override fun parse(raw: String, baseUrl: String?): List<CueEvent> =
        raw.lineSequence()
            .filter { it.isNotBlank() }
            .mapIndexed { index, line ->
                val at: Double = line.substringAfter('[').substringBefore(']').toDouble()
                CueEvent(
                    id = "line-$index",
                    startTime = at,
                    endTime = at + LINE_SECONDS,
                    text = line.substringAfter(']'),
                )
            }
            .toList()
}

private class Canned(private val body: String?) : LyricsSource {
    var asked: String? = null

    override suspend fun fetch(url: String): String? {
        asked = url
        return body
    }
}

private const val LINE_SECONDS = 2.0
private const val TRACK_SECONDS = 10.0
private const val LYRICS = "[0.0]first\n[2.0]second"

class LyricsPluginTest {

    private fun itemChange(id: String) =
        ItemChange(item = MediaItem(id = id, url = "https://example.test/$id.mp3"), index = 0)

    private fun timeUpdate(seconds: Double) =
        TimeUpdate(time = seconds, duration = TRACK_SECONDS, percentage = seconds / TRACK_SECONDS)

    private fun registry(): CueParserRegistry = CueParserRegistry().apply { register(LrcParser()) }

    private fun plugin(source: LyricsSource, autoFetch: Boolean = true) = LyricsPlugin(
        source = source,
        parsers = registry(),
        opts = LyricsOptions(getLyricsUrl = { "https://example.test/${it.id}.lrc" }, autoFetch = autoFetch),
    )

    @Test
    fun aTracksLyricsAreFetchedParsedAndAttached() = runTest {
        val source = Canned(LYRICS)
        val subject = plugin(source)

        testPlugin(subject, FakePlayer(scope = this)) { player, _ ->
            player.emit(CoreEvents.Item, itemChange("song"))
            // The fetch is a coroutine on the plugin's scope, so nothing has
            // happened until the scheduler runs it. Asserting straight after
            // the emit measures the moment before the work started.
            advanceUntilIdle()

            assertEquals("https://example.test/song.lrc", source.asked)
            assertEquals(listOf("first", "second"), subject.lines().map { it.text })
        }
    }

    @Test
    fun timeSelectsTheLineThatIsPlaying() = runTest {
        val subject = plugin(Canned(LYRICS))

        testPlugin(subject, FakePlayer(scope = this)) { player, _ ->
            player.emit(CoreEvents.Item, itemChange("song"))
            advanceUntilIdle()

            player.emit(CoreEvents.Time, timeUpdate(0.5))
            assertEquals("first", subject.line()?.text)

            player.emit(CoreEvents.Time, timeUpdate(2.5))
            assertEquals("second", subject.line()?.text)

            player.emit(CoreEvents.Time, timeUpdate(9.0))
            assertNull(subject.line())
        }
    }

    // Leaving the previous track's words up is worse than showing none: it is
    // confidently wrong.
    @Test
    fun aNewTrackClearsTheOldWords() = runTest {
        val subject = plugin(Canned(null))

        testPlugin(subject, FakePlayer(scope = this)) { player, _ ->
            subject.attach(listOf(CueEvent(startTime = 0.0, endTime = 5.0, text = "stale")))

            player.emit(CoreEvents.Item, itemChange("next"))

            assertTrue(subject.lines().isEmpty(), "expected no lines, got ${subject.lines()}")
        }
    }

    // A format nobody registered a parser for is a consumer's missing line of
    // setup, and reporting "no lyrics" would send them looking at the file.
    @Test
    fun anUnknownFormatSaysSoRatherThanReportingNoLyrics() = runTest {
        val subject = LyricsPlugin(
            source = Canned("whatever this is"),
            parsers = CueParserRegistry(),
            opts = LyricsOptions(getLyricsUrl = { "https://example.test/song.karaoke" }),
        )
        var reported: String? = null

        testPlugin(subject, FakePlayer(scope = this)) { player, _ ->
            val watching = player.on(LyricsEvents.NoParserOnPlayer) { reported = it }

            player.emit(CoreEvents.Item, itemChange("song"))
            advanceUntilIdle()

            assertEquals("https://example.test/song.karaoke", reported)

            watching.dispose()
        }
    }

    // Ships inert. Adding the plugin must not reach the network by itself.
    @Test
    fun autoFetchOffLeavesTheSourceAlone() = runTest {
        val source = Canned(LYRICS)
        val subject = plugin(source, autoFetch = false)

        testPlugin(subject, FakePlayer(scope = this)) { player, _ ->
            player.emit(CoreEvents.Item, itemChange("song"))
            advanceUntilIdle()

            assertNull(source.asked)
        }
    }
}
