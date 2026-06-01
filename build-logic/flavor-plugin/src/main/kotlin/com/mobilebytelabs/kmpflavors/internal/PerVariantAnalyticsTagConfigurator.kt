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
import com.mobilebytelabs.kmpflavors.tasks.GenerateAnalyticsTagsTask
import org.gradle.api.Project
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

/**
 * v2.6 Phase 3 — registers one [GenerateAnalyticsTagsTask] per inactive variant
 * when `kmpFlavors { analytics { enabled.set(true) } }`.
 *
 * Mirrors [PerVariantKoinDiConfigurator]. Each task receives a frozen
 * `Map<String, String>` of tag values pre-resolved at configuration time by
 * invoking the consumer-supplied `(FlavorVariant) -> String` resolvers — no
 * closure crosses the config-cache boundary.
 *
 * Output goes to `build/generated/kmpFlavors/{variant}/analytics/kotlin/` and
 * is wired into the matching per-variant compilation's default source set on
 * every non-Android target.
 */
internal object PerVariantAnalyticsTagConfigurator {

    fun configure(project: Project, extension: KmpFlavorExtension, activeVariant: FlavorVariant, inactiveVariants: List<FlavorVariant>, kotlin: KotlinMultiplatformExtension) {
        val analytics = extension.analytics
        if (!analytics.enabled.getOrElse(false)) return
        val pkg = extension.buildConfigPackage.orNull ?: return

        val nonAndroidTargets = kotlin.targets.filter {
            it.name != "android" && it.name != "metadata"
        }

        registerForVariant(project, extension, pkg, activeVariant, nonAndroidTargets, isActive = true)
        for (variant in inactiveVariants) {
            registerForVariant(project, extension, pkg, variant, nonAndroidTargets, isActive = false)
        }
    }

    private fun registerForVariant(
        project: Project,
        extension: KmpFlavorExtension,
        pkg: String,
        variant: FlavorVariant,
        nonAndroidTargets: List<org.jetbrains.kotlin.gradle.plugin.KotlinTarget>,
        isActive: Boolean,
    ) {
        val variantNameCapitalized = variant.name.replaceFirstChar { it.uppercase() }
        val taskName = "generate${variantNameCapitalized}AnalyticsTags"
        val outputDir = project.layout.buildDirectory
            .dir("generated/kmpFlavors/${variant.name}/analytics/kotlin")

        // Resolve every customTag's (FlavorVariant) -> String at config time — no
        // closure crosses the configuration-cache boundary.
        val resolvedTags: Map<String, String> = extension.analytics.customTags
            .mapValues { (_, resolver) -> resolver(variant) }

        val genTask = project.tasks.register(taskName, GenerateAnalyticsTagsTask::class.java) {
            this.packageName.set(pkg)
            this.variantName.set(variant.name)
            this.buildTypeName.set(variant.buildType?.name.orEmpty())
            this.customTagValues.set(resolvedTags)
            this.outputDirectory.set(outputDir)
        }

        for (target in nonAndroidTargets) {
            // Active variant wires into `main` (matches BuildKonfig active-variant path);
            // inactive variants wire into their per-variant compilation.
            val compilationName = if (isActive) "main" else variant.name
            val compilation = target.compilations.findByName(compilationName) ?: continue
            compilation.defaultSourceSet.kotlin.srcDir(genTask.map { it.outputDirectory })
        }
    }
}
