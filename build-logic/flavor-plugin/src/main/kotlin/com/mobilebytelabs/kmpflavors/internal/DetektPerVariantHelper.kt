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
import org.jetbrains.kotlin.gradle.plugin.KotlinTarget

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

    fun configure(
        project: Project,
        allVariants: List<FlavorVariant>,
        enabled: Boolean,
        logger: Logger,
        perTarget: Boolean = false,
        nonAndroidTargets: List<KotlinTarget> = emptyList(),
    ) {
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

            @Suppress("UNCHECKED_CAST")
            val taskClass = detektTaskClass as Class<org.gradle.api.Task>

            // v2.3 Phase 1 — per-(variant × target) mode. Branches on the perTarget
            // flag. Both modes share the same task-registration helper below.
            if (perTarget && nonAndroidTargets.isNotEmpty()) {
                registerPerVariantPerTarget(project, allVariants, nonAndroidTargets, taskClass, logger)
            } else {
                registerPerVariant(project, allVariants, taskClass, logger)
            }
        }
    }

    /**
     * v2.1 mode — one `detekt{Variant}` per variant.
     */
    private fun registerPerVariant(project: Project, allVariants: List<FlavorVariant>, taskClass: Class<org.gradle.api.Task>, logger: Logger) {
        var registered = 0
        for (variant in allVariants) {
            val variantCap = variant.name.replaceFirstChar { it.uppercase() }
            val taskName = "detekt$variantCap"
            val sourceDirs = collectSourceDirsForVariant(project, variant)

            try {
                val configureDetektTask = object : org.gradle.api.Action<org.gradle.api.Task> {
                    override fun execute(detektTask: org.gradle.api.Task) {
                        detektTask.group = "verification"
                        detektTask.description = "Run Detekt analysis for variant '${variant.name}' " +
                            "(scope: ${sourceDirs.joinToString(", ")})"
                        configureSource(detektTask, project, sourceDirs, logger)
                        configureBaseline(detektTask, project, variant.name, null, logger)
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

    /**
     * v2.3 Phase 1 mode — `detekt{Variant}{Target}` per (variant × non-Android target),
     * plus a `detekt{Variant}` aggregate task that depends on its per-target subtasks
     * (so consumers can run the existing variant-scoped detekt command + get the
     * fan-out for free).
     */
    private fun registerPerVariantPerTarget(
        project: Project,
        allVariants: List<FlavorVariant>,
        nonAndroidTargets: List<KotlinTarget>,
        taskClass: Class<org.gradle.api.Task>,
        logger: Logger,
    ) {
        var registered = 0
        for (variant in allVariants) {
            val variantCap = variant.name.replaceFirstChar { it.uppercase() }
            val sourceDirs = collectSourceDirsForVariant(project, variant)

            val perTargetTaskNames = mutableListOf<String>()
            for (target in nonAndroidTargets) {
                val targetCap = target.name.replaceFirstChar { it.uppercase() }
                val taskName = "detekt$variantCap$targetCap"
                val targetSourceDirs = sourceDirs + "src/${variant.name}/${target.name}/kotlin"
                val filteredSources = targetSourceDirs.filter { project.file(it).exists() }
                    .ifEmpty { sourceDirs }

                try {
                    val configureDetektTask = object : org.gradle.api.Action<org.gradle.api.Task> {
                        override fun execute(detektTask: org.gradle.api.Task) {
                            detektTask.group = "verification"
                            detektTask.description = "Run Detekt analysis for variant '${variant.name}' " +
                                "on target '${target.name}' (scope: ${filteredSources.joinToString(", ")})"
                            configureSource(detektTask, project, filteredSources, logger)
                            configureBaseline(detektTask, project, variant.name, target.name, logger)
                        }
                    }
                    project.tasks.register(taskName, taskClass, configureDetektTask)
                    perTargetTaskNames += taskName
                    registered += 1
                } catch (e: Exception) {
                    logger.warn(
                        "[KMP Flavors] detektPerVariantPerTarget: failed to register '$taskName' " +
                            "(${e.message ?: e::class.simpleName}). Skipping this (variant × target).",
                    )
                }
            }

            // Variant-level aggregate task — runs every per-target subtask for the variant.
            // Uses untyped registration; aggregate doesn't run Detekt directly. Use the
            // anonymous-object Action<Task> form to bypass Kotlin's SAM-conversion of
            // `{ task -> ... }` to a receiver-style lambda (same pattern as the typed
            // registrations above + PerVariantIosXcframeworkConfigurator).
            val aggregateName = "detekt$variantCap"
            val captured = perTargetTaskNames.toList()
            try {
                project.tasks.register(
                    aggregateName,
                    object : org.gradle.api.Action<org.gradle.api.Task> {
                        override fun execute(task: org.gradle.api.Task) {
                            task.group = "verification"
                            task.description = "Aggregate: runs detekt{Variant}{Target} subtasks for variant " +
                                "'${variant.name}' across ${nonAndroidTargets.size} target(s)."
                            task.dependsOn(captured)
                        }
                    },
                )
            } catch (e: Exception) {
                logger.info(
                    "[KMP Flavors] detektPerVariantPerTarget: aggregate task '$aggregateName' " +
                        "already exists (${e.message}). Skipping aggregate registration.",
                )
            }
        }
        logger.lifecycle(
            "[KMP Flavors] detektPerVariantPerTarget: registered $registered detekt{Variant}{Target} " +
                "task(s) across ${allVariants.size} variant(s) × ${nonAndroidTargets.size} target(s). " +
                "Baselines resolve under config/detekt/{variant}/{target}/baseline.xml.",
        )
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

    private fun configureBaseline(detektTask: Any, project: Project, variantName: String, targetName: String?, logger: Logger) {
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
                    val baselinePath = if (targetName != null) {
                        "config/detekt/$variantName/$targetName/baseline.xml"
                    } else {
                        "config/detekt/$variantName/baseline.xml"
                    }
                    val baselineFile = project.file(baselinePath)
                    setMethod.invoke(baselineProperty, baselineFile)
                }
            }
        } catch (e: Exception) {
            logger.info(
                "[KMP Flavors] detektPerVariant: baseline reflective wiring failed for variant " +
                    "'$variantName'${if (targetName != null) " on target '$targetName'" else ""} " +
                    "(${e.message}). Consumer can wire it manually.",
            )
        }
    }
}
