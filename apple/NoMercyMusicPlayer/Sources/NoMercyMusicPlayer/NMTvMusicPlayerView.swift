// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

import SwiftUI

/// Music, on a television.
///
/// Greenfield: there is no music television player anywhere in this ecosystem to
/// port from. What it is not is the phone screen scaled up — a television is
/// looked at from three metres and driven by focus, so the artwork is large, the
/// text is short, and the transport is a row of real focus targets rather than
/// glyphs sized for a fingertip.
///
/// The queue is deliberately absent. On a phone it sits under the player because
/// what is coming is half of why somebody opened it; on a television a list that
/// long is a focus trap between the artwork and the transport, and the remote's
/// sideways press already walks the queue.
#if os(tvOS)
@available(tvOS 15.0, *)
public struct NMTvMusicPlayerView<Player: MusicChromePlayer>: View {

    @ObservedObject private var player: Player

    private let strings: MusicStrings
    private let artwork: (NowPlaying?) -> AnyView

    @FocusState private var transportFocused: Bool

    public init(
        player: Player,
        strings: MusicStrings = MusicStrings(),
        @ViewBuilder artwork: @escaping (NowPlaying?) -> AnyView = { _ in AnyView(Color.gray.opacity(0.3)) }
    ) {
        self.player = player
        self.strings = strings
        self.artwork = artwork
    }

    public var input: TvMusicInput<Player> {
        TvMusicInput(player: player)
    }

    private var model: FullPlayerModel<Player> {
        FullPlayerModel(player: player, strings: strings)
    }

    public var body: some View {
        HStack(spacing: 64) {
            artwork(player.nowPlaying)
                .frame(width: 480, height: 480)
                .cornerRadius(12)

            VStack(alignment: .leading, spacing: 24) {
                Text(model.title)
                    .font(.system(size: 56, weight: .bold))
                    .lineLimit(2)

                if let artist = model.artist {
                    Text(artist).font(.title3).lineLimit(1)
                }

                progress

                transport
                    .focusSection()
            }

            Spacer()
        }
        .padding(80)
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(Color.black)
        .foregroundColor(.white)
        // The whole surface takes the remote, because a television has no
        // pointer and a player that only listened while a button was focused
        // would ignore the remote until somebody guessed where to press.
        .onPlayPauseCommand { input.playPause() }
        .onMoveCommand { input.move($0) }
        .onAppear { transportFocused = true }
    }

    /// Drawn rather than draggable. A remote has no position on a bar to drop,
    /// and a focusable scrubber would be one more stop between the artwork and
    /// the button somebody is reaching for.
    private var progress: some View {
        VStack(alignment: .leading, spacing: 8) {
            GeometryReader { geometry in
                ZStack(alignment: .leading) {
                    Color.white.opacity(0.25)
                    Color.white.frame(width: geometry.size.width * player.progress)
                }
            }
            .frame(height: 6)

            HStack {
                Text(model.elapsed)
                Spacer()
                Text(model.duration)
            }
            .font(.callout)
        }
        .frame(width: 640)
        .accessibilityLabel("\(model.elapsed) of \(model.duration)")
    }

    private var transport: some View {
        HStack(spacing: 40) {
            control(MusicGlyph(symbol: "backward.end.fill", label: strings.previous), action: player.previous)

            control(model.transport, action: player.togglePlayPause)
                .focused($transportFocused)
                .accessibilityIdentifier("nmTvPlayPause")

            control(MusicGlyph(symbol: "forward.end.fill", label: strings.next), action: player.next)

            control(model.shuffleControl, action: { player.setShuffled(!player.isShuffled) })

            control(model.repeatControl, action: { player.setRepeat(player.repeatMode.next) })
        }
    }

    private func control(_ glyph: MusicGlyph, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Image(systemName: glyph.symbol)
                .font(.system(size: 40))
                .foregroundColor(glyph.isOn ? .accentColor : .white)
        }
        .accessibilityLabel(glyph.label)
    }
}
#endif
