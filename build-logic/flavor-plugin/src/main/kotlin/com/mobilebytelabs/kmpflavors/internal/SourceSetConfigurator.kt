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
import org.gradle.api.Project
import org.gradle.api.logging.Logger
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.plugin.KotlinSourceSet

/**
 * Configures flavor-specific source sets and their dependencies.
 *
 * This class is responsible for:
 * 1. Creating source set entries for ALL flavors (so IDE recognizes all directories)
 * 2. Wiring dependsOn relationships for only the ACTIVE variant's flavors
 */
class SourceSetConfigurator(private val logger: Logger) {

    /**
     * Configures all flavor source sets.
     *
     * @param kotlin The KMP extension
     * @param activeVariant The currently active variant
     * @param allFlavors All configured flavors
     * @param platforms Detected platforms
     * @param platformSourceSets Map of platforms to their source sets
     * @param createIntermediates Whether to create intermediate flavor source sets
     */
    @Suppress("UNUSED_PARAMETER") // platformSourceSets kept for API compatibility
    fun configure(
        project: Project,
        kotlin: KotlinMultiplatformExtension,
        activeVariant: FlavorVariant,
        allFlavors: Collection<FlavorConfig>,
        platforms: List<PlatformGroup>,
        platformSourceSets: Map<PlatformGroup, KotlinSourceSet>,
        createIntermediates: Boolean,
        matrixModeEnabled: Boolean = false,
        createInactiveFlavorSourceSets: Boolean = false,
    ) {
        val sourceSets = kotlin.sourceSets
        val commonMain = sourceSets.getByName("commonMain")
        val commonTest = sourceSets.findByName("commonTest")
        val activeFlavorNames = activeVariant.flavorNames.toSet()

        logger.lifecycle("[KMP Flavors] Configuring source sets for ${allFlavors.size} flavors")
        logger.lifecycle("[KMP Flavors] Active variant: ${activeVariant.name} (flavors: ${activeFlavorNames.joinToString()})")

        // Get leaf platforms (non-intermediate)
        val leafPlatforms = platforms.filter { !it.isIntermediate }
        val intermediatePlatforms = platforms.filter { it.isIntermediate }

        for (flavor in allFlavors) {
            val flavorName = flavor.name
            val isActiveFlavor = flavorName in activeFlavorNames
            val capitalizedFlavor = flavorName.replaceFirstChar { it.uppercaseChar() }

            // 1. Create common<Flavor> source set — lazily.
            //    Active flavor: always created (wired below).
            //    Inactive flavor: created only when src/common<Flavor>/{kotlin,resources}
            //    contains files; otherwise an empty source set would be reported by KMP
            //    as "Unused Kotlin Source Sets". Same lazy rule applies to all flavor
            //    source sets created below.
            val commonFlavorName = "common$capitalizedFlavor"
            val commonFlavor = maybeCreateLazy(
                project, sourceSets, commonFlavorName, isActiveFlavor,
                matrixModeEnabled = matrixModeEnabled,
                createInactiveFlavorSourceSets = createInactiveFlavorSourceSets,
            )

            if (isActiveFlavor && commonFlavor != null) {
                commonFlavor.dependsOn(commonMain)
                logger.info("[KMP Flavors] Wired $commonFlavorName -> commonMain")
            }

            // 2. Create intermediate<Flavor> source sets (if enabled)
            // Note: Intermediate flavor source sets only depend on commonFlavor
            // (NOT on intermediateMain as it may be a compilation default source set)
            if (createIntermediates) {
                for (intermediate in intermediatePlatforms) {
                    val intermediateFlavorName = "${intermediate.prefix}$capitalizedFlavor"
                    val intermediateFlavor = maybeCreateLazy(
                        project, sourceSets, intermediateFlavorName, isActiveFlavor,
                        matrixModeEnabled = matrixModeEnabled,
                        createInactiveFlavorSourceSets = createInactiveFlavorSourceSets,
                    )

                    if (isActiveFlavor && intermediateFlavor != null && commonFlavor != null) {
                        // Wire only to commonFlavor (not intermediateMain to avoid compilation default dependency)
                        intermediateFlavor.dependsOn(commonFlavor)
                        logger.info("[KMP Flavors] Wired $intermediateFlavorName -> $commonFlavorName")
                    }
                }
            }

            // 3. Create <platform><Flavor> source sets for leaf platforms
            // Note: Platform flavor source sets should NOT depend on platformMain
            // (e.g., desktopFree cannot depend on desktopMain as it's a compilation default source set)
            // Instead, they depend on commonFlavor or intermediate flavor source sets only
            for (platform in leafPlatforms) {
                val platformFlavorName = "${platform.prefix}$capitalizedFlavor"
                val platformFlavor = maybeCreateLazy(
                    project, sourceSets, platformFlavorName, isActiveFlavor,
                    matrixModeEnabled = matrixModeEnabled,
                    createInactiveFlavorSourceSets = createInactiveFlavorSourceSets,
                ) ?: continue

                if (isActiveFlavor) {
                    // Wire to intermediate flavor or common flavor (NOT to platformMain)
                    if (createIntermediates && platform.parent != null) {
                        val parentIntermediate = intermediatePlatforms.find { it.prefix == platform.parent }
                        if (parentIntermediate != null) {
                            val intermediateFlavorName = "${parentIntermediate.prefix}$capitalizedFlavor"
                            val intermediateFlavor = sourceSets.findByName(intermediateFlavorName)
                            if (intermediateFlavor != null) {
                                platformFlavor.dependsOn(intermediateFlavor)
                                logger.info("[KMP Flavors] Wired $platformFlavorName -> $intermediateFlavorName")
                            }
                        }
                    } else if (commonFlavor != null) {
                        // No intermediate parent, wire directly to commonFlavor
                        platformFlavor.dependsOn(commonFlavor)
                        logger.info("[KMP Flavors] Wired $platformFlavorName -> $commonFlavorName")
                    }

                    // F8 — Wire the platform's main source set to dependsOn the
                    // flavor source set so `actual` declarations there are reachable
                    // from `compileKotlin<Target>`.
                    //
                    // SKIP for Android target: AGP already handles per-variant flavor
                    // source-set inclusion natively (it adds `androidDemo`/`androidProd`
                    // to the appropriate variants automatically). If we add
                    // `androidMain.dependsOn(androidDemo)` here, AGP sees `androidDemo`
                    // classes in EVERY android variant — causing duplicate-class
                    // errors when `androidProd` is also compiled (prodRelease etc.).
                    if (platform.prefix == "android") {
                        logger.info("[KMP Flavors] Skipping dependsOn wiring for android target — AGP handles flavor source sets natively per variant.")
                    } else {
                        val platformMain = sourceSets.findByName(platform.mainSourceSet)
                        if (platformMain != null) {
                            wireIfMissing(platformMain, platformFlavor)
                            if (commonFlavor != null) wireIfMissing(platformMain, commonFlavor)
                            logger.info("[KMP Flavors] Wired ${platform.mainSourceSet} -> $platformFlavorName + $commonFlavorName (active flavor)")
                        }
                    }
                }
            }

            // Test source sets — mirror the *Main* structure under *Test*.
            // Only created when commonTest exists (i.e. the KMP module declares any test
            // compilation). Plugin-side wiring rule: <flavor>Test depends on commonTest
            // ONLY when active, so non-active flavor test sources never reach the test
            // classpath of another variant.
            if (commonTest != null) {
                // Test source sets are explicit opt-in: created only when devs put real
                // test code under src/<name>/kotlin/. Active-flavor status alone is not
                // enough — an empty test source set still triggers "Unused" warnings.
                val commonFlavorTestName = "common${capitalizedFlavor}Test"
                val commonFlavorTest = maybeCreateLazy(
                    project, sourceSets, commonFlavorTestName, isActive = false,
                    matrixModeEnabled = matrixModeEnabled,
                    createInactiveFlavorSourceSets = createInactiveFlavorSourceSets,
                )
                if (isActiveFlavor && commonFlavorTest != null) {
                    commonFlavorTest.dependsOn(commonTest)
                    logger.info("[KMP Flavors] Wired $commonFlavorTestName -> commonTest")
                }

                for (platform in leafPlatforms) {
                    val platformFlavorTestName = "${platform.prefix}${capitalizedFlavor}Test"
                    val platformFlavorTest = maybeCreateLazy(
                        project, sourceSets, platformFlavorTestName, isActive = false,
                        matrixModeEnabled = matrixModeEnabled,
                        createInactiveFlavorSourceSets = createInactiveFlavorSourceSets,
                    )
                    if (isActiveFlavor && platformFlavorTest != null && commonFlavorTest != null) {
                        platformFlavorTest.dependsOn(commonFlavorTest)
                        logger.info("[KMP Flavors] Wired $platformFlavorTestName -> $commonFlavorTestName")
                    }
                }
            }
        }
    }

