// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.music.connect

import kotlinx.coroutines.flow.Flow

// The realtime seam Connect runs over, supplied by the application.
//
// The library owns the protocol and the application owns the wire. That is the
// opposite of casting, where the transport is plain HTTP and the library ships
// it — here it is a SignalR hub the application already holds a connection to,
// and a second one opened by a library would be a second authority on a
// server-authoritative protocol.
//
// It is also the collapse. The shipped application has two transports for this,
// a thin shared one and a rich Android one, and they double-play against each
// other; one contract means one authority.
public interface MusicConnectChannel {

    // This device's stable identity, matching what a frame calls the active
    // device when it is this one.
    public val deviceId: String

    // Frames as the application decoded them. The plugin is the only subscriber.
    public val frames: Flow<MusicPlayerState>

    // A transport command, in the player's own unit: a seek carries seconds,
    // and everything else carries nothing. The application encodes both onto
    // whatever the hub expects.
    public suspend fun playbackCommand(command: String, dataSeconds: Double? = null)

    // Hand playback to another device.
    public suspend fun changeDevice(targetDeviceId: String)

    // Where this device has actually got to, in milliseconds. Only the active
    // device reports; a passive one reporting would be telling the server about
    // a position it inferred from the server.
    public suspend fun reportPosition(positionMs: Long)

    // The server's clock, for measuring the offset from this device's. Null on a
    // transport that cannot answer, which is a reason to fall back to local time
    // rather than to refuse to mirror.
    public suspend fun serverTimeMs(): Long?
}

// The commands the protocol carries. Named rather than spelled at each call
// site, because a typo in one of these is a command the server ignores in
// silence.
public object ConnectCommand {
    public const val PLAY: String = "play"
    public const val PAUSE: String = "pause"
    public const val STOP: String = "stop"
    public const val NEXT: String = "next"
    public const val PREVIOUS: String = "previous"
    public const val SEEK: String = "seek"
}
