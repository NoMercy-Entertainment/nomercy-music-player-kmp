// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

import XCTest
@testable import NoMercyMusicPlayerUI

/// What is coming.
///
/// By position rather than by identifier, and this is the one place in these
/// libraries where position is right: a queue is an ordered list a listener
/// points at, and the same recording can legitimately be in it twice. Every
/// fixture here repeats a track, because a queue of distinct tracks cannot tell
/// selecting-by-position from selecting-by-identity apart.
@MainActor
final class QueueListTests: XCTestCase {

    private let encore = NowPlaying(id: "a", title: "First", artist: "Someone")

    private func repeatedQueue() -> FakeMusicChromePlayer {
        FakeMusicChromePlayer(
            nowPlaying: NowPlaying(id: "b", title: "Second"),
            queue: [encore, NowPlaying(id: "b", title: "Second"), encore],
            queueIndex: 1
        )
    }

    func testSelectingARowPlaysThatPositionAndNotTheFirstTrackLikeIt() {
        let subject = repeatedQueue()
        let intents = MusicIntents(player: subject)

        intents.playQueueIndex(2)

        XCTAssertEqual(subject.queueIndex, 2)
    }

    func testTheRowSomebodyIsOnIsThePositionAndNotEveryCopyOfTheTrack() {
        let subject = repeatedQueue()
        subject.playQueueIndex(2)

        let rows = QueueListModel(player: subject).rows

        XCTAssertEqual(rows.map(\.isCurrent), [false, false, true])
    }

    func testEveryRowCarriesItsOwnIdentityEvenWhenTheTrackRepeats() {
        // A row keyed by the track collapses two copies into one, and the list
        // then draws two entries where three were queued.
        let subject = repeatedQueue()

        let rows = QueueListModel(player: subject).rows

        XCTAssertEqual(Set(rows.map(\.id)).count, 3)
    }

    func testARowWithoutAnArtistShowsOnlyItsTitle() {
        let subject = FakeMusicChromePlayer(queue: [NowPlaying(id: "a", title: "First", artist: "")])

        let rows = QueueListModel(player: subject).rows

        XCTAssertEqual(rows.first?.title, "First")
        XCTAssertNil(rows.first?.artist)
    }

    func testAnEmptyQueueDrawsNothing() {
        let subject = FakeMusicChromePlayer()

        XCTAssertTrue(QueueListModel(player: subject).rows.isEmpty)
    }
}
