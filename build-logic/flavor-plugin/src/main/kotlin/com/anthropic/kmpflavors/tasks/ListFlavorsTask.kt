/*
 * Copyright 2026 Anthropic, Inc.
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

package com.anthropic.kmpflavors.tasks

import org.gradle.api.DefaultTask
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.TaskAction

/**
 * Task that prints a formatted table of all variants and marks the active one.
 *
 * Output example:
 * ```
 * ┌─────────────────────────────────────────────────────┐
 * │                   KMP Flavor Variants               │
 * ├───────────────┬───────────────┬─────────────────────┤
 * │ Variant       │ Flavors       │ Status              │
 * ├───────────────┼───────────────┼─────────────────────┤
 * │ freeDev       │ free, dev     │ ← ACTIVE            │
 * │ freeStaging   │ free, staging │                     │
 * │ freeProd      │ free, prod    │                     │
 * │ paidDev       │ paid, dev     │                     │
 * │ paidStaging   │ paid, staging │                     │
 * │ paidProd      │ paid, prod    │                     │
 * └───────────────┴───────────────┴─────────────────────┘
 * ```
 */
abstract class ListFlavorsTask : DefaultTask() {

    /**
     * Map of variant name to list of flavor names.
     */
    @get:Input
    abstract val variants: MapProperty<String, List<String>>

    /**
     * The active variant name.
     */
    @get:Input
    @get:Optional
    abstract val activeVariant: Property<String>

    /**
     * Map of dimension name to its priority.
     */
    @get:Input
    abstract val dimensions: MapProperty<String, Int>

    /**
     * List of detected platforms.
     */
    @get:Input
    abstract val platforms: ListProperty<String>

    init {
        group = "kmp flavors"
        description = "Lists all flavor variants"
    }

    @TaskAction
    fun list() {
        val variantMap = variants.get()
        val active = activeVariant.orNull
        val dimensionMap = dimensions.get()
        val platformList = platforms.get()

        println()
        printHeader()
        printDimensions(dimensionMap)
        printPlatforms(platformList)
        printVariants(variantMap, active)
        printFooter()
        println()
    }

    private fun printHeader() {
        println("┌${"─".repeat(60)}┐")
        println("│${centerText("KMP Flavor Variants", 60)}│")
        println("├${"─".repeat(60)}┤")
    }

    private fun printDimensions(dimensions: Map<String, Int>) {
        if (dimensions.isEmpty()) {
            println("│${padRight(" Dimensions: (none)", 60)}│")
        } else {
            val sorted = dimensions.entries.sortedBy { it.value }
            println("│${padRight(" Dimensions:", 60)}│")
            for ((name, priority) in sorted) {
                println("│${padRight("   • $name (priority: $priority)", 60)}│")
            }
        }
        println("├${"─".repeat(60)}┤")
    }

    private fun printPlatforms(platforms: List<String>) {
        if (platforms.isEmpty()) {
            println("│${padRight(" Platforms: (none detected)", 60)}│")
        } else {
            println("│${padRight(" Platforms: ${platforms.joinToString(", ")}", 60)}│")
        }
        println("├${"─".repeat(60)}┤")
    }

    private fun printVariants(variants: Map<String, List<String>>, active: String?) {
        if (variants.isEmpty()) {
            println("│${padRight(" No variants configured", 60)}│")
            return
        }

        // Calculate column widths
        val maxVariantLen = maxOf(variants.keys.maxOfOrNull { it.length } ?: 0, 10)
        val maxFlavorsLen = maxOf(variants.values.maxOfOrNull { it.joinToString(", ").length } ?: 0, 10)

        // Header row
        println("│ ${padRight("Variant", maxVariantLen)} │ ${padRight("Flavors", maxFlavorsLen)} │ Status     │")
        println("├${"─".repeat(maxVariantLen + 2)}┼${"─".repeat(maxFlavorsLen + 2)}┼────────────┤")

        // Variant rows
        for ((variant, flavors) in variants.entries.sortedBy { it.key }) {
            val isActive = variant == active
            val status = if (isActive) "← ACTIVE" else ""
            val flavorStr = flavors.joinToString(", ")
            println("│ ${padRight(variant, maxVariantLen)} │ ${padRight(flavorStr, maxFlavorsLen)} │ ${padRight(status, 10)} │")
        }
    }

    private fun printFooter() {
        println("└${"─".repeat(60)}┘")
    }

    private fun padRight(text: String, length: Int): String {
        return if (text.length >= length) {
            text.take(length)
        } else {
            text + " ".repeat(length - text.length)
        }
    }

    private fun centerText(text: String, width: Int): String {
        val padding = (width - text.length) / 2
        return " ".repeat(padding) + text + " ".repeat(width - padding - text.length)
    }
}
