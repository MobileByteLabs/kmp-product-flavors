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

package com.anthropic.kmpflavors.internal

import com.anthropic.kmpflavors.FlavorVariant
import org.gradle.api.Project
import org.gradle.api.logging.Logger

/**
 * Configures per-flavor dependencies for the active variant.
 */
class DependencyConfigurator(
    private val logger: Logger,
) {

    /**
     * Adds all dependencies from the active variant's flavors.
     *
     * @param project The Gradle project
     * @param activeVariant The currently active variant
     */
    fun configure(
        project: Project,
        activeVariant: FlavorVariant,
    ) {
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
}
