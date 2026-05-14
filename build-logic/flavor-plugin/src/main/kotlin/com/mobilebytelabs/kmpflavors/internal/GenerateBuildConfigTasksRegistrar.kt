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
import com.mobilebytelabs.kmpflavors.FlavorVariant
import com.mobilebytelabs.kmpflavors.KmpFlavorExtension
import com.mobilebytelabs.kmpflavors.tasks.GenerateBuildConfigTask
import org.gradle.api.Project
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

/**
 * Registers one [GenerateBuildConfigTask] per INACTIVE variant in matrix
 * mode (RFC §3 Q3-A). The active variant continues to be handled by
 * v1.x's single `generateFlavorBuildConfig` task that lives in
 * `KmpFlavorPlugin.registerTasks()`.
 *
 * Each per-variant task:
 *   - Name: `generate{Variant}BuildConfig` (e.g. `generatePaidBuildConfig`).
 *   - Output: `build/generated/kmpFlavors/{variantName}/kotlin/` so the
 *     directory layout naturally separates per-variant generated code.
 *   - Inputs: the variant's own flavor names, build-type, and merged
 *     `buildConfigField` map — so each variant's `BuildKonfig.kt` has
 *     the correct `IS_<FLAVOR>` / `IS_<BUILDTYPE>` constants.
 *
 * After registering, the generator's output directory is wired into
 * the matching variant compilation's `defaultSourceSet.kotlin.srcDir`
 * on every non-Android target so the generated Kotlin is part of
 * the variant's compilation inputs (lazy via Provider — config-cache
 * friendly).
 *
 * Skips silently when matrix mode is off, when there are no inactive
 * variants, when `generateBuildConfig` is false on the extension, or
 * when this module is not the codegen host (multi-module shouldGenerate
 * claim semantics carry over from v1.x).
 */
internal object GenerateBuildConfigTasksRegistrar {

    fun register(
        project: Project,
        extension: KmpFlavorExtension,
        inactiveVariants: List<FlavorVariant>,
        flavors: List<FlavorConfig>,
        kotlin: KotlinMultiplatformExtension,
        shouldGenerate: Boolean,
    ) {
        if (!shouldGenerate || inactiveVariants.isEmpty()) return
        if (!extension.generateBuildConfig.get()) return

        val packageNameProvider = extension.buildConfigPackage
        val classNameProvider = extension.buildConfigClassName
        val allFlavorNames = flavors.map { it.name }.toSet()
        val allBuildTypeNames = extension.buildTypes.map { it.name }.toSet()
        val nonAndroidTargets = kotlin.targets.filter {
            it.name != "android" && it.name != "metadata"
        }

        for (variant in inactiveVariants) {
            val variantNameCapitalized = variant.name.replaceFirstChar { it.uppercase() }
            val taskName = "generate${variantNameCapitalized}BuildConfig"
            val outputDir = project.layout.buildDirectory
                .dir("generated/kmpFlavors/${variant.name}/kotlin")

            val genTask = project.tasks.register(taskName, GenerateBuildConfigTask::class.java) {
                this.packageName.set(packageNameProvider)
                this.className.set(classNameProvider)
                this.variantName.set(variant.name)
                this.allFlavorNames.set(allFlavorNames)
                this.activeFlavorNames.set(variant.flavorNames.toSet())
                this.allBuildTypeNames.set(allBuildTypeNames)
                this.activeBuildTypeName.set(variant.buildType?.name ?: "")
                this.buildConfigFields.set(variant.mergedBuildConfigFields)
                this.outputDirectory.set(outputDir)
                this.group = "kmpFlavors variants"
                this.description =
                    "Generates the per-variant BuildConfig Kotlin object for '${variant.name}'."
            }

            // Wire the task's output directory into every non-Android target's
            // matching variant compilation. Use the Provider returned by the
            // TaskProvider so the wiring is lazy and config-cache friendly.
            for (target in nonAndroidTargets) {
                val variantCompilation = target.compilations.findByName(variant.name) ?: continue
                variantCompilation.defaultSourceSet.kotlin.srcDir(
                    genTask.map { it.outputDirectory },
                )
            }
        }
    }
}
