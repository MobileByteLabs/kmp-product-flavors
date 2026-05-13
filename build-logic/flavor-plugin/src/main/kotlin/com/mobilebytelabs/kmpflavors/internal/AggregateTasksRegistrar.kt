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
import org.jetbrains.kotlin.gradle.plugin.KotlinTarget

/**
 * Registers Q18-C aggregate tasks:
 *
 *   `assembleAll{Target}Variants`   — runs every variant's compilation
 *                                     for a single KMP target. Used by
 *                                     CI matrix jobs sharding by target.
 *
 *   `assembleAllVariants`           — super-aggregate that depends on
 *                                     every `assembleAll{Target}Variants`.
 *                                     Developer-convenience entry point.
 *
 * Each aggregate is a vanilla `DefaultTask` with `dependsOn` wiring —
 * no custom task class, no per-task inputs/outputs (the work happens
 * in the underlying compile tasks). Tasks land in the
 * `kmpFlavors variants` group (RFC §3 Q9 ergonomics).
 *
 * Both the active variant (compiled via the target's `main`
 * compilation, i.e. `compile{Kotlin}{Target}`) and inactive variants
 * (compiled via per-variant tasks `compile{Variant}{Kotlin}{Target}`)
 * are included so the aggregate matches AGP's `assemble` semantics
 * (everything builds, no variant is skipped).
 */
internal object AggregateTasksRegistrar {

    private const val TASK_GROUP = "kmpFlavors variants"

    fun register(
        project: Project,
        nonAndroidTargets: List<KotlinTarget>,
        activeVariant: FlavorVariant,
        inactiveVariants: List<FlavorVariant>,
    ) {
        if (nonAndroidTargets.isEmpty()) return

        val perTargetAggregateNames = mutableListOf<String>()

        for (target in nonAndroidTargets) {
            val targetCap = target.name.replaceFirstChar { it.uppercase() }
            val aggregateName = "assembleAll${targetCap}Variants"
            perTargetAggregateNames += aggregateName

            // Collect compile task names for the active variant (main compilation)
            // + each inactive variant (per-variant compilation).
            val compileTaskNames = mutableListOf<String>()
            compileTaskNames += "compileKotlin$targetCap"
            inactiveVariants.forEach { v ->
                val variantCap = v.name.replaceFirstChar { it.uppercase() }
                compileTaskNames += "compile${variantCap}Kotlin$targetCap"
            }

            project.tasks.register(aggregateName) {
                this.group = TASK_GROUP
                this.description =
                    "Compile every kmpFlavors variant on target '${target.name}' " +
                        "(${1 + inactiveVariants.size} variant(s))."
                compileTaskNames.forEach { taskName ->
                    dependsOn(project.tasks.named(taskName))
                }
            }
        }

        // Super-aggregate: walk every target × variant in one invocation.
        project.tasks.register("assembleAllVariants") {
            this.group = TASK_GROUP
            this.description =
                "Compile every kmpFlavors variant on every non-Android target " +
                    "(${nonAndroidTargets.size} target(s) × ${1 + inactiveVariants.size} variant(s))."
            perTargetAggregateNames.forEach { name ->
                dependsOn(project.tasks.named(name))
            }
        }
    }
}
