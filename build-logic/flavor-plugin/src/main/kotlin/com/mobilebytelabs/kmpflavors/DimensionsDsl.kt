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

package com.mobilebytelabs.kmpflavors

import org.gradle.api.Action

/**
 * v2.5 — ergonomic `dimensions { dimension(name) { flavor(name) {...} } }` sugar block.
 *
 * Purely additive over the v2.4 flat DSL (`flavorDimensions { register(...) } + flavors { register(...) { dimension.set(...) } }`).
 * The underlying state is unchanged: both styles populate the same
 * [KmpFlavorExtension.flavorDimensions] + [KmpFlavorExtension.flavors] containers, so
 * downstream `FlavorVariantResolver` Cartesian logic, matrix mode, BuildKonfig codegen,
 * and AGP bridging are byte-identical regardless of which DSL the consumer used.
 *
 * Mixing the new `dimensions {}` block with the legacy `flavorDimensions {} + flavors {}`
 * pair in the same `kmpFlavors {}` is a configuration error and fires `KMPF-V24` at
 * validation time. Pick one style per project.
 *
 * Example:
 * ```kotlin
 * kmpFlavors {
 *     dimensions {
 *         dimension("tier") {
 *             flavor("free") {
 *                 buildConfigField("Boolean", "PREMIUM", "false")
 *             }
 *             flavor("paid") {
 *                 buildConfigField("Boolean", "PREMIUM", "true")
 *             }
 *         }
 *         dimension("env") {
 *             flavor("dev")
 *             flavor("prod")
 *         }
 *     }
 * }
 * ```
 *
 * Equivalent in v2.4 flat DSL:
 * ```kotlin
 * kmpFlavors {
 *     flavorDimensions {
 *         register("tier")
 *         register("env")
 *     }
 *     flavors {
 *         register("free") {
 *             dimension.set("tier")
 *             buildConfigField("Boolean", "PREMIUM", "false")
 *         }
 *         register("paid") {
 *             dimension.set("tier")
 *             buildConfigField("Boolean", "PREMIUM", "true")
 *         }
 *         register("dev") { dimension.set("env") }
 *         register("prod") { dimension.set("env") }
 *     }
 * }
 * ```
 *
 * Both produce the same 4 resolved variants (`freeDev`, `freePrd`, `paidDev`, `paidPrd`) — same
 * downstream codegen, same AGP cross-product behavior, same matrix mode compilations.
 *
 * @see KmpFlavorExtension.dimensions
 * @see DimensionScope
 */
open class DimensionsDsl internal constructor(private val extension: KmpFlavorExtension) {
    /**
     * Declare a flavor dimension and (optionally) configure the flavors that belong to it.
     *
     * Under the hood, registers `name` in [KmpFlavorExtension.flavorDimensions] (idempotent
     * via `maybeCreate`), then opens a [DimensionScope] for declaring per-dimension flavors
     * via `flavor("name") { ... }`.
     */
    fun dimension(name: String, action: Action<DimensionScope> = Action {}) {
        extension.flavorDimensions.maybeCreate(name)
        val scope = DimensionScope(name, extension)
        action.execute(scope)
        // Mark that the v2.5 sugar DSL was used. Validator (KMPF-V24) will halt if the
        // legacy flat blocks are ALSO used in the same kmpFlavors {} call.
        extension.markDimensionsDslUsed()
    }
}

/**
 * Per-dimension scope for declaring flavors inside the [DimensionsDsl.dimension] block.
 *
 * Each `flavor("name") { ... }` registers the named flavor in [KmpFlavorExtension.flavors]
 * (idempotent via `maybeCreate`) and sets its `dimension.set(<this-dimension-name>)`
 * automatically, so consumers don't need to repeat the dimension assignment per flavor.
 */
class DimensionScope internal constructor(private val dimensionName: String, private val extension: KmpFlavorExtension) {
    /**
     * Declare a flavor inside the enclosing `dimension(name) { }` block.
     *
     * The flavor's `dimension.set(dimensionName)` is wired automatically. Inside the
     * `action` block, consumers configure the flavor normally — `buildConfigField(...)`,
     * `applicationIdSuffix.set(...)`, `dependency(...)`, etc.
     */
    fun flavor(name: String, action: Action<FlavorConfig> = Action {}) {
        val flavor = extension.flavors.maybeCreate(name)
        flavor.dimension.set(dimensionName)
        action.execute(flavor)
    }

    /**
     * Read-only accessor for the enclosing dimension name. Useful for consumer code that
     * wants to inspect the active scope without re-parsing the DSL.
     */
    fun getDimensionName(): String = dimensionName
}
