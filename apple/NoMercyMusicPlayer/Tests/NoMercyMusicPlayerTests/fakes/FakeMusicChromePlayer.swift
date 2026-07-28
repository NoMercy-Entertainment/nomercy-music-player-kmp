// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

import Foundation
@testable import NoMercyMusicPlayerUI

/// A player that really changes when it is told to.
///
/// Not a mock. Every view test in this package mounts this, and a stand-in that
/// only recorded calls would let a view bind to a property nothing writes and
/// still pass.
@MainActor
final class FakeMusicChromePlayer: MusicChromePlayer {

    @Published private(set) var isPlaying: Bool = false
    @Published private(set) var nowPlaying: NowPlaying?
    @Published private(set) var positionSeconds: Double = 0
    @Published private(set) var durationSeconds: Double = 0
    @Published private(set) var queue: [NowPlaying] = []
    @Published private(set) var queueIndex: Int = 0
    @Published private(set) var isShuffled: Bool = false
    @Published private(set) var repeatMode: RepeatMode = .off
    @Published private(set) var volume: Int = 100
    @Published private(set) var isMuted: Bool = false

    private(set) var recordedSeeks: [Double] = []
    private(set) var recordedQueueIndex: Int?
    private(set) var calls: [String] = []

    init(
        isPlaying: Bool = false,
        nowPlaying: NowPlaying? = nil,
        duration: Double = 0,
        queue: [NowPlaying] = [],
        queueIndex: Int = 0
    ) {
        self.isPlaying = isPlaying
        self.nowPlaying = nowPlaying
        self.durationSeconds = duration
        self.queue = queue
        self.queueIndex = queueIndex
    }

    func togglePlayPause() {
        calls.append("togglePlayPause")
        isPlaying.toggle()
    }

    func next() {
        calls.append("next")
        guard hasNext else { return }
        queueIndex += 1
        nowPlaying = queue[queueIndex]
    }

    func previous() {
        calls.append("previous")
        guard hasPrevious else { return }
        queueIndex -= 1
        nowPlaying = queue[queueIndex]
    }

    func seek(to seconds: Double) {
        recordedSeeks.append(seconds)
        positionSeconds = seconds
    }

    func playQueueIndex(_ index: Int) {
        recordedQueueIndex = index
        guard queue.indices.contains(index) else { return }
        queueIndex = index
        nowPlaying = queue[index]
    }

    func setShuffled(_ shuffled: Bool) {
        isShuffled = shuffled
    }

    func setRepeat(_ mode: RepeatMode) {
        repeatMode = mode
    }

    func setVolume(_ percent: Int) {
        volume = percent
    }

    func setMuted(_ muted: Bool) {
        isMuted = muted
    }
}
