// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

import XCTest
@testable import NoMercyMusicPlayerUI

/// The row at the bottom of the application.
///
/// What is worth asserting is which glyph reached the screen and whether pressing
/// it reached the player — neither readable from the view, and a control drawing
/// the wrong glyph is one a listener presses expecting the opposite.
@MainActor
final class MiniPlayerTests: XCTestCase {

    private let strings = MusicStrings()
    private let song = NowPlaying(id: "1", title: "Neon Sky", artist: "The Long Winter")

    func testAPausedTrackOffersPlay() {
        let model = MiniPlayerModel(player: FakeMusicChromePlayer(), strings: strings)

        XCTAssertEqual(model.transport.symbol, "play.fill")
        XCTAssertEqual(model.transport.label, strings.play)
    }

    func testAPlayingOneOffersPause() {
        let player = FakeMusicChromePlayer()
        player.togglePlayPause()
        let model = MiniPlayerModel(player: player, strings: strings)

        XCTAssertEqual(model.transport.symbol, "pause.fill")
        XCTAssertEqual(model.transport.label, strings.pause)
    }

    func testTheGlyphAndItsLabelCannotDisagree() {
        // Both other chromes in this ecosystem shipped a pause triangle
        // announcing itself as Play, and only a planted test found it.
        let player = FakeMusicChromePlayer()
        let model = MiniPlayerModel(player: player, strings: strings)
        let paused = model.transport

        player.togglePlayPause()
        let playing = MiniPlayerModel(player: player, strings: strings).transport

        XCTAssertNotEqual(paused.symbol, playing.symbol)
        XCTAssertNotEqual(paused.label, playing.label)
    }

    func testItNamesWhatIsPlaying() {
        let model = MiniPlayerModel(player: FakeMusicChromePlayer(nowPlaying: song), strings: strings)

        XCTAssertEqual(model.title, "Neon Sky")
        XCTAssertEqual(model.artist, "The Long Winter")
    }

    func testAndSaysSoWhenNothingIs() {
        // An empty line reads as something that failed to load. The honest
        // answer is that there is nothing.
        let model = MiniPlayerModel(player: FakeMusicChromePlayer(), strings: strings)

        XCTAssertEqual(model.title, strings.nothingPlaying)
        XCTAssertNil(model.artist)
    }

    func testATrackWithABlankTitleStillSaysNothingIsPlaying() {
        // A present track with an empty title, which is what a partially
        // imported file looks like. Asserting only the no-track case cannot see
        // the difference — the first version of this did, and a defect that
        // passed the blank straight through survived it.
        let untitled = NowPlaying(id: "3", title: "")
        let model = MiniPlayerModel(player: FakeMusicChromePlayer(nowPlaying: untitled), strings: strings)

        XCTAssertEqual(model.title, strings.nothingPlaying)
    }

    func testATrackWithABlankArtistDrawsNoSecondLine() {
        // Blank rather than absent is what a server sends for a track nobody
        // tagged.
        let untagged = NowPlaying(id: "2", title: "Untitled", artist: "")
        let model = MiniPlayerModel(player: FakeMusicChromePlayer(nowPlaying: untagged), strings: strings)

        XCTAssertNil(model.artist)
    }

    func testPressingTheTransportReachesThePlayer() {
        let player = FakeMusicChromePlayer(queue: [song, NowPlaying(id: "2", title: "Salt Flats")], queueIndex: 0)

        player.togglePlayPause()
        player.next()

        XCTAssertEqual(player.calls, ["togglePlayPause", "next"])
        XCTAssertEqual(player.nowPlaying?.id, "2")
    }

    func testASkipButtonBelongsOnlyWhereThereIsSomewhereToGo() {
        // A control a listener presses to find out it does nothing is worse than
        // one that is absent.
        let alone = FakeMusicChromePlayer(queue: [song], queueIndex: 0)
        let middle = FakeMusicChromePlayer(queue: [song, song, song], queueIndex: 1)

        XCTAssertFalse(alone.hasNext)
        XCTAssertFalse(alone.hasPrevious)
        XCTAssertTrue(middle.hasNext)
        XCTAssertTrue(middle.hasPrevious)
    }
}
