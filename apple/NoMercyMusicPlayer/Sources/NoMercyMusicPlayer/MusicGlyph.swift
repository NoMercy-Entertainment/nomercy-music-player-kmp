// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

/// A glyph, what it announces itself as, and whether it is lit.
///
/// Written apart, an edit to one is an edit to half of it — a pause glyph
/// announcing itself as Play. Both other chromes in this ecosystem shipped that
/// defect, and only a planted test found it.
///
/// `isOn` is here rather than at the call site for the same reason. Shuffle and
/// repeat are settings rather than actions: their label says what a press will
/// do, so the only thing left that can say what is currently set is how they are
/// drawn, and a control whose lit state is decided somewhere else is a control
/// that can be lit while announcing the opposite.
public struct MusicGlyph: Equatable, Sendable {
    public let symbol: String
    public let label: String
    public let isOn: Bool

    public init(symbol: String, label: String, isOn: Bool = false) {
        self.symbol = symbol
        self.label = label
        self.isOn = isOn
    }
}
