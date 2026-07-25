# nomercy-music-player-kmp

The music half of the NoMercy player trio, for Kotlin Multiplatform.

Everything a player does — transport, queue, volume, time, state, plugins,
lifecycle — lives in [`nomercy-player-core-kmp`][core]. This library adds only
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

## Status

Early. The event registry and its payload types are here and tested; the
concrete `NMMusicPlayer`, the audio backends and the crossfade engine are not
yet.

[core]: https://github.com/NoMercy-Entertainment/nomercy-player-core-kmp
