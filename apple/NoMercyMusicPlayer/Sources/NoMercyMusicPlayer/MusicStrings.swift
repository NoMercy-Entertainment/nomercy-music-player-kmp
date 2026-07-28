// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

/// Everything the music chrome says, in one place a host can replace.
///
/// The same set the Compose chrome carries, spelled the same way. A library that
/// hardcoded English would be a library nobody outside one locale can ship, and
/// two chromes with different wording for the same button is the same product
/// disagreeing with itself across two of a listener's devices.
public struct MusicStrings: Sendable {
    public var play: String = "Play"
    public var pause: String = "Pause"
    public var next: String = "Next"
    public var previous: String = "Previous"
    public var nothingPlaying: String = "Nothing playing"
    public var collapse: String = "Close player"
    public var expand: String = "Open player"
    public var queue: String = "Up next"

    // What the button will DO, which is what a label is for. A control that
    // announced its own state would tell a screen reader "repeat off" on a
    // button that turns repeating on.
    public var shuffleOn: String = "Shuffle"
    public var shuffleOff: String = "Stop shuffling"
    public var repeatAll: String = "Repeat queue"
    public var repeatOne: String = "Repeat track"
    public var repeatOff: String = "Stop repeating"

    public init() {}
}