    /**
     * Creates a flavor source set ONLY when it will actually be used.
     *
     * Returns the source set if any of:
     *   - it's the active flavor (will be wired into compilation)
     *   - src/<name>/kotlin or src/<name>/resources exists on disk with files
     *   - it was already created earlier (e.g. by the consumer build script)
     *
     * Returns `null` otherwise — KMP previously reported empty inactive-flavor
     * source sets as "Unused Kotlin Source Sets". This lazy creation eliminates
     * the warning while still letting devs opt-in by simply dropping a file
     * into `src/<name>/kotlin/`.
     */
    private fun maybeCreateLazy(
        project: Project,
        sourceSets: org.gradle.api.NamedDomainObjectContainer<KotlinSourceSet>,
        name: String,
        isActive: Boolean,
        matrixModeEnabled: Boolean = false,
        createInactiveFlavorSourceSets: Boolean = false,
    ): KotlinSourceSet? {
        val existing = sourceSets.findByName(name)
        if (existing != null) {
            // Already created (consumer build script or earlier plugin pass) — re-register
            // src dirs idempotently and return.
            return sourceSets.maybeCreate(name).apply {
                kotlin.srcDir("src/$name/kotlin")
                resources.srcDir("src/$name/resources")
            }
        }
        if (isActive) {
            return sourceSets.maybeCreate(name).apply {
                kotlin.srcDir("src/$name/kotlin")
                resources.srcDir("src/$name/resources")
            }
        }
        // Inactive flavor below.
        if (!hasOnDiskContent(project, name)) {
            // No content, no need to create — KGP never sees it.
            return null
        }
        // Inactive + has on-disk content. v2.6 Tier E.1 decision tree:
        // 1) Matrix mode ON → create (per-variant compilations will use it; KGP won't warn).
        // 2) Matrix mode OFF + opt-in flag → create (consumer accepts KGP "Unused" warning).
        // 3) Matrix mode OFF + default → skip + structured WARN (avoids the KGP warning by
        //    structural means — the source set is never registered).
        if (matrixModeEnabled || createInactiveFlavorSourceSets) {
            return sourceSets.maybeCreate(name).apply {
                kotlin.srcDir("src/$name/kotlin")
                resources.srcDir("src/$name/resources")
            }
        }
        logger.warn(
            "[KMP Flavors] Skipping creation of inactive source set '$name' — " +
                "it has on-disk content (src/$name/kotlin or .../resources) but '$name' " +
                "is not the active flavor and matrix mode is off, so this code is " +
                "currently DEAD. Options: " +
                "(a) `kmpFlavors { createInactiveFlavorSourceSets.set(true) }` to " +
                "preserve the source set (KGP will emit 'Unused Kotlin Source Sets' " +
                "for it); " +
                "(b) enable matrix mode (`kmpFlavors { buildMatrix.set(true) }`) so " +
                "every flavor compiles; " +
                "(c) switch the active flavor (`-PkmpFlavor=<variant>`) to compile this code.",
        )
        return null
    }

    private fun hasOnDiskContent(project: Project, name: String): Boolean {
        val kotlinDir = project.file("src/$name/kotlin")
        val resourcesDir = project.file("src/$name/resources")
        return (kotlinDir.isDirectory && kotlinDir.walk().any { it.isFile }) ||
            (resourcesDir.isDirectory && resourcesDir.walk().any { it.isFile })
    }

    /**
     * Add a dependsOn edge from [from] to [target] only if it isn't already present
     * directly or transitively. Prevents "Redundant dependsOn" warnings.
     */
    private fun wireIfMissing(from: KotlinSourceSet, target: KotlinSourceSet) {
        if (from == target || target in from.dependsOn) return
        val seen = mutableSetOf<KotlinSourceSet>()
        val queue = ArrayDeque(from.dependsOn)
        while (queue.isNotEmpty()) {
            val next = queue.removeFirst()
            if (!seen.add(next)) continue
            if (next == target) return
            queue.addAll(next.dependsOn)
        }
        from.dependsOn(target)
    }
}
