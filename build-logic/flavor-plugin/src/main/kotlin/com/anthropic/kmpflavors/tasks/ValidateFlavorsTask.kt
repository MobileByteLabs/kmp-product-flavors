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
import org.gradle.api.GradleException
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.SetProperty
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.TaskAction

/**
 * Task that validates the flavor configuration.
 *
 * Validation rules:
 * 1. All flavors must reference existing dimensions (if dimensions are defined)
 * 2. Each dimension should have at most one default flavor
 * 3. The active variant must be valid (exists in the variant matrix)
 * 4. No duplicate flavor names
 * 5. Flavor names must be valid Kotlin identifiers
 */
abstract class ValidateFlavorsTask : DefaultTask() {

    /**
     * Set of defined dimension names.
     */
    @get:Input
    abstract val dimensionNames: SetProperty<String>

    /**
     * Map of flavor name to its dimension (null if no dimension set).
     */
    @get:Input
    abstract val flavorDimensions: MapProperty<String, String?>

    /**
     * Map of flavor name to whether it's marked as default.
     */
    @get:Input
    abstract val flavorDefaults: MapProperty<String, Boolean>

    /**
     * All valid variant names.
     */
    @get:Input
    abstract val validVariantNames: SetProperty<String>

    /**
     * The active variant name (may be null if using defaults).
     */
    @get:Input
    @get:Optional
    abstract val activeVariantName: Property<String>

    /**
     * All flavor names for duplicate check.
     */
    @get:Input
    abstract val allFlavorNames: ListProperty<String>

    init {
        group = "kmp flavors"
        description = "Validates the flavor configuration"
    }

    @TaskAction
    fun validate() {
        val errors = mutableListOf<String>()
        val warnings = mutableListOf<String>()

        val dimensions = dimensionNames.get()
        val flavorDims = flavorDimensions.get()
        val flavorDefs = flavorDefaults.get()
        val variants = validVariantNames.get()
        val activeVariant = activeVariantName.orNull
        val flavors = allFlavorNames.get()

        logger.lifecycle("[KMP Flavors] Validating flavor configuration...")

        // Rule 1: Check for duplicate flavor names
        val duplicates = flavors.groupingBy { it }.eachCount().filter { it.value > 1 }
        if (duplicates.isNotEmpty()) {
            errors.add("Duplicate flavor names: ${duplicates.keys.joinToString(", ")}")
        }

        // Rule 2: Validate flavor names are valid Kotlin identifiers
        val invalidNames = flavors.filter { !isValidKotlinIdentifier(it) }
        if (invalidNames.isNotEmpty()) {
            errors.add("Invalid flavor names (must be valid Kotlin identifiers): ${invalidNames.joinToString(", ")}")
        }

        // Rule 3: All flavors must reference existing dimensions (if dimensions defined)
        if (dimensions.isNotEmpty()) {
            for ((flavor, dimension) in flavorDims) {
                when {
                    dimension == null -> {
                        errors.add("Flavor '$flavor' has no dimension. Use dimension.set(\"...\") to assign one.")
                    }
                    dimension !in dimensions -> {
                        errors.add("Flavor '$flavor' references unknown dimension '$dimension'. Available: ${dimensions.joinToString(", ")}")
                    }
                }
            }
        }

        // Rule 4: Check default counts per dimension
        if (dimensions.isNotEmpty()) {
            val defaultsByDimension = mutableMapOf<String, MutableList<String>>()
            for ((flavor, dimension) in flavorDims) {
                if (dimension != null && flavorDefs[flavor] == true) {
                    defaultsByDimension.getOrPut(dimension) { mutableListOf() }.add(flavor)
                }
            }
            for ((dimension, defaults) in defaultsByDimension) {
                if (defaults.size > 1) {
                    warnings.add("Dimension '$dimension' has multiple defaults: ${defaults.joinToString(", ")}. Only the first will be used.")
                }
            }

            // Check dimensions without any flavors
            for (dimension in dimensions) {
                val dimensionFlavors = flavorDims.entries.filter { it.value == dimension }
                if (dimensionFlavors.isEmpty()) {
                    errors.add("Dimension '$dimension' has no flavors assigned to it.")
                }
            }
        }

        // Rule 5: Validate active variant exists
        if (activeVariant != null && variants.isNotEmpty() && activeVariant !in variants) {
            errors.add(
                "Active variant '$activeVariant' is not valid. Available variants: ${variants.joinToString(", ")}"
            )
        }

        // Report results
        for (warning in warnings) {
            logger.warn("[KMP Flavors] WARNING: $warning")
        }

        if (errors.isNotEmpty()) {
            val errorMessage = buildString {
                appendLine("[KMP Flavors] Validation failed with ${errors.size} error(s):")
                errors.forEachIndexed { index, error ->
                    appendLine("  ${index + 1}. $error")
                }
            }
            throw GradleException(errorMessage)
        }

        logger.lifecycle("[KMP Flavors] Validation passed!")
        if (variants.isNotEmpty()) {
            logger.lifecycle("[KMP Flavors] Valid variants: ${variants.joinToString(", ")}")
        }
        if (activeVariant != null) {
            logger.lifecycle("[KMP Flavors] Active variant: $activeVariant")
        }
    }

    /**
     * Checks if a string is a valid Kotlin identifier.
     */
    private fun isValidKotlinIdentifier(name: String): Boolean {
        if (name.isEmpty()) return false
        if (!name[0].isLetter() && name[0] != '_') return false
        return name.all { it.isLetterOrDigit() || it == '_' }
    }
}
