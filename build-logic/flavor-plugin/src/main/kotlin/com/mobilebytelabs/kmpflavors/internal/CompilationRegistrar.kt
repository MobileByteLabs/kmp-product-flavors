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
import java.io.File

/**
 * Registers one [KotlinCompilation] per variant on a single [KotlinTarget].
 *
 * Load-bearing piece of v2.0 matrix mode (RFC §3 Q1-Q4 + Q11 + Q12 + Q17 + Q25).
 *
 * Per RFC §1.1 (Zero-Touch Adoption Tenet), consumer KMP module
 * `build.gradle.kts` files NEVER call this — only the plugin's own
 * `apply()` invokes the registrar after [PlatformDetector] surfaces
 * detected targets.
 *
 * Task naming (RFC §3 Q1-B) is produced automatically by KGP from
 * `compilations.create("freeDev")` — KGP emits `compileFreeDevKotlinDesktop`
 * etc. The registrar just supplies the variant name; no manual task
 * registration.
 *
 * Source-set wiring (RFC §3 Q2-C hybrid): the registrar adds two kinds
 * of wiring per variant:
 *   1. `dependsOn` edges into existing per-flavor source sets that
 *      v1.x SourceSetConfigurator created (commonFree, commonPaid, etc.).
 *      This keeps `expect` and `actual` in DIFFERENT Kotlin modules so
 *      KMP's expect/actual semantics work across variants (Q11).
 *   2. Optional variant-specific `srcDir` entries for code that is unique
 *      to a single variant (e.g. `src/freeDev/kotlin`).
 *
 * Variant compilations deliberately do NOT `associateWith(main)`:
 *   - Doing so would pull the active variant's per-flavor source sets
 *     into every other variant compilation, breaking cross-variant
 *     isolation (Q12) and producing duplicate `actual` declarations.
 *   - Per-variant compileClasspath wiring lands in W2 via
 *     `DependencyConfigurator`'s matrix-mode extension (Q17).
 *
 * W1 scope: handles any [KotlinTarget] via duck-typing through the
 * abstract `compilations` container. JVM is exercised first by W1
 * tests; iOS / JS / WasmJs are exercised in W3 integration tests.
 */
internal object CompilationRegistrar {

    /**
     * Compilation names that KGP reserves and we MUST NOT recreate.
     * Attempting to `create("main")` throws at runtime.
     */
    private val RESERVED_NAMES = setOf("main", "test")

