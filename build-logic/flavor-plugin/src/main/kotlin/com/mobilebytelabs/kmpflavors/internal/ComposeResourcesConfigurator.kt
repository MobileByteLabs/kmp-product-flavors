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

import com.mobilebytelabs.kmpflavors.FlavorConfig
import org.gradle.api.Project
import org.gradle.api.logging.Logger
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

/**
 * v2.1 Phase 3A — per-variant Compose Multiplatform resources.
 *
 * Compose Multiplatform v1.7+ auto-discovers a `composeResources/` directory
 * under any Kotlin source set via its plugin's `kotlin.sourceSets.configureEach`
 * hook. Matrix mode's per-flavor source sets (`commonFree`, `commonPaid`,
 * etc.) ARE Kotlin source sets, so CMP picks them up automatically — no
 * explicit srcDir wiring on the plugin side.
 *
 * What this configurator does:
 *   1. Detects whether the consumer applied `org.jetbrains.compose`.
 *   2. Logs a lifecycle line announcing per-variant resources are available,
 *      with the convention path so consumers know where to drop files.
 *   3. Serves as the forward extension point for v2.1+ enhancements
 *      (CMP-version check, custom resource directory registration, etc.).
 *
 * Per-variant resource merge behaviour (handled by CMP at compile time):
 *   - Active variant's `compileKotlin{Target}` sees `commonMain/composeResources/`
 *     + `commonActiveFlavor/composeResources/` via the dependsOn chain.
 *   - Inactive variant's `compile{Variant}Kotlin{Target}` sees
 *     `commonMain/composeResources/` + `commonInactiveFlavor/composeResources/`
 *     via the variant compilation's defaultSourceSet → commonFlavor → commonMain.
 *   - Leaf source set wins on duplicate keys: `commonFree/strings.xml#app_name`
 *     overrides `commonMain/strings.xml#app_name` for the free-variant compile.
 *
 * Cross-variant isolation (Q12) holds: the paid-variant compilation never sees
 * the free-variant's resources because their source-set trees are disjoint past
 * the shared commonMain root.
 *
 * No-op when:
 *   - Matrix mode is off (v1.x behaviour preserved).
 *   - No flavors registered.
 *   - CMP plugin not applied (the consumer build is non-Compose).
 */
internal object ComposeResourcesConfigurator {

    /**
     * Plugin id used by Compose Multiplatform's Gradle plugin.
     */
    private const val COMPOSE_PLUGIN_ID: String = "org.jetbrains.compose"

    /**
     * Wires per-variant Compose resources for [project].
     *
     * The function returns immediately if matrix mode is off, no flavors are
     * registered, or the project doesn't apply the Compose Multiplatform plugin.
     */
    fun configure(
        project: Project,
        @Suppress("UNUSED_PARAMETER") kotlin: KotlinMultiplatformExtension,
        allFlavors: List<FlavorConfig>,
        matrixModeEnabled: Boolean,
        logger: Logger,
    ) {
        if (!matrixModeEnabled || allFlavors.isEmpty()) return

        project.pluginManager.withPlugin(COMPOSE_PLUGIN_ID) {
            val flavorPaths = allFlavors.joinToString(", ") { flavor ->
                val ssName = "common${flavor.name.replaceFirstChar { it.uppercase() }}"
                "src/$ssName/composeResources/"
            }
            logger.lifecycle(
                "[KMP Flavors] Compose resources: per-variant resource directories " +
                    "are auto-discovered by CMP for ${allFlavors.size} flavor(s). " +
                    "Drop files under: $flavorPaths to override commonMain resources " +
                    "per variant. Leaf source set wins on duplicate keys.",
            )
        }
    }
}
