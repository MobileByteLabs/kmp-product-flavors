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

package com.mobilebytelabs.kmpflavors.tasks

import org.gradle.api.DefaultTask
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction

/**
 * RFC §3 Q13 — `listVariantCompilations` task.
 *
 * Prints the full variant × target compilation matrix as a Markdown table.
 * Answers the question: "what did matrix mode actually register on this module?".
 *
 * Output shape:
 * ```
 * | Variant      | Status   | desktopJvm | iosArm64 |
 * |--------------|----------|------------|----------|
 * | freeDev      | ACTIVE   | main       | main     |
 * | freeStaging  | inactive | freeStaging| freeStaging |
 * | paidDev      | inactive | paidDev    | paidDev  |
 * ```
 *
 * All inputs are captured at configuration time so the task is
 * configuration-cache-friendly.
 */
abstract class ListVariantCompilationsTask : DefaultTask() {

    /**
     * Variant names in registration order.
     */
    @get:Input
    abstract val allVariantNames: ListProperty<String>

    /**
     * Target names in registration order (left to right in the table).
     */
    @get:Input
    abstract val allTargetNames: ListProperty<String>

    /**
     * Map keyed by `"$variantName::$targetName"` to the registered compilation
     * name on that target. Pre-computed at configuration time so the task
     * doesn't traverse the Kotlin extension at action time.
     *
     * Empty value means "no compilation registered for this variant × target"
     * (typically: the variant is excluded by a filter that doesn't apply to
     * other targets, or matrix mode is off for that target).
     */
    @get:Input
    abstract val compilationByVariantTarget: MapProperty<String, String>

    /**
     * The active variant name, marked as `ACTIVE` in the rendered table.
     */
    @get:Input
    abstract val activeVariantName: Property<String>

    init {
        group = "kmp flavors"
        description = "Prints the variant × target compilation matrix as a Markdown table"
    }

    @TaskAction
    fun list() {
        val variants = allVariantNames.get()
        val targets = allTargetNames.get()
        val compilations = compilationByVariantTarget.get()
        val active = activeVariantName.get()

        if (variants.isEmpty()) {
            println()
            println("KMPF listVariantCompilations: no variants resolved on this module.")
            println()
            return
        }
        if (targets.isEmpty()) {
            println()
            println("KMPF listVariantCompilations: no non-Android KMP targets on this module — matrix mode is a no-op here.")
            println()
            return
        }

        // Column widths.
        val variantColWidth = maxOf("Variant".length, variants.maxOf { it.length })
        val statusColWidth = maxOf("Status".length, "inactive".length)
        val perTargetWidth = targets.associateWith { target ->
            val cellWidths = variants.map { variant ->
                cellFor(variant, target, compilations).length
            }
            maxOf(target.length, cellWidths.maxOrNull() ?: 0)
        }

        // Header.
        val headerCells = listOf(pad("Variant", variantColWidth), pad("Status", statusColWidth)) +
            targets.map { pad(it, perTargetWidth.getValue(it)) }
        val separatorCells = listOf("-".repeat(variantColWidth), "-".repeat(statusColWidth)) +
            targets.map { "-".repeat(perTargetWidth.getValue(it)) }

        println()
        println("KMPF listVariantCompilations: ${variants.size} variant(s) × ${targets.size} target(s)")
        println()
        println("| " + headerCells.joinToString(" | ") + " |")
        println("|" + separatorCells.joinToString("|") { "-$it-" } + "|")
        for (variant in variants) {
            val status = if (variant == active) "ACTIVE" else "inactive"
            val row = listOf(pad(variant, variantColWidth), pad(status, statusColWidth)) +
                targets.map { target ->
                    pad(cellFor(variant, target, compilations), perTargetWidth.getValue(target))
                }
            println("| " + row.joinToString(" | ") + " |")
        }
        println()
    }

    private fun cellFor(variant: String, target: String, compilations: Map<String, String>): String {
        val key = "$variant::$target"
        return compilations[key].orEmpty().ifBlank { "—" }
    }

    private fun pad(text: String, width: Int): String = if (text.length >= width) text else text + " ".repeat(width - text.length)
}
