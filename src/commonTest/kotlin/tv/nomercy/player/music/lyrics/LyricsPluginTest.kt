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
import tv.nomercy.player.core.cues.Cue
import tv.nomercy.player.core.cues.LrcPayload
import tv.nomercy.player.core.cues.TextPayload
import tv.nomercy.player.core.events.CoreEvents
import tv.nomercy.player.core.events.ItemChange
import tv.nomercy.player.core.events.TimeUpdate
import tv.nomercy.player.core.media.MediaItem
import tv.nomercy.player.core.ports.CueParser
import tv.nomercy.player.testing.FakePlayer
import tv.nomercy.player.testing.testPlugin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

private class Canned(private val body: String?) : LyricsSource {
    var asked: String? = null

    override suspend fun fetch(url: String): String? {
        asked = url
        return body
    }
}

private const val TRACK_SECONDS = 10.0

// A real LRC file, read by the parser the plugin ships with.
//
// The test used to hand the plugin a registry holding a hand-written parser, and
// that is exactly what production did not have: the registry was empty, so every
// url reported noParser and the plugin could not read a lyric file on any
// platform. A test that supplies the missing piece proves the piece works and
// says nothing about whether anybody installed it.
private const val LYRICS = "[00:00.00]first\n[00:02.00]second"

class LyricsPluginTest {

    private fun itemChange(id: String) =
        ItemChange(item = MediaItem(id = id, url = "https://example.test/$id.mp3"), index = 0)

    private fun timeUpdate(seconds: Double) =
        TimeUpdate(time = seconds, duration = TRACK_SECONDS, percentage = seconds / TRACK_SECONDS)

    // No parser registry to pass any more — the plugin always resolves through
    // the host, and FakePlayer seeds the same built-ins ComposedPlayer does.
    private fun plugin(source: LyricsSource, autoFetch: Boolean = true) = LyricsPlugin(
        source = source,
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
            assertEquals(listOf("first", "second"), subject.lines().map { it.payload.text })
        }
    }

    // The plugin's own promise: an LRC file and a VTT file both work. Neither did
    // — the registry it defaulted to was empty.
    @Test
    fun aVttLyricFileReadsAsWellAsAnLrcOne() = runTest {
        val subject = LyricsPlugin(
            source = Canned("WEBVTT\n\n00:00:00.000 --> 00:00:02.000\nfirst\n"),
            opts = LyricsOptions(getLyricsUrl = { "https://example.test/${it.id}.vtt" }),
        )

        testPlugin(subject, FakePlayer(scope = this)) { player, _ ->
            player.emit(CoreEvents.Item, itemChange("song"))
            advanceUntilIdle()

            assertEquals(listOf("first"), subject.lines().map { it.payload.text })
        }
    }

    @Test
    fun timeSelectsTheLineThatIsPlaying() = runTest {
        val subject = plugin(Canned(LYRICS))

        testPlugin(subject, FakePlayer(scope = this)) { player, _ ->
            player.emit(CoreEvents.Item, itemChange("song"))
            advanceUntilIdle()

            player.emit(CoreEvents.Time, timeUpdate(0.5))
            assertEquals("first", subject.line()?.payload?.text)

            player.emit(CoreEvents.Time, timeUpdate(2.5))
            assertEquals("second", subject.line()?.payload?.text)

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
            subject.attach(listOf(Cue<TextPayload>(start = 0.0, end = 5.0, payload = LrcPayload(text = "stale"))))

            player.emit(CoreEvents.Item, itemChange("next"))

            assertTrue(subject.lines().isEmpty(), "expected no lines, got ${subject.lines()}")
        }
    }

    // A format nobody registered a parser for is a consumer's missing line of
    // setup, and reporting "no lyrics" would send them looking at the file.
    @Test
    fun anUnknownFormatSaysSoRatherThanReportingNoLyrics() = runTest {
        // The default registry, so this also proves the built-ins do not answer
        // for a format they cannot read: a wrong parser fails after claiming the
        // file, which is worse than no parser.
        val subject = LyricsPlugin(
            source = Canned("whatever this is"),
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

    // The gap this closes: [CueParser] used to flatten every format into a
    // string, so enhanced LRC's per-word timing was read by `parseLrc` and then
    // discarded before a karaoke display ever saw it. This fails on the old
    // `List<CueEvent>` surface — `.text` has no words to read.
    @Test
    fun enhancedLrcWordTimingReachesTheConsumer() = runTest {
        val subject = plugin(Canned("[00:01.00]<00:01.00>Never <00:01.50>gonna <00:02.00>give"))

        testPlugin(subject, FakePlayer(scope = this)) { player, _ ->
            player.emit(CoreEvents.Item, itemChange("song"))
            advanceUntilIdle()

            val lrc = subject.lines().single().payload as LrcPayload
            assertEquals(listOf("Never", "gonna", "give"), lrc.words.map { it.text })
        }
    }

    // The gap this closes: the plugin used to default to its own
    // CueParserRegistry seeded only with the built-ins, and a host's own
    // registered parser was invisible to it. Registering a custom parser on the
    // HOST before the plugin ever runs, and getting it back rather than
    // NoParser, is the proof this now goes through one registry, not two.
    @Test
    fun aHostsCustomParserIsVisibleToThePlugin() = runTest {
        val karaoke = object : CueParser<LrcPayload> {
            override val id: String = "fillz:karaoke"
            override fun canParse(url: String, contentType: String?): Boolean = url.endsWith(".karaoke")
            override fun parse(raw: String, baseUrl: String?): List<Cue<LrcPayload>> =
                listOf(Cue(start = 0.0, end = 1.0, payload = LrcPayload(text = raw)))
        }
        val host = FakePlayer(scope = this)
        host.cueParsers.register(karaoke)

        val subject = LyricsPlugin(
            source = Canned("from the host's own parser"),
            opts = LyricsOptions(getLyricsUrl = { "https://example.test/${it.id}.karaoke" }),
        )

        testPlugin(subject, host) { player, _ ->
            player.emit(CoreEvents.Item, itemChange("song"))
            advanceUntilIdle()

            assertEquals("from the host's own parser", subject.lines().single().payload.text)
        }
    }
}
