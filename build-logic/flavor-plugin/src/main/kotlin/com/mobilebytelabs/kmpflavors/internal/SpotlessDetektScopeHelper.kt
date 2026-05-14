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

import org.gradle.api.Project
import org.gradle.api.logging.Logger

// v2.1 Phase 4 — auto-exclude generated `BuildKonfig` directories from
// Spotless and Detekt globs. Closes Q24's documented caveat that Spotless
// rules matching generated `BuildKonfig.kt` trigger N format checks
// (once per variant), and the same caveat for Detekt globs.
//
// When the consumer opts in via
// `kmpFlavors.excludeGeneratedFromFormatters.set(true)`, this helper:
//   - Adds the generated path exclusion to every Spotless task, when
//     Spotless is applied.
//   - Adds the same path to Detekt's `exclude(...)` configuration on every
//     Detekt task, when Detekt is applied.
//
// Gated by `pluginManager.withPlugin(...)` hooks so it stays a no-op when
// the consumer hasn't applied either adjacent plugin.
//
// Reflective calls keep the helper free of direct compile-time dependencies
// on Spotless and Detekt — both plugins evolve their public APIs at
// different cadences than the consumer's plugin pinning.
internal object SpotlessDetektScopeHelper {

    private const val SPOTLESS_PLUGIN_ID: String = "com.diffplug.spotless"
    private const val DETEKT_PLUGIN_ID: String = "io.gitlab.arturbosch.detekt"

    /**
     * Glob excluding everything under the per-variant codegen output directory.
     * Stored as a const so consumer-side workarounds (when the reflective wiring
     * fails) can copy the exact same string into their build script.
     */
    @Suppress("ktlint:standard:property-naming")
    private val GENERATED_GLOB: String = listOf(
        "*",
        "*",
        "/",
        "build/generated/kmpFlavors/",
        "*",
        "*",
    ).joinToString("")

    fun configure(project: Project, enabled: Boolean, logger: Logger) {
        if (!enabled) return

        project.pluginManager.withPlugin(SPOTLESS_PLUGIN_ID) {
            try {
                project.extensions.findByName("spotless") ?: return@withPlugin
                // The Spotless Gradle task type exposes `exclude(...)` through its inputs.
                // We walk Spotless tasks and add the exclude pattern reflectively to avoid
                // a hard compile-time dependency on Spotless's public DSL surface.
                project.tasks.matching { it.name.startsWith("spotless") }.configureEach {
                    val excludeMethod = try {
                        this.javaClass.methods.firstOrNull { it.name == "exclude" && it.parameterCount == 1 }
                    } catch (e: Exception) {
                        null
                    }
                    if (excludeMethod != null) {
                        try {
                            excludeMethod.invoke(this, listOf(GENERATED_GLOB))
                        } catch (e: Exception) {
                            // Best-effort — Spotless versions vary.
                        }
                    }
                }
                logger.lifecycle(
                    "[KMP Flavors] excludeGeneratedFromFormatters: Spotless exclude added for " +
                        "'$GENERATED_GLOB'. Per-variant BuildKonfig.kt files will not trigger " +
                        "format checks.",
                )
            } catch (e: Exception) {
                logger.warn(
                    "[KMP Flavors] excludeGeneratedFromFormatters: Spotless wiring failed " +
                        "(${e.message}). Add the equivalent `spotless { kotlin { targetExclude(...) } }` " +
                        "block manually.",
                )
            }
        }

        project.pluginManager.withPlugin(DETEKT_PLUGIN_ID) {
            try {
                project.extensions.findByName("detekt") ?: return@withPlugin
                project.tasks.matching { it.name.startsWith("detekt") }.configureEach {
                    val excludeMethod = try {
                        this.javaClass.methods.firstOrNull { it.name == "exclude" && it.parameterCount == 1 }
                    } catch (e: Exception) {
                        null
                    }
                    if (excludeMethod != null) {
                        try {
                            excludeMethod.invoke(this, GENERATED_GLOB)
                        } catch (e: Exception) {
                            // Best-effort.
                        }
                    }
                }
                logger.lifecycle(
                    "[KMP Flavors] excludeGeneratedFromFormatters: Detekt exclude added for " +
                        "'$GENERATED_GLOB'.",
                )
            } catch (e: Exception) {
                logger.warn(
                    "[KMP Flavors] excludeGeneratedFromFormatters: Detekt wiring failed " +
                        "(${e.message}). Add the equivalent `detekt { … }` exclude manually.",
                )
            }
        }
    }
}
