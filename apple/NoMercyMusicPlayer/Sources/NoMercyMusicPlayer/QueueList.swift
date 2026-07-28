// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

import SwiftUI

/// One line of what is coming.
///
/// Identified by position and not by track. A queue is the one place in these
/// libraries where position is right: the same recording can legitimately be in
/// it twice, and rows keyed by the track collapse two copies into one — a list
/// that draws two entries where three were queued.
public struct QueueRow: Identifiable, Equatable, Sendable {
    public let id: Int
    public let title: String
    public let artist: String?
    public let isCurrent: Bool
}

/// What is coming, as rows.
///
/// Derived rather than drawn inline so the position rule can be asserted without
/// a render pass, which is what the Compose side asserts by test tag.
@MainActor
public struct QueueListModel<Player: MusicChromePlayer> {

    private let player: Player

    public init(player: Player) {
        self.player = player
    }

    public var rows: [QueueRow] {
        player.queue.enumerated().map { index, track in
            QueueRow(
                id: index,
                title: track.title,
                artist: track.artist.flatMap { $0.isEmpty ? nil : $0 },
                isCurrent: index == player.queueIndex
            )
        }
    }
}

/// What is coming, under the player rather than behind a sheet.
///
/// A queue one more tap away is one a listener stops checking, and what is
/// coming is the other half of what they opened the full player for. The Compose
/// chrome puts it in the same place for the same reason.
#if os(iOS)
@available(iOS 15.0, *)
public struct MusicQueueList<Player: MusicChromePlayer>: View {

    @ObservedObject private var player: Player
    private let strings: MusicStrings
    private let onSelect: (Int) -> Void

    public init(player: Player, strings: MusicStrings = MusicStrings(), onSelect: @escaping (Int) -> Void) {
        self.player = player
        self.strings = strings
        self.onSelect = onSelect
    }

    public var body: some View {
        ScrollView {
            LazyVStack(alignment: .leading, spacing: 0) {
                ForEach(QueueListModel(player: player).rows) { row in
                    Button {
                        onSelect(row.id)
                    } label: {
                        VStack(alignment: .leading, spacing: 2) {
                            Text(row.title)
                                // Weight rather than colour, so the row somebody
                                // is on is readable to anyone who cannot tell
                                // the two colours apart.
                                .fontWeight(row.isCurrent ? .bold : .regular)
                                .lineLimit(1)

                            if let artist = row.artist {
                                Text(artist).font(.caption).lineLimit(1)
                            }
                        }
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .padding(12)
                        .contentShape(Rectangle())
                    }
                    .buttonStyle(.plain)
                }
            }
        }
        .foregroundColor(.white)
        .accessibilityLabel(strings.queue)
    }
}
#endif
