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
import com.mobilebytelabs.kmpflavors.KmpFlavorExtension
import com.mobilebytelabs.kmpflavors.tasks.GenerateKoinModulesTask
import org.gradle.api.Project
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

/**
 * v2.6 Phase 3 — registers one [GenerateKoinModulesTask] per inactive variant
 * when the consumer has declared `kmpFlavors { di { koin { variantModule(...) { ... } } } }`.
 *
 * Mirrors [GenerateBuildConfigTasksRegistrar]'s shape: skip silently when there
 * are no variant modules declared or no inactive variants in scope. Each task's
 * output directory is wired into the matching per-variant compilation's default
 * source set on every non-Android, non-metadata target — same loop as
 * BuildKonfig codegen.
 *
 * The plugin does NOT inject Koin onto the classpath; the generated source
 * compiles only when the consumer adds `io.insert-koin:koin-core` themselves
 * (see docs/DI_INTEGRATION.md for the integration pattern).
 */
internal object PerVariantKoinDiConfigurator {

    fun configure(project: Project, extension: KmpFlavorExtension, activeVariant: FlavorVariant, inactiveVariants: List<FlavorVariant>, kotlin: KotlinMultiplatformExtension) {
        val di = extension.di.orNull ?: return
        val specs = di.koin.variantModules.values.toList()
        if (specs.isEmpty()) return
        val pkg = extension.buildConfigPackage.orNull ?: return

        val nonAndroidTargets = kotlin.targets.filter {
            it.name != "android" && it.name != "metadata"
        }

        registerForVariant(project, pkg, activeVariant, specs, nonAndroidTargets, isActive = true)
        for (variant in inactiveVariants) {
            registerForVariant(project, pkg, variant, specs, nonAndroidTargets, isActive = false)
        }
    }

    private fun registerForVariant(
        project: Project,
        pkg: String,
        variant: FlavorVariant,
        specs: List<com.mobilebytelabs.kmpflavors.KoinModuleSpec>,
        nonAndroidTargets: List<org.jetbrains.kotlin.gradle.plugin.KotlinTarget>,
        isActive: Boolean,
    ) {
        val variantNameCapitalized = variant.name.replaceFirstChar { it.uppercase() }
        val taskName = "generate${variantNameCapitalized}KoinModules"
        val outputDir = project.layout.buildDirectory
            .dir("generated/kmpFlavors/${variant.name}/di-koin/kotlin")

        val genTask = project.tasks.register(taskName, GenerateKoinModulesTask::class.java) {
            this.packageName.set(pkg)
            this.variantName.set(variant.name)
            this.moduleSpecs.set(specs)
            this.outputDirectory.set(outputDir)
        }

        for (target in nonAndroidTargets) {
            // Active variant wires into `main` (matches the BuildKonfig active-variant path
            // in wireGenerateBuildConfigToCompilation); inactive variants wire into their
            // per-variant compilation.
            val compilationName = if (isActive) "main" else variant.name
            val compilation = target.compilations.findByName(compilationName) ?: continue
            compilation.defaultSourceSet.kotlin.srcDir(genTask.map { it.outputDirectory })
        }
    }
}
