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
import org.jetbrains.kotlin.gradle.plugin.KotlinTarget

/**
 * Registers one [KotlinCompilation] per variant on a single [KotlinTarget].
 *
 * Load-bearing piece of v2.0 matrix mode (RFC §3 Q1-Q4 + Q12 + Q17 + Q25).
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
 * Variant compilation `associateWith(main)` (RFC §3 Q12 prerequisite)
 * gives each variant access to `main`'s classpath so the variant's
 * `commonMain` consumers compile.
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
     * For each created variant, wires `defaultSourceSet.kotlin.srcDir(path)`
     * for every directory returned by [srcDirsFor]. This is the W2 piece
     * that unlocks per-variant `expect`/`actual` and cross-variant
     * isolation (RFC §3 Q11 + Q12). Pass `srcDirsFor = { _ -> emptyList() }`
     * for the W1 behaviour of registering compilations without source
     * dirs (the test suite uses this).
     *
     * @param target the KMP target (JVM / iOS / JS / etc.) to extend
     * @param variantNames variant names to register, e.g. `["freeDev", "paidProd"]`
     * @param srcDirsFor for each variant name, returns the list of source
     *   roots to wire into the variant compilation's defaultSourceSet
     *   (Kotlin source dirs only; resources land in W4)
     * @param logger optional info logger for telemetry (RFC §3 Q13)
     */
    fun register(
        target: KotlinTarget,
        variantNames: List<String>,
        srcDirsFor: (variantName: String) -> List<String> = { emptyList() },
        logger: Logger? = null,
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
            val existing = container.findByName(variantName)
            val variant = existing ?: container.create(variantName)
            // Deliberately NOT associateWith(main): variant compilations are
            // independent of `main`. associating would (a) pull the active
            // variant's per-flavor source sets into every other variant
            // (creating duplicate `actual` declarations for the same `expect`),
            // and (b) make cross-variant isolation (RFC §3 Q12) impossible.
            // Per-variant dependency wiring lands in W2 of the v2.0 impl plan
            // (Q17), where each variant gets its own `compileClasspath` via
            // DependencyConfigurator's matrix-mode extension.
            // Wire per-variant source dirs into the variant's defaultSourceSet.
            // RFC §3 Q2-C hybrid: explicit srcDir for variant-specific roots
            // (commonFree, commonDev, commonFreeDev, …) + Hierarchy Template
            // for the common edges. The default source set's `kotlin.srcDir(...)`
            // call is the same shape the D1 spike used.
            val srcDirs = srcDirsFor(variantName)
            srcDirs.forEach { dir ->
                variant.defaultSourceSet.kotlin.srcDir(dir)
            }
            if (existing == null) {
                logger?.info(
                    "[KMP Flavors] Registered variant compilation: " +
                        "${variantName}Kotlin${target.name.replaceFirstChar { it.uppercase() }} " +
                        "(srcDirs=${srcDirs.size})",
                )
            }
        }
    }
}
