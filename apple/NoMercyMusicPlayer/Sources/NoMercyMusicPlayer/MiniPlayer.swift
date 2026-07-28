// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

import SwiftUI

/// A glyph and what it announces itself as, which are one choice.
///
/// Written apart, an edit to one is an edit to half of it — a pause glyph
/// announcing itself as Play. Both other chromes in this ecosystem shipped that
/// defect, and only a planted test found it.
public struct MusicGlyph: Equatable, Sendable {
    public let symbol: String
    public let label: String
}

/// The strings the row shows.
public struct MusicStrings: Sendable {
    public var play: String = "Play"
    public var pause: String = "Pause"
    public var next: String = "Next"
    public var previous: String = "Previous"
    public var nothingPlaying: String = "Nothing playing"
    public var expand: String = "Open player"

    public init() {}
}

/// What the row draws, derived in one place.
@MainActor
public struct MiniPlayerModel<Player: MusicChromePlayer> {

    private let player: Player
    private let strings: MusicStrings

    public init(player: Player, strings: MusicStrings = MusicStrings()) {
        self.player = player
        self.strings = strings
    }

    public var transport: MusicGlyph {
        player.isPlaying
            ? MusicGlyph(symbol: "pause.fill", label: strings.pause)
            : MusicGlyph(symbol: "play.fill", label: strings.play)
    }

    /// The honest answer when there is nothing, rather than an empty line — a
    /// gap a listener reads as something that failed to load.
    public var title: String {
        guard let name = player.nowPlaying?.title, !name.isEmpty else { return strings.nothingPlaying }
        return name
    }

    public var artist: String? {
        player.nowPlaying?.artist.flatMap { $0.isEmpty ? nil : $0 }
    }
}

/// The row at the bottom of an application while something plays.
///
/// Artwork, two lines, the transport and a thin line of progress — the least
/// that answers "what is playing and can I stop it" at a glance, which is where
/// a listener spends the session.
///
/// The progress line is drawn rather than draggable. A two-pixel strip beside a
/// pause button is a target nobody meant to hit, and a row that scrubbed by
/// accident loses a listener's place every time they reach for pause.
@available(iOS 15.0, tvOS 15.0, *)
public struct NMMiniPlayerView<Player: MusicChromePlayer>: View {

    @ObservedObject private var player: Player

    private let strings: MusicStrings
    private let onExpand: (() -> Void)?
    private let artwork: (NowPlaying?) -> AnyView

    public init(
        player: Player,
        strings: MusicStrings = MusicStrings(),
        onExpand: (() -> Void)? = nil,
        @ViewBuilder artwork: @escaping (NowPlaying?) -> AnyView = { _ in AnyView(Color.gray.opacity(0.3)) }
    ) {
        self.player = player
        self.strings = strings
        self.onExpand = onExpand
        self.artwork = artwork
    }

    private var model: MiniPlayerModel<Player> {
        MiniPlayerModel(player: player, strings: strings)
    }

    public var body: some View {
        VStack(spacing: 0) {
            HStack(spacing: 12) {
                // The artwork and the two lines open the full player; the
                // buttons beside them do not. One tap target over the whole row
                // announces itself as a single control and takes the pause
                // button out of reach of a screen reader — which is exactly what
                // the Compose row did before a test caught it.
                Button {
                    onExpand?()
                } label: {
                    HStack(spacing: 12) {
                        artwork(player.nowPlaying)
                            .frame(width: 44, height: 44)
                            .cornerRadius(4)

                        VStack(alignment: .leading, spacing: 2) {
                            Text(model.title).font(.subheadline).lineLimit(1)
                            if let artist = model.artist {
                                Text(artist).font(.caption).lineLimit(1)
                            }
                        }

                        Spacer()
                    }
                }
                .buttonStyle(.plain)
                .accessibilityLabel(strings.expand)

                transport
            }
            .padding(.horizontal, 12)
            .padding(.vertical, 6)

            ProgressLine(progress: player.progress)
        }
        .background(Color.black)
    }

    private var transport: some View {
        HStack(spacing: 16) {
            if player.hasPrevious {
                Button(action: player.previous) {
                    Image(systemName: "backward.end.fill")
                }
                .accessibilityLabel(strings.previous)
            }

            Button(action: player.togglePlayPause) {
                Image(systemName: model.transport.symbol)
            }
            .accessibilityLabel(model.transport.label)
            .accessibilityIdentifier("nmMiniPlayPause")

            if player.hasNext {
                Button(action: player.next) {
                    Image(systemName: "forward.end.fill")
                }
                .accessibilityLabel(strings.next)
            }
        }
        .foregroundColor(.white)
    }
}

@available(iOS 15.0, tvOS 15.0, *)
private struct ProgressLine: View {
    let progress: Double

    var body: some View {
        GeometryReader { geometry in
            ZStack(alignment: .leading) {
                Color.white.opacity(0.25)
                Color.white.frame(width: geometry.size.width * progress)
            }
        }
        .frame(height: 2)
    }
}
