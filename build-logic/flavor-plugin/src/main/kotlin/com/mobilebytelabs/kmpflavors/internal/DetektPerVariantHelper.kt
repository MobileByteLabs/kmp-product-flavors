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
import org.gradle.api.file.ConfigurableFileTree
import org.gradle.api.logging.Logger

/**
 * v2.1 Phase 4 — per-variant Detekt analysis.
 *
 * Detekt currently runs globally over a project's source tree. With matrix
 * mode, that means free-only code and paid-only code share a single Detekt
 * task, baseline, and rule set — which doesn't reflect how AGP's "Lint per
 * variant" works for Android consumers and was the most-requested
 * adjacent-plugin gap on the v2.0 alpha feedback.
 *
 * When the consumer opts in via `kmpFlavors.detektPerVariant.set(true)`,
 * this helper registers one Detekt task per variant:
 *   - Task name: `detekt{Variant}` (e.g., `detektFreeDev`, `detektPaidProd`).
 *   - Source scope: the variant's source-set hierarchy
 *     (`commonMain + commonFlavor + variant-specific srcDirs`).
 *   - Baseline: `config/detekt/{variant}/baseline.xml`
 *     (consumer can pre-create per-variant baselines; Detekt's standard
 *     `detektBaseline` task variants generate them).
 *   - Config: inherits the consumer's existing `detekt { config }` setting;
 *     can be overridden per variant via the standard Detekt extension's
 *     `detekt { config.setFrom(...) }` if needed.
 *
 * Gated by `pluginManager.withPlugin("io.gitlab.arturbosch.detekt")` so it
 * remains a no-op when the consumer hasn't applied Detekt.
 *
 * Reflective calls keep the helper free of a compile-time dependency on
 * Detekt's `Detekt` task type — different Detekt minor versions have
 * different task-property surfaces.
 *
 * Tasks are registered in the "verification" group following Gradle convention.
 */
internal object DetektPerVariantHelper {

    private const val DETEKT_PLUGIN_ID: String = "io.gitlab.arturbosch.detekt"
    private const val DETEKT_TASK_CLASS_FQ: String = "io.gitlab.arturbosch.detekt.Detekt"

    fun configure(project: Project, allVariants: List<FlavorVariant>, enabled: Boolean, logger: Logger) {
        if (!enabled || allVariants.isEmpty()) return

        project.pluginManager.withPlugin(DETEKT_PLUGIN_ID) {
            val detektTaskClass: Class<*>? = try {
                Class.forName(DETEKT_TASK_CLASS_FQ)
            } catch (e: ClassNotFoundException) {
                logger.warn(
                    "[KMP Flavors] detektPerVariant: Detekt plugin applied but '$DETEKT_TASK_CLASS_FQ' " +
                        "not on the buildscript classpath. Skipping.",
                )
                null
            }
            if (detektTaskClass == null) return@withPlugin

            var registered = 0
            for (variant in allVariants) {
                val variantCap = variant.name.replaceFirstChar { it.uppercase() }
                val taskName = "detekt$variantCap"
                val sourceDirs = collectSourceDirsForVariant(project, variant)

                @Suppress("UNCHECKED_CAST")
                val taskClass = detektTaskClass as Class<org.gradle.api.Task>
                try {
                    // org.gradle.api.tasks.TaskContainer.register(name, type, Action<in T>).
                    // Kotlin's SAM conversion of `Action<T> { … }` is receiver-style and
                    // produces a type mismatch on this signature — use the anonymous-object
                    // form to keep the parameter form straightforward.
                    val configureDetektTask = object : org.gradle.api.Action<org.gradle.api.Task> {
                        override fun execute(detektTask: org.gradle.api.Task) {
                            detektTask.group = "verification"
                            detektTask.description = "Run Detekt analysis for variant '${variant.name}' " +
                                "(scope: ${sourceDirs.joinToString(", ")})"
                            // Reflectively configure Detekt task properties. Detekt task surface:
                            //   - source: ConfigurableFileCollection
                            //   - baseline: RegularFileProperty
                            //   - reports: configurable report block
                            configureSource(detektTask, project, sourceDirs, logger)
                            configureBaseline(detektTask, project, variant.name, logger)
                        }
                    }
                    project.tasks.register(taskName, taskClass, configureDetektTask)
                    registered += 1
                } catch (e: Exception) {
                    logger.warn(
                        "[KMP Flavors] detektPerVariant: failed to register '$taskName' " +
                            "(${e.message ?: e::class.simpleName}). Skipping this variant.",
                    )
                }
            }
            logger.lifecycle(
                "[KMP Flavors] detektPerVariant: registered $registered detekt{Variant} task(s) " +
                    "(one per variant). Baselines resolve under config/detekt/{variant}/baseline.xml.",
            )
        }
    }

    /**
     * Resolve the source directories a variant's Detekt task should scan.
     * Includes commonMain + each per-flavor common source set's `src/.../kotlin` directory.
     */
    private fun collectSourceDirsForVariant(project: Project, variant: FlavorVariant): List<String> {
        val dirs = mutableListOf("src/commonMain/kotlin")
        variant.flavorNames.forEach { flavorName ->
            val ss = "common${flavorName.replaceFirstChar { it.uppercase() }}"
            dirs += "src/$ss/kotlin"
        }
        // Variant-specific srcDir for code unique to one variant.
        dirs += "src/${variant.name}/kotlin"
        return dirs.filter { project.file(it).exists() }.ifEmpty { listOf("src/commonMain/kotlin") }
    }

    private fun configureSource(detektTask: Any, project: Project, dirs: List<String>, logger: Logger) {
        try {
            val sourceMethod = detektTask.javaClass.methods.firstOrNull {
                it.name == "setSource" && it.parameterCount == 1
            }
            if (sourceMethod != null) {
                val tree: ConfigurableFileTree = project.fileTree(
                    mapOf(
                        "dir" to project.projectDir,
                        "includes" to dirs.map { "$it/**/*.kt" },
                    ),
                )
                sourceMethod.invoke(detektTask, tree)
            }
        } catch (e: Exception) {
            logger.info(
                "[KMP Flavors] detektPerVariant: setSource reflective call failed " +
                    "(${e.message}). Task will fall back to its default source scope.",
            )
        }
    }

    private fun configureBaseline(detektTask: Any, project: Project, variantName: String, logger: Logger) {
        try {
            val getBaselineMethod = detektTask.javaClass.methods.firstOrNull { it.name == "getBaseline" }
            if (getBaselineMethod != null) {
                val baselineProperty = getBaselineMethod.invoke(detektTask)
                val setMethod = baselineProperty.javaClass.methods.firstOrNull {
                    it.name == "set" && it.parameterCount == 1 && it.parameterTypes[0] == java.io.File::class.java
                } ?: baselineProperty.javaClass.methods.firstOrNull {
                    it.name == "fileValue" && it.parameterCount == 1
                }
                if (setMethod != null) {
                    val baselineFile = project.file("config/detekt/$variantName/baseline.xml")
                    setMethod.invoke(baselineProperty, baselineFile)
                }
            }
        } catch (e: Exception) {
            logger.info(
                "[KMP Flavors] detektPerVariant: baseline reflective wiring failed for variant " +
                    "'$variantName' (${e.message}). Consumer can wire it manually.",
            )
        }
    }
}
