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

/**
 * v2.4 Phase 5 — variant-conditional dependency configuration scope.
 *
 * Exposed via [KmpFlavorVariant.dependencies] for consumers using
 * `kmpFlavors.variants.matching { … }.configureEach { dependencies { exclude(…) } }`.
 *
 * Currently exposes [exclude] only. Future v2.x revisions may add
 * `add(group, module)` for variant-only deps (the inverse direction); for
 * now the v2.1 per-flavor `dependencies { … }` block on
 * `KmpFlavorPluginExtension.flavors.register("…")` covers variant-add.
 *
 * Excludes registered here are applied by
 * [com.mobilebytelabs.kmpflavors.internal.DependencyConfigurator] after
 * the per-variant compilation classpaths are wired.
 */
abstract class VariantDependenciesScope {

    /**
     * Collected exclude requests. Internal — read by `DependencyConfigurator` at
     * apply time. The [Exclude] data class is intentionally simple (only group
     * + module); v2.5+ may extend with classifier / configuration scope if
     * consumer requests surface.
     */
    internal val excludes: MutableList<Exclude> = mutableListOf()

    /**
     * Add a variant-conditional exclude. Matches Gradle's standard
     * `Configuration.exclude(group, module)` shape so adopters familiar
     * with `configurations.runtimeClasspath { exclude(...) }` get a
     * familiar API.
     *
     * Example:
     * ```kotlin
     * kmpFlavors.variants.matching { it.flavors.contains("free") }.configureEach {
     *     dependencies {
     *         exclude(group = "com.example", module = "premium-sdk")
     *     }
     * }
     * ```
     *
     * Both parameters are required; pass empty string to wildcard a side
     * (Gradle treats `group = ""` as "any group"). Passing both empty
     * triggers KMPF-V22 at apply time.
     *
     * @param group Maven coordinate group (e.g. `"com.example"`).
     * @param module Maven coordinate module / artifactId (e.g. `"premium-sdk"`).
     */
    fun exclude(group: String, module: String) {
        excludes += Exclude(group = group, module = module)
    }

    /**
     * Internal record of an exclude. Held until apply-time when
     * `DependencyConfigurator` translates each entry into per-variant-
     * compilation classpath `exclude(group, module)` calls.
     */
    internal data class Exclude(val group: String, val module: String)
}
