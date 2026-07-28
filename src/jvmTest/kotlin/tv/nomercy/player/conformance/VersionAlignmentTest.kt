// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.conformance

import java.io.File
import java.util.Properties
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

// The repository's declared contract version, against the fixture it vendors.
//
// Two numbers in two files, and nothing made them agree. A repository can be
// built against 2.0.1 while carrying a 2.0.0 fixture: every gate passes, because
// each one is measuring the copy sitting next to it, and the mismatch only shows
// up as a symbol nobody can find months later.
//
// This is the cheapest half of the drift alarm. It does not know whether the web
// has moved on — that is the sweep's job, and it needs every repository at once,
// which no single repository's CI has.
class VersionAlignmentTest {

    private fun declared(): String {
        val file = File("gradle.properties")
        assertTrue(file.exists(), "no gradle.properties at ${file.absolutePath}")

        val properties = Properties()
        file.inputStream().use(properties::load)

        val version: String? = properties.getProperty("CONTRACT_VERSION")
        assertTrue(!version.isNullOrBlank(), "this repository declares no CONTRACT_VERSION")
        return version!!
    }

    @Test
    fun theRepositoryAndItsFixtureAgreeOnAContractVersion() {
        assertEquals(
            declared(),
            ContractFixture.version(),
            "gradle.properties says one contract version and the vendored fixture says another",
        )
    }

    @Test
    fun theScenariosWereWrittenAgainstThatSameContract() {
        assertEquals(
            declared(),
            loadScenarios().contractVersion,
            "the scenarios describe a different contract version than this repository declares",
        )
    }
}
