// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

import SwiftUI

/// Where a drag has got to, and whether it has been let go of.
///
/// The track does not move until the drag ends. What moves while somebody is
/// hunting is the figure, because seeking on every pixel is a seek storm the
/// engine answers by stalling.
///
/// A class rather than view state so the rule can be driven from a test. The
/// interesting failures here — a seek per pixel, a cancel that seeks anyway, a
/// drag off the end that lands past the end — are all invisible in a screenshot.
@MainActor
public final class MusicScrubberModel<Player: MusicChromePlayer>: ObservableObject {

    @Published public private(set) var dragSeconds: Double?

    private let player: Player

    public init(player: Player) {
        self.player = player
    }

    /// Where along the track a horizontal position falls.
    ///
    /// Clamped at both ends, because a drag that leaves the strip reports a
    /// position outside it and an unclamped answer is a seek past the end or
    /// before the start.
    public func dragMoved(toX x: Double, width: Double) {
        guard player.durationSeconds > 0, width > 0 else { return }

        dragSeconds = (x / width) * player.durationSeconds
    }

    /// Only a completed drag moves the track.
    public func dragEnded() {
        if let seconds = dragSeconds {
            player.seek(to: seconds)
        }
        dragSeconds = nil
    }

    /// A cancel leaves it where it was, and the figure goes back — or the bar
    /// keeps showing a place nobody went.
    public func dragCancelled() {
        dragSeconds = nil
    }

    public var shownSeconds: Double {
        dragSeconds ?? player.positionSeconds
    }

    public var fraction: Double {
        guard player.durationSeconds > 0 else { return 0 }

        return min(1, max(0, shownSeconds / player.durationSeconds))
    }
}

/// Dragging along the track, in the full player where there is room for it.
///
/// The mini-player's line is drawn and this one is dragged, which is the whole
/// difference between them: a two-pixel strip beside a pause button is a target
/// nobody meant to hit, and a row that scrubbed by accident loses a listener's
/// place every time they reach for pause.
#if os(iOS)
@available(iOS 15.0, *)
public struct MusicScrubber<Player: MusicChromePlayer>: View {

    @ObservedObject private var player: Player
    @StateObject private var model: MusicScrubberModel<Player>

    @MainActor
    public init(player: Player) {
        self.player = player
        _model = StateObject(wrappedValue: MusicScrubberModel(player: player))
    }

    public var body: some View {
        VStack(spacing: 4) {
            GeometryReader { geometry in
                ZStack(alignment: .leading) {
                    Color.white.opacity(0.25)
                    Color.white.frame(width: geometry.size.width * model.fraction)
                }
                .frame(height: lineHeight)
                .frame(maxHeight: .infinity, alignment: .center)
                // The whole strip is the target rather than the few pixels the
                // bar is drawn in. The drawn height is a design decision; the
                // reachable height is not. Fingers are not pixels.
                .contentShape(Rectangle())
                .gesture(
                    DragGesture(minimumDistance: 0)
                        .onChanged { value in
                            model.dragMoved(toX: value.location.x, width: geometry.size.width)
                        }
                        .onEnded { _ in model.dragEnded() }
                )
            }
            .frame(height: touchHeight)
            .accessibilityLabel(formatTime(model.shownSeconds))

            // Elapsed on the left and the whole length on the right, which is
            // the pair every player draws. Remaining is the video chrome's
            // answer because somebody deciding whether to start another episode
            // is asking how much is left; a listener looking at a song is asking
            // how long it is.
            HStack {
                Text(formatTime(model.shownSeconds))
                Spacer()
                Text(formatTime(player.durationSeconds))
            }
            .font(.caption)
            .foregroundColor(.white)
        }
    }

    private let touchHeight: CGFloat = 32
    private let lineHeight: CGFloat = 3
}
#endif