    /**
     * Registers a `KotlinCompilation` per [variantNames] entry on [target].
     *
     * Skips reserved names. Idempotent: a duplicate call with the same
     * variant name re-uses the existing compilation instead of throwing.
     *
     * For each variant, the registrar wires:
     *   1. `defaultSourceSet.dependsOn(parent)` for every source set in
     *      [parentSourceSetsFor]`(variantName)` — the proper Kotlin
     *      source-set hierarchy (RFC §3 Q2-C) so expect/actual works
     *      across variants (Q11) and isolation holds (Q12).
     *   2. `defaultSourceSet.kotlin.srcDir(path)` for every directory in
     *      [variantSpecificSrcDirsFor]`(variantName)` — used for code
     *      unique to a single variant (rare; most code lives in
     *      per-flavor source sets handled by clause 1).
     *
     * @param target the KMP target (JVM / iOS / JS / etc.) to extend
     * @param variantNames variant names to register, e.g. `["freeDev", "paidProd"]`
     * @param parentSourceSetsFor for each variant name, returns the
     *   already-resolved per-flavor [KotlinSourceSet]s the variant
     *   compilation's defaultSourceSet should depend on
     * @param variantSpecificSrcDirsFor for each variant name, returns
     *   any variant-specific source directories to wire directly into
     *   the variant compilation's defaultSourceSet
     * @param logger optional info logger for telemetry (RFC §3 Q13)
     */
    fun register(
        target: KotlinTarget,
        variantNames: List<String>,
        parentSourceSetsFor: (variantName: String) -> List<KotlinSourceSet> = { emptyList() },
        variantSpecificSrcDirsFor: (variantName: String) -> List<String> = { emptyList() },
        logger: Logger? = null,
        shouldRegisterVariantOnTarget: (variantName: String, targetName: String) -> Boolean = { _, _ -> true },
    ) {
        if (variantNames.isEmpty()) return

        @Suppress("UNCHECKED_CAST")
        val container = target.compilations as NamedDomainObjectContainer<KotlinCompilation<*>>

        for (variantName in variantNames) {
            if (variantName in RESERVED_NAMES) {
                logger?.warn(
                    "[KMP Flavors] Skipping reserved compilation name '$variantName' " +
                        "on target '${target.name}'",
                )
                continue
            }

            // v2.6 Phase 4 — `variantFilter { excludeTargets(...) }` opt-out. Skip
            // registration when the consumer's filter excluded this (variant, target)
            // pair. Dead source sets stay on disk (intentional — see SOURCE_SET_DISCIPLINE).
            if (!shouldRegisterVariantOnTarget(variantName, target.name)) {
                logger?.info(
                    "[KMP Flavors] Skipping variant '$variantName' on target '${target.name}' " +
                        "(excluded via kmpFlavors.variantFilter { excludeTargets(...) }).",
                )
                continue
            }
            val existing = container.findByName(variantName)
            val variant = existing ?: container.create(variantName)

            // Wire dependsOn edges into the per-flavor source sets created by
            // v1.x SourceSetConfigurator. This is the Q2-C hybrid: the variant's
            // defaultSourceSet hangs off commonFree / commonPaid / etc. via
            // standard KMP source-set dependsOn, so `expect` in commonMain and
            // `actual` in commonFlavor live in separate compilation modules
            // exactly the way KMP expects.
            val parents = parentSourceSetsFor(variantName)
            parents.forEach { parent -> variant.defaultSourceSet.dependsOn(parent) }

            // ISSUE #99 FIX — include the target's `<target>Main` source folders
            // (e.g. `src/desktopMain/kotlin/`) directly into the variant's
            // defaultSourceSet so platform `actual` declarations resolve.
            //
            // Without this, `expect` declarations in `commonMain` have no path
            // to their `actual` in `<target>Main` from the inactive-variant
            // compilation's perspective — KMP fails with "Expected ... has no
            // actual declaration in module <commonMain> for <Target>".
            //
            // We can't use `dependsOn(targetMain.defaultSourceSet)` here because
            // KGP forbids non-default source sets from declaring a `dependsOn`
            // on a defaultSourceSet ("Invalid Dependency on Default Compilation
            // Source Set" warning). Instead we replay the target main's source
            // folders into the variant's defaultSourceSet — the actual files
            // belong to the variant compilation directly. This is safe because
            // those same files also compile via the active variant's standard
            // `main` compilation, and the two compilation outputs are kept in
            // separate JARs (no class duplication on classpath).
            //
            // Caller (KmpFlavorPlugin) already restricts this registrar to
            // non-Android targets — Android's variant source sets are handled
            // natively by AGP.
            val targetMain = container.findByName(KotlinCompilation.MAIN_COMPILATION_NAME)
            val targetMainSrcDirs: Set<File> = if (targetMain != null && targetMain !== variant) {
                targetMain.defaultSourceSet.kotlin.srcDirs.toSet()
            } else {
                emptySet()
            }
            targetMainSrcDirs.forEach { srcDir -> variant.defaultSourceSet.kotlin.srcDir(srcDir) }

            // Wire variant-specific source dirs directly into the variant
            // defaultSourceSet (rare path — used for code that only exists
            // for one specific variant, not per-flavor).
            val srcDirs = variantSpecificSrcDirsFor(variantName)
            srcDirs.forEach { dir -> variant.defaultSourceSet.kotlin.srcDir(dir) }

            if (existing == null) {
                logger?.info(
                    "[KMP Flavors] Registered variant compilation: " +
                        "${variantName}Kotlin${target.name.replaceFirstChar { it.uppercase() }} " +
                        "(parents=${parents.size}, srcDirs=${srcDirs.size}, " +
                        "targetMain=${targetMain?.defaultSourceSet?.name ?: "absent"})",
                )
            }
        }
    }
}
