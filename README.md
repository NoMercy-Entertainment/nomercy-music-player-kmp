# nomercy-music-player-kmp

The music half of the NoMercy player trio, for Kotlin Multiplatform.

Everything a player does (transport, queue, volume, time, state, plugins,
lifecycle) lives in [`nomercy-player-core-kmp`][core]. This library adds only
what makes a player a *music* player, which at the event level is five keys and
almost all of them are about crossfade.

That smallness is the point. A listener for `play` uses `CoreEvents.Play` on a
music player exactly as it does on a video player: one bus, one set of names,
and the split is about which library owns the declaration.

## Building it

```
./gradlew build
```

The build depends on core by its published coordinate and substitutes a sibling
`../nomercy-player-core-kmp` checkout when there is one, so a change to core is
picked up without publishing first.

## NoMercy Connect

Connect is music playing across several devices with one of them making sound.
A phone, a desktop and a television can all show the same session, and whichever
one the server names is the one you hear. The others follow: they show the track
and a progress bar that moves, and they never open the stream.

The server decides who is playing. No device decides for itself, because two
devices each deciding is how the same song ends up playing twice a fraction of a
second apart.

The library owns the protocol and the application owns the wire. You implement
`MusicConnectChannel` over whatever connection you already hold, hand it decoded
frames, and the library never sees a hub method name or a JSON key. That split
is what lets one plugin serve a phone, a desktop and a television whose
transports have nothing in common.

```kotlin
val connect = connectMusic(player, myChannel, screenScope)

// What a device that is not playing should draw.
connect.mirror.collect { render(it.item, it.positionMs, it.durationMs) }
```

Transport calls stay the same wherever they come from. `player.play()` on the
device that is playing tells the server and carries on, so there is no gap in
the audio for the person holding it. The same call on a device that is not
playing tells the server and stops there.

| | The device you hear | Every other device |
| --- | --- | --- |
| A press | reported, then applied here | reported, shown, applied when the server agrees |
| A frame | reconciled against the engine | drawn, never loaded |
| Volume | its own remembered level | its own remembered level |

Volume is the one thing that is never about who is playing. A phone at thirty
percent and a television at eighty are both correct, so each device applies its
own remembered level from every frame.

The reference behaviour is the web `musicConnectPlugin`, and this follows it
including the parts that look like corrections: the sequence gate that drops
redelivered frames, the shield that holds a press against frames the server sent
before it heard about it, and the clock measurement everything about "now" is
judged against. The SignalR channel and the retirement of the two older adapters
belong to the application and land with app adoption.

## Status

The player, the audio backends, the crossfade engine, the event registry and
Connect are here and tested. The application wiring that supplies a channel over
SignalR is not part of this library.

[core]: https://github.com/NoMercy-Entertainment/nomercy-player-core-kmp
