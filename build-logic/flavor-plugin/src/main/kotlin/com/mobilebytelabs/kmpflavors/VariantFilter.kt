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
 * Context for filtering variants during resolution.
 *
 * This class provides information about a variant being evaluated and allows
 * excluding it from the final variant list.
 *
 * Example usage:
 * ```kotlin
 * kmpFlavors {
 *     variantFilter {
 *         // Exclude freeProd variant
 *         if (flavorNames.containsAll(listOf("free", "prod"))) {
 *             exclude()
 *         }
 *
 *         // Or using variant name
 *         if (variantName == "freeProd") {
 *             exclude()
 *         }
 *     }
 * }
 * ```
 *
 * @property variantName The full variant name (e.g., "freeDev", "paidProd")
 * @property flavorNames The list of flavor names in this variant
 * @property flavors The list of FlavorConfig objects in this variant
 */
class VariantFilter(
    val variantName: String,
    val flavorNames: List<String>,
    val flavors: List<FlavorConfig>,
    /**
     * The build type name for this variant, or `null` when build types
     * are not enabled. Added in v2.0 for RFC §3 Q20-A AGP-parity:
     *
     * ```kotlin
     * variantFilter {
     *     if (flavors.contains("paid") && buildType == "staging") setIgnore(true)
     * }
     * ```
     */
    val buildType: String? = null,
    /**
     * v2.6 — the set of Kotlin target names available in this project
     * (`"desktop"`, `"iosArm64"`, `"watchosArm64"`, etc.). Surfaced as a
     * read-only field so consumer filters can reason about which target
     * names are valid for [excludeTargets].
     */
    val availableTargets: Set<String> = emptySet(),
) {
    private var excluded = false
    private val excludedTargets: MutableSet<String> = mutableSetOf()

    /**
     * Marks this variant to be excluded from the build.
     * Once excluded, the variant will not appear in the list of available variants.
     */
    fun exclude() {
        excluded = true
    }

    /**
     * AGP-style synonym for [exclude] (RFC §3 Q20-A). Lowers the friction
     * for Android developers migrating to KMP. Passing `false` resets the
     * exclusion flag — matches AGP semantics where later filter actions can
     * override earlier ones.
     */
    fun setIgnore(ignore: Boolean) {
        excluded = ignore
    }

    /**
     * Checks if this variant's build type matches [name].
     * Returns `false` when build types are disabled and [buildType] is `null`.
     */
    fun hasBuildType(name: String): Boolean = buildType == name

    /**
     * Returns whether this variant has been marked for exclusion.
     */
    internal fun isExcluded(): Boolean = excluded

    /**
     * Checks if this variant contains a specific flavor.
     *
     * @param flavorName The flavor name to check
     * @return true if the variant contains the flavor
     */
    fun hasFlavor(flavorName: String): Boolean = flavorNames.contains(flavorName)

    /**
     * Checks if this variant contains all the specified flavors.
     *
     * @param names The flavor names to check
     * @return true if the variant contains all the specified flavors
     */
    fun hasAllFlavors(vararg names: String): Boolean = flavorNames.containsAll(names.toList())

    /**
     * Checks if this variant contains any of the specified flavors.
     *
     * @param names The flavor names to check
     * @return true if the variant contains at least one of the specified flavors
     */
    fun hasAnyFlavor(vararg names: String): Boolean = names.any { it in flavorNames }

    /**
     * Gets the flavor from a specific dimension.
     *
     * @param dimensionName The dimension name
     * @return The flavor name from that dimension, or null if not found
     */
    fun getFlavorFromDimension(dimensionName: String): String? = flavors.find { it.dimension.orNull == dimensionName }?.name

    /**
     * v2.6 — exclude this variant from the named Kotlin targets. The variant
     * itself stays in the resolved set; only the per-target compilations for
     * the named targets are skipped. Common use case: gate CI-expensive
     * targets (`watchosArm64`, `tvosArm64`) on the active variant's tier.
     *
     * ```kotlin
     * variantFilter {
     *     if (flavorNames.contains("free")) {
     *         excludeTargets("watchosArm64", "watchosX64", "tvosArm64", "tvosX64")
     *     }
     * }
     * ```
     *
     * Target names must match the names declared in the `kotlin { ... }` block
     * exactly (the same strings you'd find in [availableTargets]). Globs are
     * not supported in v2.6 — pass each target by name.
     *
     * @throws IllegalArgumentException when called with zero target names.
     */
    fun excludeTargets(vararg targets: String) {
        require(targets.isNotEmpty()) { "excludeTargets() requires at least one target name" }
        targets.forEach { excludedTargets.add(it) }
    }

    /** v2.6 internal — query whether this variant excluded a given target. */
    internal fun isTargetExcluded(target: String): Boolean = target in excludedTargets

    /** v2.6 internal — snapshot of declared excluded-target names. */
    internal fun excludedTargetsSnapshot(): Set<String> = excludedTargets.toSet()
}
