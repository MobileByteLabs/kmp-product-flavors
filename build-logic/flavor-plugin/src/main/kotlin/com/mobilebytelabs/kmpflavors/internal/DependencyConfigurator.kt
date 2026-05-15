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

import com.mobilebytelabs.kmpflavors.FlavorVariant
import com.mobilebytelabs.kmpflavors.KmpFlavorVariant
import org.gradle.api.NamedDomainObjectContainer
import org.gradle.api.Project
import org.gradle.api.artifacts.ExcludeRule
import org.gradle.api.logging.Logger

/**
 * Configures per-flavor dependencies for the active variant.
 *
 * This configurator handles:
 * 1. Adding flavor-specific dependencies
 * 2. Resolving matchingFallbacks for project dependencies that don't have matching flavors
 */
class DependencyConfigurator(private val logger: Logger) {

    /**
     * Adds all dependencies from the active variant's flavors.
     *
     * @param project The Gradle project
     * @param activeVariant The currently active variant
     */
    fun configure(project: Project, activeVariant: FlavorVariant) {
        val dependencies = activeVariant.allDependencies

        if (dependencies.isEmpty()) {
            logger.info("[KMP Flavors] No flavor-specific dependencies to add")
            return
        }

        logger.lifecycle("[KMP Flavors] Adding ${dependencies.size} flavor-specific dependencies")

        for (dep in dependencies) {
            try {
                project.dependencies.add(dep.configuration, dep.notation)
                logger.info("[KMP Flavors] Added dependency: ${dep.configuration}(\"${dep.notation}\")")
            } catch (e: Exception) {
                logger.warn(
                    "[KMP Flavors] Failed to add dependency ${dep.configuration}(\"${dep.notation}\"): ${e.message}",
                )
            }
        }
    }

    /**
     * Resolves a variant name using matchingFallbacks.
     *
     * When a dependency module doesn't have the same flavor configuration,
     * this method finds a fallback variant that the module does provide.
     *
     * @param requestedVariant The variant name being requested
     * @param availableVariants List of variant names the dependency module provides
     * @param fallbacks List of fallback flavor names to try
     * @return The resolved variant name, or null if no match found
     */
    fun resolveWithFallbacks(requestedVariant: String, availableVariants: List<String>, fallbacks: List<String>): String? {
        // First, check if exact match exists
        if (requestedVariant in availableVariants) {
            return requestedVariant
        }

        // Try each fallback in order
        for (fallback in fallbacks) {
            if (fallback in availableVariants) {
                logger.info(
                    "[KMP Flavors] Using matchingFallback: $fallback for requested variant $requestedVariant",
                )
                return fallback
            }
        }

        // No match found
        return null
    }

    /**
     * Gets the fallback resolution chain for a variant.
     *
     * This produces a list of variant names to try in order when resolving dependencies:
     * 1. The exact variant name
     * 2. Each matchingFallback in order
     *
     * @param activeVariant The currently active variant
     * @return List of variant names to try in order
     */
    fun getFallbackChain(activeVariant: FlavorVariant): List<String> {
        val chain = mutableListOf(activeVariant.name)
        chain.addAll(activeVariant.mergedMatchingFallbacks)
        return chain.distinct()
    }

    /**
     * v2.4 Phase 5 — apply per-variant dependency excludes registered via
     * `kmpFlavors.variants.matching { … }.configureEach { dependencies { exclude(…) } }`.
     *
     * For each variant with non-empty `dependencies.excludes`, walks every
     * `KotlinCompilation`'s configurations (compile + runtime classpaths)
     * + adds the exclude rule. Excludes are scoped per-variant: a `free`
     * variant's exclude does not affect the `paid` variant's classpath
     * resolution.
     *
     * No-op when:
     *   - No variant has registered excludes (common case for consumers
     *     who don't use the v2.4 Phase 5 DSL).
     *   - The variant has not been wired to a compilation (early reads).
     *
     * @param variants The plugin-extension's `variants` container; iterates
     *                 every registered variant to collect excludes.
     */
    fun applyVariantExcludes(variants: NamedDomainObjectContainer<KmpFlavorVariant>) {
        var totalExcludes = 0
        for (variant in variants) {
            val excludes = variant.dependencies.excludes
            if (excludes.isEmpty()) continue
            for (exclude in excludes) {
                val attrs = mutableMapOf<String, String>()
                if (exclude.group.isNotEmpty()) attrs[ExcludeRule.GROUP_KEY] = exclude.group
                if (exclude.module.isNotEmpty()) attrs[ExcludeRule.MODULE_KEY] = exclude.module
                if (attrs.isEmpty()) {
                    logger.warn(
                        "[KMP Flavors] Phase 5 — variant '${variant.name}' registered an empty " +
                            "exclude(group=\"\", module=\"\"). Skipping (KMPF-V22 — see ERROR_CODES).",
                    )
                    continue
                }
                // Walk each compilation's defaultSourceSet → propagate via the
                // standard classpath configurations Gradle wires per compilation.
                for ((_, compilation) in variant.compilations) {
                    val configNames = listOf(
                        compilation.compileDependencyConfigurationName,
                        compilation.runtimeDependencyConfigurationName ?: "",
                    ).filter { it.isNotEmpty() }
                    for (cfgName in configNames) {
                        val cfg = compilation.target.project.configurations.findByName(cfgName) ?: continue
                        cfg.exclude(attrs)
                        totalExcludes += 1
                    }
                }
            }
        }
        if (totalExcludes > 0) {
            logger.lifecycle(
                "[KMP Flavors] Phase 5 — applied $totalExcludes per-variant " +
                    "dependency exclude(s) across registered variants.",
            )
        }
    }
}
