// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.music.conformance

import tv.nomercy.player.conformance.ContractFixture
import tv.nomercy.player.music.MusicEvents
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

// The music event map, both directions.
//
// The half a consumer meets first: a subscription is a string, the compiler has no
// opinion about it, and an event this port never declares is a handler that
// silently never runs. The Swift side gained the same check before the JVM had
// one at all.
class EventSurfaceConformanceTest {

    private val documented: Set<String> = ContractFixture.eventNames("music")

    private val declared: Set<String> = MusicEvents.all.map { it.name }.toSet()

    // Events the contract carries in the base map that this library does not raise, each
    // with the reason it is somebody else's. Empty, and that is the finding: the
    // base map is fully declared here.

    private val raisedElsewhere: Map<String, String> = emptyMap()

    @Test
    fun thisLibraryDeclaresNoEventTheEcosystemHasNeverHeardOf() {
        // The direction that matters more. A consumer subscribing to an event no
        // other client emits has written code that will never run, and nothing
        // in any compiler will mention it.
        assertEquals(
            emptySet(),
            declared - documented,
            "this library declares events the contract does not carry",
        )
    }

    @Test
    fun everyMappedEventThisLibraryDoesNotRaiseHasAStatedReason() {
        assertEquals(
            raisedElsewhere.keys,
            documented - declared,
            "a mapped event is undeclared with no reason recorded, or a reason outlived its event",
        )
    }

    @Test
    fun theReasonListDoesNotOutliveTheEventsItExcuses() {
        // A reason for an event that no longer exists is a note nobody will ever
        // delete, and it makes the exemption list look smaller than it is.
        val unknown = raisedElsewhere.keys - documented

        assertEquals(emptySet(), unknown, "reasons recorded for events the contract no longer has")
    }

    @Test
    fun theMapIsPopulatedRatherThanEmpty()  {
        // A comparison against an empty set passes forever, which is how a
        // filter that matched nothing would look exactly like a port that is
        // complete.
        assertTrue(documented.isNotEmpty(), "no music events read from the contract")
    }
}
