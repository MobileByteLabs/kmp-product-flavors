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
 * v2.1 Phase 4 — per-variant dependency-guard helper.
 *
 * Closes Q24's documented limitation: "consumers may need to add explicit
 * `dependencyGuard { configuration(\"{variant}CompileClasspath\") }` entries
 * to baseline each variant's classpath".
 *
 * When the consumer opts in via `kmpFlavors.dependencyGuardPerVariant.set(true)`,
 * this helper auto-registers one `dependencyGuard.configuration(...)` entry
 * per (variant × target) — saving the consumer from listing them manually
 * and keeping the baseline set in sync as variants are added/removed.
 *
 * Gated by `pluginManager.withPlugin("com.dropbox.dependency-guard")` so it
 * remains a no-op when the consumer hasn't applied dependency-guard.
 *
 * The reflective call into dependency-guard's extension is intentional: a
 * compile-time dependency on the dependency-guard Gradle plugin would force
 * downstream consumers to pull it transitively. Reflection isolates the
 * coupling to the opt-in code path.
 */
internal object DependencyGuardHelper {

    private const val DEPENDENCY_GUARD_PLUGIN_ID: String = "com.dropbox.dependency-guard"
    private const val DEPENDENCY_GUARD_EXTENSION: String = "dependencyGuard"

    /**
     * Configures per-variant dependency-guard baselines for [project].
     *
     * No-op when [enabled] is false, [allVariants] is empty, or the
     * dependency-guard plugin isn't applied.
     */
    fun configure(project: Project, allVariants: List<FlavorVariant>, targetNames: List<String>, enabled: Boolean, logger: Logger) {
        if (!enabled || allVariants.isEmpty() || targetNames.isEmpty()) return

        project.pluginManager.withPlugin(DEPENDENCY_GUARD_PLUGIN_ID) {
            val ext = project.extensions.findByName(DEPENDENCY_GUARD_EXTENSION)
            if (ext == null) {
                logger.warn(
                    "[KMP Flavors] dependencyGuardPerVariant: extension '$DEPENDENCY_GUARD_EXTENSION' " +
                        "not found despite plugin being applied. Skipping.",
                )
                return@withPlugin
            }
            val configurationMethod = try {
                ext.javaClass.methods.firstOrNull { it.name == "configuration" && it.parameterCount == 1 }
            } catch (e: Exception) {
                logger.warn(
                    "[KMP Flavors] dependencyGuardPerVariant: reflective lookup failed " +
                        "(${e.message}). Skipping.",
                )
                return@withPlugin
            }
            if (configurationMethod == null) {
                logger.warn(
                    "[KMP Flavors] dependencyGuardPerVariant: no `configuration(String)` method " +
                        "on the dependency-guard extension. Plugin version may be incompatible.",
                )
                return@withPlugin
            }

            var registered = 0
            for (variant in allVariants) {
                for (target in targetNames) {
                    // Per-variant compileClasspath naming follows KGP's convention:
                    //   active variant: {target}CompileClasspath (e.g., desktopCompileClasspath)
                    //   inactive variant: {variant}{target}CompileClasspath
                    val targetCap = target.replaceFirstChar { it.uppercase() }
                    val configurationName = "${variant.name}${targetCap}CompileClasspath"
                    try {
                        configurationMethod.invoke(ext, configurationName)
                        registered += 1
                    } catch (e: Exception) {
                        logger.info(
                            "[KMP Flavors] dependencyGuardPerVariant: skipped '$configurationName' " +
                                "(${e.message ?: e::class.simpleName}). The configuration may not exist " +
                                "for this variant × target combination.",
                        )
                    }
                }
            }
            logger.lifecycle(
                "[KMP Flavors] dependencyGuardPerVariant: registered $registered " +
                    "per-variant compileClasspath baselines across ${allVariants.size} variant(s) " +
                    "× ${targetNames.size} target(s).",
            )
        }
    }
}
