// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.conformance

import java.io.File
import java.security.MessageDigest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

// The vendored fixtures are the ones that were synced.
//
// Every repository carries its own copy of the contract and the scenarios,
// because CI checks out one repository at a time and a test reaching across the
// monorepo would pass here and fail there. The cost of that is four copies of
// two files, and the failure it invites is somebody editing one — a scenario
// nudged until a port passes it is a port measuring itself with a ruler it drew.
//
// The lock is written by the sync script. Editing a vendored file fails this;
// re-syncing updates the lock with it, which is correct, because then the source
// moved and every repository moves together.
class VendoredFixturesTest {

    private fun sha256(path: String): String {
        val file = File(path)
        assertTrue(file.exists(), "no vendored file at ${file.absolutePath} — run tools/sync-conformance.sh")

        return MessageDigest.getInstance("SHA-256")
            .digest(file.readBytes())
            .joinToString("") { "%02x".format(it) }
    }

    private fun locked(path: String): String {
        val file = File(path)
        assertTrue(file.exists(), "no ${file.name} — run tools/sync-conformance.sh")
        return file.readText().trim()
    }

    @Test
    fun theVendoredContractIsTheOneThatWasSynced() {
        assertEquals(
            locked("contract.lock"),
            sha256("contract/contract.json"),
            "the vendored contract does not match contract.lock — it was edited, or the sync did not run",
        )
    }

    @Test
    fun theVendoredScenariosAreTheOnesThatWereSynced() {
        assertEquals(
            locked("scenarios.lock"),
            sha256("scenarios/scenarios.json"),
            "the vendored scenarios do not match scenarios.lock — they were edited, or the sync did not run",
        )
    }

    @Test
    fun theScenariosDeclareTheContractTheyWereWrittenAgainst() {
        // Two files that drifted apart independently would each pass their own
        // hash check while describing different versions of the ecosystem.
        assertEquals(
            ContractFixture.version(),
            loadScenarios().contractVersion,
            "the vendored scenarios were written against a different contract version",
        )
    }
}
