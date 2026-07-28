// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

#if os(iOS)
import XCTest
@testable import NoMercyMusicPlayerUI

/// The screen a listener opens to choose something.
///
/// Every press here goes to the same facade the row uses, which is the only
/// reason the two views cannot disagree about what is playing. What is asserted
/// is the wiring and the labels — the two things that break silently.
@MainActor
final class FullPlayerTests: XCTestCase {

    private let tracks = [
        NowPlaying(id: "a", title: "First", artist: "Someone"),
        NowPlaying(id: "b", title: "Second", artist: "Someone"),
        NowPlaying(id: "c", title: "Third", artist: "Someone"),
    ]

    private func player(at index: Int = 1) -> FakeMusicChromePlayer {
        FakeMusicChromePlayer(nowPlaying: tracks[index], duration: 240, queue: tracks, queueIndex: index)
    }

    func testTheTransportDrivesTheFacade() {
        let subject = player()
        let intents = MusicIntents(player: subject)

        intents.togglePlay()
        XCTAssertTrue(subject.isPlaying)

        intents.next()
        XCTAssertEqual(subject.nowPlaying?.id, "c")

        intents.previous()
        XCTAssertEqual(subject.nowPlaying?.id, "b")
    }

    func testTheScrubberSeeksWhereItWasDropped() {
        let subject = player()
        let intents = MusicIntents(player: subject)

        intents.seekCommit(to: 120)

        XCTAssertEqual(subject.recordedSeeks.last, 120)
    }

    func testCyclingRepeatWalksOffThenAllThenOneThenOff() {
        // Three states rather than a boolean, and the order every client walks.
        // A cycle that skipped one would leave a button whose second press
        // surprises somebody.
        let subject = player()
        let intents = MusicIntents(player: subject)

        intents.cycleRepeat()
        XCTAssertEqual(subject.repeatMode, .all)

        intents.cycleRepeat()
        XCTAssertEqual(subject.repeatMode, .one)

        intents.cycleRepeat()
        XCTAssertEqual(subject.repeatMode, .off)
    }

    func testShuffleIsSetFromWhatIsShownRatherThanToggledBlind() {
        let subject = player()
        subject.setShuffled(true)
        let intents = MusicIntents(player: subject)

        intents.toggleShuffle()

        XCTAssertFalse(subject.isShuffled)
    }

    func testTheRepeatControlSaysWhatThePressWillDoAndNotWhatIsSet() {
        // A control announcing its own state gives a screen reader "repeat off"
        // on the button that turns repeating on.
        let subject = player()
        let strings = MusicStrings()

        XCTAssertEqual(FullPlayerModel(player: subject, strings: strings).repeatControl.label, strings.repeatAll)

        subject.setRepeat(.all)
        XCTAssertEqual(FullPlayerModel(player: subject, strings: strings).repeatControl.label, strings.repeatOne)

        subject.setRepeat(.one)
        XCTAssertEqual(FullPlayerModel(player: subject, strings: strings).repeatControl.label, strings.repeatOff)
    }

    func testTheRepeatControlLooksDifferentInEachOfItsThreeStates() {
        // A tri-state control drawn with one glyph is a control whose state a
        // listener cannot see. The label is for a screen reader; this is for
        // everybody else.
        let subject = player()
        let drawn: [MusicGlyph] = RepeatMode.allCases.map { mode in
            subject.setRepeat(mode)
            return FullPlayerModel(player: subject, strings: MusicStrings()).repeatControl
        }

        XCTAssertEqual(Set(drawn.map { "\($0.symbol)|\($0.isOn)" }).count, RepeatMode.allCases.count)
    }

    func testTheShuffleControlSaysWhatThePressWillDo() {
        let subject = player()
        let strings = MusicStrings()

        XCTAssertEqual(FullPlayerModel(player: subject, strings: strings).shuffleControl.label, strings.shuffleOn)

        subject.setShuffled(true)
        let shuffled = FullPlayerModel(player: subject, strings: strings).shuffleControl
        XCTAssertEqual(shuffled.label, strings.shuffleOff)
        XCTAssertTrue(shuffled.isOn)
    }

    func testTheTransportGlyphAndItsLabelAreOneDecision() {
        // Written apart, an edit to one is an edit to half of it — a pause glyph
        // announcing itself as Play, which two chromes in this ecosystem shipped.
        let subject = player()
        let strings = MusicStrings()

        let paused = FullPlayerModel(player: subject, strings: strings).transport
        XCTAssertEqual(paused.symbol, "play.fill")
        XCTAssertEqual(paused.label, strings.play)

        subject.togglePlayPause()
        let playing = FullPlayerModel(player: subject, strings: strings).transport
        XCTAssertEqual(playing.symbol, "pause.fill")
        XCTAssertEqual(playing.label, strings.pause)
    }

    func testABlankTitleStillSaysNothingIsPlaying() {
        // A gap is read as something that failed to load. A server that sent an
        // empty string is the case, not a nil track.
        let subject = FakeMusicChromePlayer(nowPlaying: NowPlaying(id: "x", title: "", artist: "Someone"))

        let model = FullPlayerModel(player: subject, strings: MusicStrings())

        XCTAssertEqual(model.title, MusicStrings().nothingPlaying)
    }

    func testTheTimesReadElapsedAndWholeLength() {
        // Elapsed and length, not remaining. Somebody looking at a song is
        // asking how long it is; the video chrome answers the other question.
        let subject = FakeMusicChromePlayer(nowPlaying: tracks[0], duration: 245)
        subject.seek(to: 65)

        let model = FullPlayerModel(player: subject, strings: MusicStrings())

        XCTAssertEqual(model.elapsed, "1:05")
        XCTAssertEqual(model.duration, "4:05")
    }

    func testTheScreenMountsWithNothingButAPlayer() {
        let subject = player()

        let screen = NMMusicPlayerView(player: subject)

        XCTAssertNotNil(screen.body)
    }
}
#endif
