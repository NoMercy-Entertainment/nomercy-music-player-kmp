// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

import XCTest
@testable import NoMercyMusicPlayerUI

/// The seam both music views read.
@MainActor
final class MusicFacadeTests: XCTestCase {

    private let first = NowPlaying(id: "1", title: "Neon Sky", artist: "The Long Winter")
    private let second = NowPlaying(id: "2", title: "Salt Flats", artist: "The Long Winter")

    func testATrackWithNoLengthYetHasNoProgress() {
        // Every stream starts this way and a live one never leaves it.
        let player = FakeMusicChromePlayer(duration: 0)
        player.seek(to: 30)

        XCTAssertEqual(player.progress, 0)
    }

    func testAndOneWithALengthIsDrawnAgainstIt() {
        let player = FakeMusicChromePlayer(duration: 120)
        player.seek(to: 30)

        XCTAssertEqual(player.progress, 0.25)
    }

    func testAPositionPastTheEndStillFitsOnTheBar() {
        // Engines overshoot at the end of a track, and a fraction above one is a
        // line drawn past the edge of what it is inside.
        let player = FakeMusicChromePlayer(duration: 120)
        player.seek(to: 200)

        XCTAssertEqual(player.progress, 1)
    }

    func testTheEndsOfAQueueKnowTheyAreTheEnds() {
        let start = FakeMusicChromePlayer(queue: [first, second], queueIndex: 0)
        let end = FakeMusicChromePlayer(queue: [first, second], queueIndex: 1)

        XCTAssertTrue(start.hasNext)
        XCTAssertFalse(start.hasPrevious)
        XCTAssertFalse(end.hasNext)
        XCTAssertTrue(end.hasPrevious)
    }

    func testRepeatWalksOffThenTheQueueThenTheTrack() {
        // Three states rather than a boolean: "repeat" without saying what is
        // repeated is a button whose second press surprises somebody.
        XCTAssertEqual(RepeatMode.off.next, .all)
        XCTAssertEqual(RepeatMode.all.next, .one)
        XCTAssertEqual(RepeatMode.one.next, .off)
    }

    func testTheFakeReallyChangesWhenItIsTold() {
        // The collaborator every view test mounts. If it only recorded calls, a
        // view could bind to a property nothing writes and still pass.
        let player = FakeMusicChromePlayer(queue: [first, second], queueIndex: 0)

        player.togglePlayPause()
        player.next()

        XCTAssertTrue(player.isPlaying)
        XCTAssertEqual(player.nowPlaying?.id, "2")
    }

    func testSkippingPastTheEndOfTheQueueStaysPut() {
        let player = FakeMusicChromePlayer(queue: [first], queueIndex: 0)

        player.next()

        XCTAssertEqual(player.queueIndex, 0)
    }
}
