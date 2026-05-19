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

import org.gradle.api.NamedDomainObjectContainer
import org.gradle.api.logging.Logger
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilation
import org.jetbrains.kotlin.gradle.plugin.KotlinSourceSet
import org.jetbrains.kotlin.gradle.plugin.KotlinTarget

/**
 * Registers one TEST [KotlinCompilation] per variant on a single [KotlinTarget].
 *
 * v2.1 Phase 2 — RFC §3 Q10. v2.0 ships `compile{Variant}Kotlin{Target}` per
 * inactive variant via [CompilationRegistrar]; this registrar mirrors that
 * shape for test code so per-variant tests are first-class.
 *
 * Task naming: KGP auto-derives the test task name from the compilation name —
 * `compilations.create("${variantName}Test")` produces
 * `compile{Variant}TestKotlin{Target}` (e.g., `compilePaidDevTestKotlinDesktop`).
 *
 * Wiring per variant test compilation:
 *   1. [KotlinCompilation.associateWith]`(variantMainCompilation)` so the
 *      test code can see the variant's main compilation's `internal`
 *      declarations + has the main output on its compile classpath.
 *   2. `defaultSourceSet.dependsOn(commonFlavorTest)` for every flavor in
 *      the variant — mirrors how v1.x active-variant test source sets are
 *      wired, but per-variant in matrix mode.
 *   3. Optional variant-specific test srcDir (`src/${variant}Test/kotlin`)
 *      for code unique to one variant's test suite.
 *
 * The active variant continues to use the standard `test` compilation —
 * matrix mode only adds compilations for INACTIVE variants on top.
 *
 * Skips reserved compilation names and falls through cleanly when the
 * variant's main compilation isn't registered yet (race during plugin
 * apply) — the registrar logs an info line and moves on.
 */
internal object TestCompilationRegistrar {

    /**
     * Compilation names that KGP reserves and we MUST NOT recreate.
     */
    private val RESERVED_NAMES = setOf("main", "test")

    /**
     * Registers a test-`KotlinCompilation` per [variantNames] entry on [target].
     *
     * Idempotent: a duplicate call with the same variant name re-uses the
     * existing test compilation instead of throwing.
     *
     * @param target the KMP target (JVM / iOS / JS / etc.) to extend
     * @param variantNames variant names whose test compilations to register
     *   (typically the inactive-variant set — the active variant uses the
     *   standard `test` compilation that KGP creates by default)
     * @param parentTestSourceSetsFor for each variant name, returns the
     *   already-resolved per-flavor TEST [KotlinSourceSet]s the variant
     *   test compilation's defaultSourceSet should depend on
     * @param variantSpecificTestSrcDirsFor for each variant name, returns
     *   any variant-specific test source directories to wire directly into
     *   the variant test compilation's defaultSourceSet
     * @param logger optional info logger for telemetry (RFC §3 Q13)
     */
    fun register(
        target: KotlinTarget,
        variantNames: List<String>,
        parentTestSourceSetsFor: (variantName: String) -> List<KotlinSourceSet> = { emptyList() },
        variantSpecificTestSrcDirsFor: (variantName: String) -> List<String> = { emptyList() },
        logger: Logger? = null,
    ) {
        if (variantNames.isEmpty()) return

        @Suppress("UNCHECKED_CAST")
        val container = target.compilations as NamedDomainObjectContainer<KotlinCompilation<*>>

        for (variantName in variantNames) {
            if (variantName in RESERVED_NAMES) {
                logger?.warn(
                    "[KMP Flavors] Skipping reserved compilation name '$variantName' " +
                        "for test registrar on target '${target.name}'",
                )
                continue
            }
            val testCompilationName = "${variantName}Test"
            // The variant's main compilation must already exist (registered by
            // CompilationRegistrar earlier in apply()). If it doesn't, skip — the
            // test compilation has nothing to associateWith.
            val variantMain = container.findByName(variantName)
            if (variantMain == null) {
                logger?.info(
                    "[KMP Flavors] No main compilation '$variantName' on target '${target.name}'; " +
                        "skipping test compilation registration for this variant × target.",
                )
                continue
            }
            val existing = container.findByName(testCompilationName)
            val testCompilation = existing ?: container.create(testCompilationName)

            // associateWith makes variant test code see variant main's `internal`
            // symbols and adds variant main's output to the test compile classpath.
            // Without this, per-variant tests can only see commonMain/public APIs.
            testCompilation.associateWith(variantMain)

            // Wire dependsOn edges into the per-flavor TEST source sets created by
            // SourceSetConfigurator. Variant test compilations hang off
            // commonFreeTest / commonPaidTest / etc. so test code can use flavor-
            // specific test helpers + see the variant main's per-flavor symbols.
            val parents = parentTestSourceSetsFor(variantName)
            parents.forEach { parent -> testCompilation.defaultSourceSet.dependsOn(parent) }

            // ISSUE #99 FIX (test counterpart) — include the target's `<target>Test`
            // source folders directly into the variant test compilation's
            // defaultSourceSet so platform `actual` test-fixture declarations
            // resolve. Mirrors the main-compilation fix in `CompilationRegistrar`
            // — and for the same KGP reason (can't `dependsOn` a default source
            // set; replay srcDirs instead).
            val targetTest = container.findByName(KotlinCompilation.TEST_COMPILATION_NAME)
            val targetTestSrcDirs: Set<java.io.File> = if (targetTest != null && targetTest !== testCompilation) {
                targetTest.defaultSourceSet.kotlin.srcDirs.toSet()
            } else {
                emptySet()
            }
            targetTestSrcDirs.forEach { srcDir -> testCompilation.defaultSourceSet.kotlin.srcDir(srcDir) }

            // Variant-specific test srcDir (e.g., `src/freeDevTest/kotlin`) — used
            // for test code that only exists for one specific variant, not per-flavor.
            val srcDirs = variantSpecificTestSrcDirsFor(variantName)
            srcDirs.forEach { dir -> testCompilation.defaultSourceSet.kotlin.srcDir(dir) }

            if (existing == null) {
                logger?.info(
                    "[KMP Flavors] Registered variant TEST compilation: " +
                        "compile${variantName.replaceFirstChar { it.uppercase() }}TestKotlin" +
                        target.name.replaceFirstChar { it.uppercase() } +
                        " (associateWith=${variantMain.name}, parents=${parents.size}, srcDirs=${srcDirs.size})",
                )
            }
        }
    }
}
