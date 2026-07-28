// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

import XCTest
@testable import NoMercyMusicPlayerUI

/// The television music player, driven the way a remote drives it.
///
/// Music has no shared state machine and does not need one: a television remote
/// controls transport directly, with no scrub mode to enter and no dialogs to
/// own the keys. What is worth asserting is the mapping — which gesture means
/// which command — because getting it wrong is silent. Both directions do
/// something, and only one of them is what was pressed.
@MainActor
final class TvMusicPlayerTests: XCTestCase {

    private let tracks = [
        NowPlaying(id: "a", title: "First", artist: "Someone"),
        NowPlaying(id: "b", title: "Second", artist: "Someone"),
        NowPlaying(id: "c", title: "Third", artist: "Someone"),
    ]

    private func player(at index: Int = 1) -> FakeMusicChromePlayer {
        FakeMusicChromePlayer(nowPlaying: tracks[index], duration: 240, queue: tracks, queueIndex: index)
    }

    func testTheRemotesPlayPauseReachesTheFacade() {
        let subject = player()
        let input = TvMusicInput(player: subject)

        input.playPause()

        XCTAssertTrue(subject.isPlaying)
    }

    func testSidewaysMovesThroughTheQueue() {
        // Right is next and left is previous, which is the direction the queue
        // is drawn in. Swapped, both still do something and a listener reaching
        // forward goes back.
        let subject = player()
        let input = TvMusicInput(player: subject)

        input.move(TvMusicInput<FakeMusicChromePlayer>.Direction.right)
        XCTAssertEqual(subject.nowPlaying?.id, "c")

        input.move(TvMusicInput<FakeMusicChromePlayer>.Direction.left)
        XCTAssertEqual(subject.nowPlaying?.id, "b")
    }

    func testUpAndDownDoNotMoveThroughTheQueue() {
        // A vertical press on a television moves focus. Mapped to transport it
        // would skip a track every time somebody reached for a button.
        let subject = player()
        let input = TvMusicInput(player: subject)

        input.move(TvMusicInput<FakeMusicChromePlayer>.Direction.up)
        input.move(TvMusicInput<FakeMusicChromePlayer>.Direction.down)

        XCTAssertEqual(subject.nowPlaying?.id, "b")
    }

    func testEveryDirectionIsHandledRatherThanFallingThrough() {
        // Four directions, and the mapping says what each one is for — including
        // the two that deliberately do nothing to the queue. A default case
        // hides which those are.
        let subject = player()
        let input = TvMusicInput(player: subject)

        let handled = TvMusicInput.Direction.allCases.map { input.intent(for: $0) }

        XCTAssertEqual(handled.count, 4)
        XCTAssertEqual(handled.filter { $0 == .none }.count, 2, "up and down, and only those")
    }

    func testTheScreenMountsWithNothingButAPlayer() {
        let subject = player()

        XCTAssertNotNil(TvMusicInput(player: subject))
    }
}
