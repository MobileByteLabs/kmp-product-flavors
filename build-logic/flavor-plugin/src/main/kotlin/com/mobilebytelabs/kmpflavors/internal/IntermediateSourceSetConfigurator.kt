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

import com.mobilebytelabs.kmpflavors.BuildTypeConfig
import com.mobilebytelabs.kmpflavors.FlavorVariant
import org.gradle.api.logging.Logger
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.plugin.KotlinSourceSet
import org.jetbrains.kotlin.gradle.plugin.KotlinTarget

/**
 * v2.2 Phase 1A — cross-variant intermediate `common{BuildType}` source sets.
 *
 * Closes RFC §10 deferral. Today every variant is a **leaf** in the
 * source-set DAG — `freeStaging` and `paidStaging` BOTH depend on their own
 * per-flavor common source sets, but the staging-specific code that should
 * be shared between them has no home except `commonMain` (too broad — it
 * also applies to `freeProd` + `paidProd`).
 *
 * This configurator introduces `common{BuildType}` intermediate source sets
 * (e.g. `commonStaging`, `commonProd`) that `dependsOn(commonMain)` and are
 * pulled in by every variant on the matching build type. Cross-variant
 * isolation (Q12) holds: only same-build-type variants see the intermediate.
 *
 * Source-set DAG after Phase 1A (with `enableBuildTypes=true` and a 2×2 matrix):
 * ```
 *               commonMain
 *               /    |    \
 *      commonFree  commonStaging  commonPaid
 *                  /        \
 *  freeStaging───┘            └───paidStaging
 *     │                                │
 *  also dependsOn commonFree     also dependsOn commonPaid
 * ```
 *
 * No-op when:
 *   - `kmpFlavors.createIntermediateBuildTypeSourceSets = false` (default).
 *   - `enableBuildTypes = false` (build types disabled — nothing to namespace).
 *   - Matrix mode is off.
 *   - No build types are registered.
 *
 * Per-target intermediate source sets (e.g. `desktopStaging`) follow the
 * same pattern but on the platform axis, mirroring the v1.x SourceSetConfigurator
 * convention for per-flavor platform source sets.
 */
internal object IntermediateSourceSetConfigurator {

