// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

import XCTest
@testable import NoMercyMusicPlayerUI

/// Dragging along the track.
///
/// The rule the Compose scrubber follows, asserted the same way: the figure
/// moves while somebody hunts and the track does not move until they let go.
/// Seeking on every pixel is a seek storm the engine answers by stalling.
@MainActor
final class MusicScrubberTests: XCTestCase {

    private func player(duration: Double = 200, at seconds: Double = 50) -> FakeMusicChromePlayer {
        let subject = FakeMusicChromePlayer(
            nowPlaying: NowPlaying(id: "a", title: "First"),
            duration: duration
        )
        subject.seek(to: seconds)
        return subject
    }

    func testTheFigureFollowsTheDragAndTheTrackDoesNot() {
        let subject = player()
        let model = MusicScrubberModel(player: subject)

        model.dragMoved(toX: 25, width: 100)

        XCTAssertEqual(model.shownSeconds, 50)
        XCTAssertEqual(subject.recordedSeeks.count, 1, "the seek that set up the fixture, and no other")
    }

    func testTheTrackMovesWhenTheDragEnds() {
        let subject = player()
        let model = MusicScrubberModel(player: subject)

        model.dragMoved(toX: 75, width: 100)
        model.dragEnded()

        XCTAssertEqual(subject.recordedSeeks.last, 150)
    }

    func testACancelledDragLeavesThePlayheadWhereItWas() {
        let subject = player()
        let model = MusicScrubberModel(player: subject)

        model.dragMoved(toX: 75, width: 100)
        model.dragCancelled()

        XCTAssertEqual(subject.recordedSeeks.last, 50, "the fixture's position, not the one hunted to")
        XCTAssertEqual(model.shownSeconds, 50, "and the figure goes back")
    }

    func testADragPastTheEndLandsOnTheEnd() {
        // A drag that leaves the strip reports a position outside it, and an
        // unclamped answer is a seek past the end.
        let subject = player()
        let model = MusicScrubberModel(player: subject)

        model.dragMoved(toX: 180, width: 100)
        model.dragEnded()

        XCTAssertEqual(subject.recordedSeeks.last, 200)
    }

    func testADragBeforeTheStartLandsOnTheStart() {
        let subject = player()
        let model = MusicScrubberModel(player: subject)

        model.dragMoved(toX: -40, width: 100)
        model.dragEnded()

        XCTAssertEqual(subject.recordedSeeks.last, 0)
    }

    func testATrackWhoseLengthHasNotArrivedDoesNotMove() {
        let subject = FakeMusicChromePlayer(nowPlaying: NowPlaying(id: "a", title: "First"), duration: 0)
        let model = MusicScrubberModel(player: subject)

        model.dragMoved(toX: 75, width: 100)
        model.dragEnded()

        XCTAssertTrue(subject.recordedSeeks.isEmpty, "not a seek to zero — no seek at all")
        XCTAssertEqual(model.fraction, 0, "and nothing is drawn filled")
    }

    func testTheFilledFractionIsHowFarAlongTheDragIs() {
        let subject = player()
        let model = MusicScrubberModel(player: subject)

        XCTAssertEqual(model.fraction, 0.25, accuracy: 0.001)

        model.dragMoved(toX: 90, width: 100)

        XCTAssertEqual(model.fraction, 0.9, accuracy: 0.001)
    }

    func testAClockShowsHoursOnlyWhenThereAreAny() {
        // A three-minute song written 0:03:12 makes a listener parse a field
        // that is always zero, and an eleven-hour mix written 47:12 is a lie
        // about which one they are looking at.
        XCTAssertEqual(formatTime(0), "0:00")
        XCTAssertEqual(formatTime(9), "0:09")
        XCTAssertEqual(formatTime(192), "3:12")
        XCTAssertEqual(formatTime(3600), "1:00:00")
        XCTAssertEqual(formatTime(3725), "1:02:05")
    }

    func testAClockRefusesNonsenseRatherThanDrawingIt() {
        XCTAssertEqual(formatTime(-5), "0:00")
        XCTAssertEqual(formatTime(.nan), "0:00")
    }
}
