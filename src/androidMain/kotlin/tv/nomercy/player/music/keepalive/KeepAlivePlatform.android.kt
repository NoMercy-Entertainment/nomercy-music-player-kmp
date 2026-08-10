// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.music.keepalive

import tv.nomercy.player.core.ports.PlatformEnvironment

// The Android TV panel this feature exists for. Falls back to the no-op the
// same way `defaultCastWaker`/`defaultSystemTransport` do: a host test with
// no installed context is not a reason for a player to refuse to start.
public actual fun defaultKeepAliveTone(): KeepAliveTone =
    if (PlatformEnvironment.isInstalled()) {
        AndroidKeepAliveTone(PlatformEnvironment.requireContext().androidContext)
    } else {
        NoKeepAliveTone
    }
