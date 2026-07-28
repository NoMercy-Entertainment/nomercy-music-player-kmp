// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

import Combine
import Foundation

/// What is playing, as a row shows it.
///
/// The library's own value rather than the engine's item, which carries an id, a
/// title and a url and nothing else. An artist and an album come from whichever
/// server the host talks to, and a default that invented them would be guessing
/// at a wire format the library was never told about.
public struct NowPlaying: Identifiable, Equatable, Sendable {
    public let id: String
    public let title: String
    public let artist: String?
    public let album: String?
    public let artworkURL: URL?

    public init(id: String, title: String, artist: String? = nil, album: String? = nil, artworkURL: URL? = nil) {
        self.id = id
        self.title = title
        self.artist = artist
        self.album = album
        self.artworkURL = artworkURL
    }
}

/// Off, the whole queue, or this one track.
///
/// Three states rather than a boolean, because "repeat" without saying what is
/// being repeated is a button whose second press surprises somebody.
public enum RepeatMode: String, CaseIterable, Sendable {
    case off
    case all
    case one

    /// The order every client walks, so the same button does the same thing
    /// wherever a listener meets it.
    public var next: RepeatMode {
        switch self {
        case .off: return .all
        case .all: return .one
        case .one: return .off
        }
    }
}

/// The one surface the music chrome binds to.
///
/// The counterpart of the video facade and, deliberately, the counterpart of the
/// Compose MusicChromeState plus MusicCommands. Both views on this platform read
/// it, so the row and the full player cannot disagree about what is playing —
/// which is the failure two separately-bound views produce.
@MainActor
public protocol MusicChromePlayer: ObservableObject {
    var isPlaying: Bool { get }
    var nowPlaying: NowPlaying? { get }

    var positionSeconds: Double { get }
    var durationSeconds: Double { get }

    var queue: [NowPlaying] { get }
    var queueIndex: Int { get }

    var isShuffled: Bool { get }
    var repeatMode: RepeatMode { get }

    var volume: Int { get }
    var isMuted: Bool { get }

    func togglePlayPause()
    func next()
    func previous()
    func seek(to seconds: Double)
    func playQueueIndex(_ index: Int)
    func setShuffled(_ shuffled: Bool)
    func setRepeat(_ mode: RepeatMode)
    func setVolume(_ percent: Int)
    func setMuted(_ muted: Bool)
}

extension MusicChromePlayer {

    /// Drawn by the thin line under the row, computed once. Zero rather than a
    /// division by zero, which is every track whose length has not arrived.
    public var progress: Double {
        durationSeconds <= 0 ? 0 : min(1, max(0, positionSeconds / durationSeconds))
    }

    public var hasNext: Bool { queueIndex < queue.count - 1 }

    public var hasPrevious: Bool { queueIndex > 0 }
}