    /**
     * Wires `common{BuildType}` + per-target `{target}{BuildType}` intermediate
     * source sets and the `dependsOn` edges from variant compilations.
     *
     * Returns a map of variant name → list of intermediate source sets the
     * variant depends on, so the caller can populate `KmpFlavorVariant.intermediateSourceSets`.
     */
    fun configure(
        project: org.gradle.api.Project,
        kotlin: KotlinMultiplatformExtension,
        buildTypes: List<BuildTypeConfig>,
        allVariants: List<FlavorVariant>,
        nonAndroidTargets: List<KotlinTarget>,
        enabled: Boolean,
        logger: Logger,
    ): Map<String, List<KotlinSourceSet>> {
        if (!enabled || buildTypes.isEmpty() || allVariants.isEmpty()) {
            return emptyMap()
        }

        val commonMain = kotlin.sourceSets.findByName("commonMain") ?: run {
            logger.warn(
                "[KMP Flavors] Phase 1A — commonMain source set missing; skipping intermediate " +
                    "build-type source-set creation.",
            )
            return emptyMap()
        }

        // 1. Create one common{BuildType} per registered build type, dependsOn commonMain.
        val commonBuildTypeSourceSets: Map<String, KotlinSourceSet> = buildTypes.mapNotNull { buildType ->
            val ssName = "common${buildType.name.replaceFirstChar { it.uppercase() }}"
            // NOTE: deliberately NOT gated on on-disk content. Registering a build type is
            // a CONTRACT that `common{BuildType}` exists — consumers configure it directly
            // (`sourceSets.commonStaging.dependencies { … }`) before any file is placed
            // there, and IntermediateBuildTypeSourceSetTest pins that. Gating it trades 3
            // warnings for a broken public contract.
            val ss = kotlin.sourceSets.maybeCreate(ssName)
            ss.kotlin.srcDir("src/$ssName/kotlin")
            ss.resources.srcDir("src/$ssName/resources")
            if (commonMain !in ss.dependsOn) {
                ss.dependsOn(commonMain)
            }
            logger.info("[KMP Flavors] Phase 1A — created $ssName -> dependsOn(commonMain)")
            buildType.name to ss
        }.toMap()

        // 2. Create per-target {target}{BuildType} source sets too. These let consumers drop
        //    target+buildType-specific code in `src/desktopStaging/kotlin/...`.
        val perTargetBuildTypeSourceSets: MutableMap<Pair<String, String>, KotlinSourceSet> = mutableMapOf()
        for (target in nonAndroidTargets) {
            for (buildType in buildTypes) {
                val targetBtName = "${target.name}${buildType.name.replaceFirstChar { it.uppercase() }}"
                val ss = kotlin.sourceSets.maybeCreate(targetBtName)
                ss.kotlin.srcDir("src/$targetBtName/kotlin")
                ss.resources.srcDir("src/$targetBtName/resources")
                val parentCommon = commonBuildTypeSourceSets[buildType.name]
                if (parentCommon != null && parentCommon !in ss.dependsOn) {
                    ss.dependsOn(parentCommon)
                }
                perTargetBuildTypeSourceSets[target.name to buildType.name] = ss
                logger.info(
                    "[KMP Flavors] Phase 1A — created $targetBtName -> dependsOn(common${buildType.name.replaceFirstChar { it.uppercase() }})",
                )
            }
        }

        // 3. Wire variant compilations' defaultSourceSet to dependsOn the matching
        //    common{BuildType} source set. Skip variants that have no buildType
        //    (legacy flavor-only variants — shouldn't happen when enableBuildTypes=true,
        //    but defensive).
        val variantIntermediateSourceSets: MutableMap<String, MutableList<KotlinSourceSet>> = mutableMapOf()
        for (variant in allVariants) {
            val buildTypeName = variant.buildType?.name ?: continue
            val commonBt = commonBuildTypeSourceSets[buildTypeName] ?: continue
            val intermediates = mutableListOf<KotlinSourceSet>()
            intermediates += commonBt

            for (target in nonAndroidTargets) {
                @Suppress("UNCHECKED_CAST")
                val container = target.compilations
                val compilation = container.findByName(variant.name) ?: continue
                // v2.9 — share the build-type DIRECTORIES, never the NODES.
                //
                // `commonDebug` / `watchosArm64Debug` are shared by EVERY variant carrying
                // that build type, so a `dependsOn` edge put one node into several Kotlin
                // Source Set Trees at once and KGP rejected it:
                //   w: ⚠️ Invalid Source Set Dependency Across Trees
                //      …'watchosArm64Debug'… 'enterpriseDebug' Tree / 'paidDebug' Tree
                // This mirrors the ISSUE #99 fix in CompilationRegistrar, which already
                // folds `<target>Main` in by srcDir for exactly the same reason.
                val commonBtName = commonBt.name
                compilation.defaultSourceSet.kotlin.srcDir("src/$commonBtName/kotlin")
                compilation.defaultSourceSet.resources.srcDir("src/$commonBtName/resources")
                inheritSourceSetDependencies(project, compilation.defaultSourceSet.name, commonBtName)

                // Also fold in the per-target intermediate if it exists.
                val perTarget = perTargetBuildTypeSourceSets[target.name to buildTypeName]
                if (perTarget != null) {
                    compilation.defaultSourceSet.kotlin.srcDir("src/${perTarget.name}/kotlin")
                    compilation.defaultSourceSet.resources.srcDir("src/${perTarget.name}/resources")
                    inheritSourceSetDependencies(project, compilation.defaultSourceSet.name, perTarget.name)
                    intermediates += perTarget
                }
            }
            variantIntermediateSourceSets[variant.name] = intermediates
            logger.info(
                "[KMP Flavors] Phase 1A — variant '${variant.name}' (buildType=$buildTypeName) " +
                    "depends on ${intermediates.size} intermediate source set(s).",
            )
        }

        val variantCount = variantIntermediateSourceSets.size
        logger.lifecycle(
            "[KMP Flavors] Phase 1A — wired cross-variant intermediate source sets: " +
                "${commonBuildTypeSourceSets.size} common{BuildType} + ${perTargetBuildTypeSourceSets.size} " +
                "{target}{BuildType} source sets across $variantCount variant(s).",
        )
        return variantIntermediateSourceSets
    }

    /**
     * Inherit the dependencies declared on a shared build-type source set without creating a
     * `dependsOn` edge (which would place one node in several Source Set Trees). Directories
     * carry sources; `extendsFrom` carries dependencies.
     */
    private fun inheritSourceSetDependencies(project: org.gradle.api.Project, targetSourceSetName: String, sharedSourceSetName: String) {
        listOf("Implementation", "Api", "CompileOnly", "RuntimeOnly").forEach { scope ->
            val from = project.configurations.findByName("$sharedSourceSetName$scope") ?: return@forEach
            val into = project.configurations.findByName("$targetSourceSetName$scope") ?: return@forEach
            if (from !== into && !into.extendsFrom.contains(from)) {
                into.extendsFrom(from)
            }
        }
    }

    /** True when `src/{name}/{kotlin,resources}` actually contains files. */
    private fun hasOnDiskContent(project: org.gradle.api.Project, name: String): Boolean {
        val kotlinDir = project.file("src/$name/kotlin")
        val resourcesDir = project.file("src/$name/resources")
        return (kotlinDir.isDirectory && kotlinDir.walk().any { it.isFile }) ||
            (resourcesDir.isDirectory && resourcesDir.walk().any { it.isFile })
    }
}
