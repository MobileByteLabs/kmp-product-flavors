/*
 * Copyright 2026 MobileByteLabs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.mobilebytelabs.kmpflavors.internal

import org.gradle.api.Action

/**
 * v2.6 Phase 2 — reflection-shaped mock of AGP's `AndroidComponentsExtension` for
 * unit testing [AgpBridge.propagateVariantFilterToAgp] without a real AGP
 * classpath.
 *
 * AgpBridge looks up `beforeVariants(selector, action)` reflectively, so this
 * mock just needs a method with the right name + arity. The mock captures the
 * registered Action and exposes a [fireRegisteredAction] entry point so tests
 * can simulate AGP iterating variants.
 *
 * Companion mock [MockVariantBuilder] satisfies AgpBridge's
 * `getName()` + `setEnabled(boolean)` reflection lookups.
 *
 * Matches the Phase 1 [MockAndroidExtension] pattern (Kotlin properties whose
 * generated setX(String) JVM signature is matched by AgpBridge's
 * `methods.firstOrNull { it.name == "setX" && parameterCount == 1 }`).
 */
open class MockAndroidComponentsExtension {

    private var registeredAction: Action<Any>? = null

    /**
     * Reflective AgpBridge target — captures the Action proxy for later firing.
     * Signature mirrors AGP's `AndroidComponentsExtension.beforeVariants(selector, action)`.
     */
    @Suppress("UNCHECKED_CAST", "UNUSED_PARAMETER")
    fun beforeVariants(selector: Any?, action: Any) {
        registeredAction = action as Action<Any>
    }

    /**
     * Simulate AGP iterating variants — fires the captured action against each
     * provided builder.
     */
    fun fireRegisteredAction(variantBuilder: Any) {
        registeredAction?.execute(variantBuilder)
    }
}

/**
 * Standard mock variant builder — supports the full `getName` + `setEnabled` shape
 * that AgpBridge's reflection lookup expects.
 *
 * Kotlin generates `getName(): String` for the `val name` constructor property
 * and `setEnabled(boolean)` for the `var enabled` property — both match
 * AgpBridge's reflection signature checks. Do NOT add explicit `fun getName()` /
 * `fun setEnabled()` (same JVM-signature-clash pattern as MockAndroidExtension).
 */
class MockVariantBuilder(val name: String) {
    var enabled: Boolean = true
}

/**
 * Reflection-shaped mock that omits `setEnabled` — exercises the AGP-fallback
 * WARN branch of [AgpBridge.propagateVariantFilterToAgp]. Field `enabled` is
 * exposed as a read-only `val` so AgpBridge's
 * `methods.firstOrNull { it.name == "setEnabled" && parameterCount == 1 }`
 * returns null (only the auto-generated getter exists), and the WARN branch fires.
 */
class MockVariantBuilderMissingSetEnabled(val name: String) {
    val enabled: Boolean = true
}
