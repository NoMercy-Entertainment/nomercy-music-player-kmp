// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

import SwiftUI

/// What the full player draws, decided in one place.
///
/// The row has one of these and so does this screen, both reading the same
/// facade — which is the only reason the two views cannot disagree about what is
/// playing. Every glyph carries its own label so an edit to one is an edit to
/// both.
@MainActor
public struct FullPlayerModel<Player: MusicChromePlayer> {

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

    /// Lit when shuffling, labelled with what the press will do. A control that
    /// announced its own state would tell a screen reader "shuffle" on the
    /// button that stops it.
    public var shuffleControl: MusicGlyph {
        MusicGlyph(
            symbol: "shuffle",
            label: player.isShuffled ? strings.shuffleOff : strings.shuffleOn,
            isOn: player.isShuffled
        )
    }

    /// Three states a listener can see as well as hear. The label says what the
    /// press will do; the glyph and the lit state say what is set — and a
    /// tri-state control drawn one way in all three is one whose state nobody
    /// can read without pressing it.
    public var repeatControl: MusicGlyph {
        switch player.repeatMode {
        case .off:
            return MusicGlyph(symbol: "repeat", label: strings.repeatAll, isOn: false)
        case .all:
            return MusicGlyph(symbol: "repeat", label: strings.repeatOne, isOn: true)
        case .one:
            return MusicGlyph(symbol: "repeat.1", label: strings.repeatOff, isOn: true)
        }
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

    public var elapsed: String {
        formatTime(player.positionSeconds)
    }

    public var duration: String {
        formatTime(player.durationSeconds)
    }
}

/// The player a listener opens to choose something.
///
/// The same state and the same commands the row uses, laid out for a screen
/// rather than for a strip. Nothing here reaches the player directly, which is
/// what lets the two views disagree about layout and never about what is
/// playing.
///
/// iOS only, and that is the line the Compose chrome draws too. A television is
/// driven by focus rather than by a finger and its player is its own view.
#if os(iOS)
@available(iOS 15.0, *)
public struct NMMusicPlayerView<Player: MusicChromePlayer>: View {

    @ObservedObject private var player: Player

    private let strings: MusicStrings
    private let onCollapse: (() -> Void)?
    private let artwork: (NowPlaying?) -> AnyView

    public init(
        player: Player,
        strings: MusicStrings = MusicStrings(),
        onCollapse: (() -> Void)? = nil,
        @ViewBuilder artwork: @escaping (NowPlaying?) -> AnyView = { _ in AnyView(Color.gray.opacity(0.3)) }
    ) {
        self.player = player
        self.strings = strings
        self.onCollapse = onCollapse
        self.artwork = artwork
    }

    /// Every press, apart from how it is drawn. Public so a host embedding the
    /// screen in something larger can drive the same actions from its own
    /// controls rather than reaching past the chrome at the player.
    public var intents: MusicIntents<Player> {
        MusicIntents(player: player)
    }

    private var model: FullPlayerModel<Player> {
        FullPlayerModel(player: player, strings: strings)
    }

    public var body: some View {
        VStack(spacing: 12) {
            HStack {
                Button {
                    onCollapse?()
                } label: {
                    Image(systemName: "chevron.down")
                }
                .accessibilityLabel(strings.collapse)

                Spacer()
            }

            artwork(player.nowPlaying)
                .frame(maxWidth: .infinity, maxHeight: .infinity)
                .cornerRadius(8)

            heading

            MusicScrubber(player: player)

            transport

            // Underneath rather than behind a separate screen. What is coming is
            // the other half of what a listener opened this for, and a queue one
            // more tap away is one they stop checking.
            MusicQueueList(player: player, strings: strings, onSelect: intents.playQueueIndex)
                .frame(height: 180)
        }
        .padding(16)
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(Color.black)
    }

    private var heading: some View {
        VStack(alignment: .leading, spacing: 2) {
            Text(model.title)
                .font(.title2.bold())
                .lineLimit(2)

            if let artist = model.artist {
                Text(artist).font(.body).lineLimit(1)
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .foregroundColor(.white)
    }

    /// Shuffle and repeat live here and not on the row, because the row is for
    /// answering "what is playing" at a glance and these are decisions somebody
    /// makes deliberately. Both are drawn from the facade rather than remembered,
    /// so a change made on another device is what the button shows.
    private var transport: some View {
        HStack {
            Spacer()
            control(model.shuffleControl, action: intents.toggleShuffle)
            Spacer()
            control(MusicGlyph(symbol: "backward.end.fill", label: strings.previous), action: intents.previous)
            Spacer()
            control(model.transport, action: intents.togglePlay)
                .accessibilityIdentifier("nmFullPlayPause")
            Spacer()
            control(MusicGlyph(symbol: "forward.end.fill", label: strings.next), action: intents.next)
            Spacer()
            control(model.repeatControl, action: intents.cycleRepeat)
            Spacer()
        }
    }

    private func control(_ glyph: MusicGlyph, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Image(systemName: glyph.symbol)
                .foregroundColor(glyph.isOn ? .accentColor : .white)
        }
        .accessibilityLabel(glyph.label)
    }
}
#endif
