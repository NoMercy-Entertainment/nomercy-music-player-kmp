// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

import SwiftUI

/// A television remote, mapped to the music facade.
///
/// Music needs no shared state machine and the video chrome does: there is no
/// scrub mode to enter, no dialog that owns the keys, and no autohide over a
/// picture. Transport is direct, so the mapping is the whole of it — and the
/// mapping is worth its own type because getting a direction wrong is silent.
/// Both directions do something; only one is what was pressed.
@MainActor
public struct TvMusicInput<Player: MusicChromePlayer> {

    /// What the remote can send. Its own enum rather than SwiftUI's, so the
    /// mapping is assertable on a platform that has no `MoveCommandDirection`.
    public enum Direction: CaseIterable, Sendable {
        case left
        case right
        case up
        case down
    }

    /// What a direction is for. `none` is a real answer rather than a fallback:
    /// vertical presses move focus, and a default case would hide which
    /// directions deliberately leave the queue alone.
    public enum Intent: Equatable, Sendable {
        case previous
        case next
        case none
    }

    private let player: Player

    public init(player: Player) {
        self.player = player
    }

    public func intent(for direction: Direction) -> Intent {
        switch direction {
        case .left: return .previous
        case .right: return .next
        // Focus, not transport. Mapped to the queue, reaching for a button below
        // the artwork would skip a track.
        case .up, .down: return .none
        }
    }

    public func move(_ direction: Direction) {
        switch intent(for: direction) {
        case .previous: player.previous()
        case .next: player.next()
        case .none: break
        }
    }

    public func playPause() {
        player.togglePlayPause()
    }

    #if os(tvOS)
    public func move(_ direction: MoveCommandDirection) {
        switch direction {
        // Spelled out, because the two enums share every case name and the
        // compiler cannot tell which `.left` a bare one means.
        case .left: move(Direction.left)
        case .right: move(Direction.right)
        case .up: move(Direction.up)
        case .down: move(Direction.down)
        @unknown default: break
        }
    }
    #endif
}
