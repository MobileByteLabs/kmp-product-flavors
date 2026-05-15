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
import org.gradle.api.Project
import org.gradle.api.logging.Logger

/**
 * v2.2 Phase 3A — per-variant Build Scan tagging.
 *
 * When the consumer applies the Gradle Develocity plugin (`com.gradle.develocity`),
 * this configurator tags every per-variant compile task with `variant={variantName}`
 * via Develocity's custom-value API. Tagging makes per-variant build time, cache
 * hit rate, and classpath resolution aggregable in the Build Scan UI — load-bearing
 * for shops running 100+ variants × 10+ modules at scale.
 *
 * Mechanism (reflective to keep the plugin free of a hard Develocity classpath):
 *   1. `pluginManager.withPlugin("com.gradle.develocity")` callback.
 *   2. Walk `project.tasks.matching { it.name.startsWith("compile") }` and find
 *      the per-variant compile tasks (`compile{Variant}Kotlin{Target}`).
 *   3. For each such task, register a finalizer or task-input hook that calls
 *      Develocity's `buildScan.value("variant", variantName)` API.
 *
 * No-op when:
 *   - Develocity plugin isn't applied.
 *   - Matrix mode is off (no per-variant tasks to tag).
 *   - `allVariants` is empty.
 *
 * Reflective access — Develocity's `BuildScanExtension` is in
 * `com.gradle.develocity.agent.gradle.scan` and its API surface evolves across
 * versions. We use `extensions.findByName("develocity")` and walk the typed
 * `buildScan { … }` block reflectively. Best-effort: failure to wire one task
 * doesn't fail the whole configuration.
 */
internal object BuildScanConfigurator {

    private const val DEVELOCITY_PLUGIN_ID: String = "com.gradle.develocity"

    fun configure(
        project: Project,
        allVariants: List<FlavorVariant>,
        nonAndroidTargets: List<org.jetbrains.kotlin.gradle.plugin.KotlinTarget>,
        matrixModeEnabled: Boolean,
        logger: Logger,
    ) {
        if (!matrixModeEnabled || allVariants.isEmpty()) return

        project.pluginManager.withPlugin(DEVELOCITY_PLUGIN_ID) {
            val develocityExt = project.extensions.findByName("develocity") ?: run {
                logger.info(
                    "[KMP Flavors] Phase 3A — Develocity plugin applied but `develocity` extension " +
                        "not found; skipping Build Scan per-variant tagging.",
                )
                return@withPlugin
            }
            val buildScan = extractBuildScan(develocityExt) ?: run {
                logger.info(
                    "[KMP Flavors] Phase 3A — `develocity.buildScan` not resolvable on this " +
                        "Develocity version; skipping Build Scan per-variant tagging.",
                )
                return@withPlugin
            }

            // Tag the build with the set of registered variants up-front so the scan summary
            // names them even before any compile task fires.
            val variantNames = allVariants.joinToString(",") { it.name }
            invokeValue(buildScan, "kmpFlavors.variants", variantNames, logger)
            invokeValue(buildScan, "kmpFlavors.variantCount", allVariants.size.toString(), logger)

            // For each variant × target compile task, attach a doFirst hook that calls
            // buildScan.value("kmpFlavors.variant", variantName) so the per-variant timing data
            // accrues against the right tag.
            var tagged = 0
            for (variant in allVariants) {
                for (target in nonAndroidTargets) {
                    val variantCap = variant.name.replaceFirstChar { it.uppercase() }
                    val targetCap = target.name.replaceFirstChar { it.uppercase() }
                    val taskName = "compile${variantCap}Kotlin$targetCap"
                    project.tasks.matching { it.name == taskName }.configureEach {
                        doFirst {
                            invokeValue(buildScan, "kmpFlavors.variant", variant.name, logger)
                            invokeValue(buildScan, "kmpFlavors.target", target.name, logger)
                        }
                    }
                    tagged += 1
                }
            }

            logger.lifecycle(
                "[KMP Flavors] Phase 3A — Build Scan tagging wired for ${allVariants.size} " +
                    "variant(s) × ${nonAndroidTargets.size} target(s) ($tagged tasks tagged). " +
                    "Per-variant build time + cache hit rate accrue against `kmpFlavors.variant` + " +
                    "`kmpFlavors.target` custom values in the Build Scan UI.",
            )
        }
    }

    /**
     * Reflectively extracts the `buildScan` accessor from the Develocity extension. Returns
     * null when not resolvable (incompatible Develocity version).
     */
    private fun extractBuildScan(develocityExt: Any): Any? = try {
        val getter = develocityExt.javaClass.methods.firstOrNull { it.name == "getBuildScan" }
        getter?.invoke(develocityExt)
    } catch (e: Exception) {
        null
    }

    /**
     * Reflectively calls `buildScan.value(name, value)` (the canonical Develocity API for
     * custom values). Logs at info level on reflective failure; never throws.
     */
    private fun invokeValue(buildScan: Any, name: String, value: String, logger: Logger) {
        try {
            val method = buildScan.javaClass.methods.firstOrNull {
                it.name == "value" && it.parameterCount == 2
            }
            if (method != null) {
                method.invoke(buildScan, name, value)
            }
        } catch (e: Exception) {
            logger.info("[KMP Flavors] Phase 3A — buildScan.value reflective call failed: ${e.message}")
        }
    }
}
