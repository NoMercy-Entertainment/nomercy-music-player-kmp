// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

/// What the music chrome does, apart from how it is drawn.
///
/// The counterpart of the Compose `MusicCommands`. A press asserted through this
/// is a press asserted without a render pass, which is the difference between a
/// suite that catches "next skips two" and a screenshot that cannot.
@MainActor
public struct MusicIntents<Player: MusicChromePlayer> {

    private let player: Player

    /// The player, held rather than passed per call. Handed in at each press,
    /// nothing stops a test driving one player while the view binds another —
    /// which is a green suite over a screen where no button works.
    public init(player: Player) {
        self.player = player
    }

    public func togglePlay() {
        player.togglePlayPause()
    }

    public func next() {
        player.next()
    }

    public func previous() {
        player.previous()
    }

    /// Absolute, in seconds, and only when a drag ends. A progress line knows
    /// where it was dropped, and a delta computed from a position that has moved
    /// since would land elsewhere.
    public func seekCommit(to seconds: Double) {
        player.seek(to: seconds)
    }

    /// Off, then the whole queue, then this one track — the order every client
    /// walks, read off what is set rather than off a count kept here. A chrome
    /// counting its own presses is a chrome that disagrees with the phone in the
    /// listener's other hand.
    public func cycleRepeat() {
        player.setRepeat(player.repeatMode.next)
    }

    public func toggleShuffle() {
        player.setShuffled(!player.isShuffled)
    }

    /// By position, because that is what a queue list is: the row a listener
    /// tapped is the nth, and the same recording can legitimately be in a queue
    /// twice.
    public func playQueueIndex(_ index: Int) {
        player.playQueueIndex(index)
    }
}
