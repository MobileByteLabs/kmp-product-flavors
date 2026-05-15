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
import com.mobilebytelabs.kmpflavors.VariantPromotion
import org.gradle.api.Project
import org.gradle.api.logging.Logger
import java.io.File

/**
 * v2.2 Phase 4A — variant promotion DSL.
 *
 * Today, a variant's source set graduates between buildTypes manually — a
 * consumer iterating `freeDev` → `freeStaging` → `freeProd` copies files,
 * refactors imports, and runs migration scripts themselves. The promotion
 * DSL automates that:
 *
 * ```kotlin
 * kmpFlavors {
 *     flavors.promote(from = "freeDev", to = "freeStaging") {
 *         applyTransform("renamePackage", "com.example.dev" to "com.example.staging")
 *         copyResources(true)
 *         copyTests(true)
 *     }
 * }
 * ```
 *
 * For each declared promotion, this configurator registers a `promote{From}To{To}`
 * task that copies / transforms files from the source variant's source set into
 * the target variant's source set. Supports `--dry-run` for preview.
 *
 * Out of scope for v2.2: actual file-content transformations beyond simple
 * package renames. The transformation registry is extensible (`applyTransform`
 * is a string + pair API) — v2.3+ can add more transforms.
 *
 * No-op when no promotions are declared.
 */
internal object VariantPromotionConfigurator {

    fun configure(project: Project, flavors: List<FlavorConfig>, promotions: List<VariantPromotion>, logger: Logger) {
        if (promotions.isEmpty()) return

        var registered = 0
        for (promotion in promotions) {
            val fromFlavor = flavors.firstOrNull { it.name == promotion.from }
            val toFlavor = flavors.firstOrNull { it.name == promotion.to }

            if (fromFlavor == null || toFlavor == null) {
                logger.warn(
                    "[KMP Flavors] Phase 4A — KMPF-V12: variant promotion declared with " +
                        "${if (fromFlavor == null) "from='${promotion.from}'" else "to='${promotion.to}'"} " +
                        "but no such flavor is registered. Promotion '${promotion.from}→${promotion.to}' skipped.",
                )
                continue
            }

            val fromCap = promotion.from.replaceFirstChar { it.uppercase() }
            val toCap = promotion.to.replaceFirstChar { it.uppercase() }
            val taskName = "promote${fromCap}To$toCap"

            project.tasks.register(taskName) {
                group = "kmp flavors"
                description = "Copies / refactors files from variant '${promotion.from}' to " +
                    "variant '${promotion.to}' source set. Use --dry-run to preview without writing."

                doLast {
                    val dryRun = project.findProperty("dry-run")?.toString()?.equals("true", ignoreCase = true) == true
                    val sourceDir = File(project.projectDir, "src/common$fromCap")
                    val targetDir = File(project.projectDir, "src/common$toCap")

                    if (!sourceDir.exists()) {
                        logger.warn(
                            "[KMP Flavors] Phase 4A — source dir 'src/common$fromCap' doesn't exist; " +
                                "nothing to promote.",
                        )
                        return@doLast
                    }
                    if (dryRun) {
                        logger.lifecycle("[KMP Flavors] Phase 4A (dry-run) — would promote files:")
                    } else {
                        logger.lifecycle(
                            "[KMP Flavors] Phase 4A — promoting files from 'src/common$fromCap' " +
                                "to 'src/common$toCap'...",
                        )
                    }

                    var copied = 0
                    sourceDir.walkTopDown().filter { it.isFile }.forEach { srcFile ->
                        val relPath = srcFile.relativeTo(sourceDir)
                        val targetFile = File(targetDir, relPath.path)
                        if (dryRun) {
                            logger.lifecycle("  ${srcFile.relativeTo(project.projectDir)} → ${targetFile.relativeTo(project.projectDir)}")
                        } else {
                            targetFile.parentFile.mkdirs()
                            var content = srcFile.readText()
                            // Apply registered transforms.
                            for (transform in promotion.transforms) {
                                if (transform.kind == "renamePackage") {
                                    content = content.replace(transform.from, transform.to)
                                }
                            }
                            targetFile.writeText(content)
                            copied += 1
                        }
                    }

                    if (!dryRun) {
                        logger.lifecycle(
                            "[KMP Flavors] Phase 4A — promoted $copied file(s) from common$fromCap to common$toCap.",
                        )
                    }
                }
            }
            registered += 1
        }

        if (registered > 0) {
            logger.lifecycle(
                "[KMP Flavors] Phase 4A — registered $registered variant promotion task(s). " +
                    "Resolve via `./gradlew :module:promote{From}To{To}` (add `-Pdry-run=true` for preview).",
            )
        }
    }
}
