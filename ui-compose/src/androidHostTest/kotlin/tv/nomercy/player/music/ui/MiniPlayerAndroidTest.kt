// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.music.ui

import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// The mini-player on Android, under a real Compose Android hierarchy.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [SDK_UNDER_TEST])
class MiniPlayerAndroidTest : MiniPlayerGate()

private const val SDK_UNDER_TEST = 34
