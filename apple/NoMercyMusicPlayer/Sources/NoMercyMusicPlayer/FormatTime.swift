// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

import Foundation

/// A position, as a clock reads it.
///
/// Hours only when there are any. A three-minute song written 0:03:12 makes a
/// listener parse a field that is always zero, and an eleven-hour mix written
/// 47:12 is a lie about which one they are looking at.
///
/// The same rule the Compose chrome follows, and the same one its tests assert:
/// a listener whose phone and whose television disagree about how long a track
/// is has one of them wrong and no way to tell which.
public func formatTime(_ seconds: Double) -> String {
    guard seconds.isFinite, seconds >= 0 else { return "0:00" }

    let total = Int(seconds)
    let hours = total / secondsPerHour
    let minutes = (total % secondsPerHour) / secondsPerMinute
    let remainder = total % secondsPerMinute

    if hours > 0 {
        return "\(hours):\(pad(minutes)):\(pad(remainder))"
    }
    return "\(minutes):\(pad(remainder))"
}

private func pad(_ value: Int) -> String {
    value < 10 ? "0\(value)" : "\(value)"
}

private let secondsPerHour = 3600
private let secondsPerMinute = 60
