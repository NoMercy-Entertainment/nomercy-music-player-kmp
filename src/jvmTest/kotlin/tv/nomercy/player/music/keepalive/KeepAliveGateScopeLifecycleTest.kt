// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.music.keepalive

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlin.reflect.full.declaredMemberProperties
import kotlin.reflect.jvm.isAccessible
import kotlin.test.Test
import kotlin.test.assertFalse

// dispose() used to cancel its three named jobs but never the scope that
// built them. Every one of those jobs is a child of that scope, so the leak
// was latent rather than visible through the jobs themselves — the next
// launch anyone added on [scope] would have kept running past dispose()
// undetected. Reflection is the only way to observe [scope] directly: it is
// private, and nothing public exposes its lifecycle.
class KeepAliveGateScopeLifecycleTest {

    private class FakeTone : KeepAliveTone {
        override var isRunning: Boolean = false
        override fun start() {
            isRunning = true
        }

        override fun stop() {
            isRunning = false
        }
    }

    @Test
    fun disposeCancelsTheScopeItOwns() {
        val gate = KeepAliveGate(FakeTone())

        gate.dispose()

        val scopeProperty = KeepAliveGate::class.declaredMemberProperties.first { it.name == "scope" }
        scopeProperty.isAccessible = true
        val scope = scopeProperty.call(gate) as CoroutineScope
        val job = requireNotNull(scope.coroutineContext[Job]) { "the gate's scope carries no Job" }

        assertFalse(job.isActive, "dispose() must cancel the scope it built, not only the jobs launched on it")
    }
}
