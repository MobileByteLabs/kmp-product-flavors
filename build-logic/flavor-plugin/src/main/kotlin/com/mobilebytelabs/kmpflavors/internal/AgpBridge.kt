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
import com.mobilebytelabs.kmpflavors.FlavorConfig
import com.mobilebytelabs.kmpflavors.FlavorDimension
import org.gradle.api.Project
import org.gradle.api.logging.Logger

/**
 * Propagates KMP flavor DSL into the Android Gradle Plugin (AGP) extension.
 *
 * Reflective access is used so this module does not need a compile-time
 * dependency on `com.android.tools.build:gradle`. AGP's public extension
 * (`com.android.build.api.dsl.ApplicationExtension`) is found via the
 * project's extension container only when the consumer applies AGP.
 *
 * Scope (v1.1.0): `com.android.application` only.
 * Scope (v1.2.0, Phase J): `com.android.library` + `com.android.kotlin.multiplatform.library`.
 *
 * The bridge is conservative — when AGP `productFlavors` or `buildTypes` are
 * already populated by the consumer's own DSL, it logs a warning and skips
 * propagation rather than silently overriding.
 */
internal object AgpBridge {

    private const val APP_PLUGIN_ID = "com.android.application"

    /**
     * Apply the AGP bridge if requested. Returns silently when:
     * - the project does not apply `com.android.application` (v1.1.0 scope), or
     * - AGP's `ApplicationExtension` cannot be located.
     */
    fun apply(
        project: Project,
        bridgeProductFlavors: Boolean,
        bridgeBuildTypes: Boolean,
        kmpDimensions: List<FlavorDimension>,
        kmpFlavors: List<FlavorConfig>,
        kmpBuildTypes: List<BuildTypeConfig>,
        logger: Logger,
        allowedVariantNames: Set<String> = emptySet(),
    ) {
        if (!bridgeProductFlavors && !bridgeBuildTypes) return

        if (!project.plugins.hasPlugin(APP_PLUGIN_ID)) {
            logger.info(
                "[KMP Flavors] bridgeAgp* flag set but com.android.application not " +
                    "applied — skipping AGP bridge (library plugin support arrives in v1.2.0).",
            )
            return
        }

        // v1.1.3 — register propagation via AGP's androidComponents.finalizeDsl callback
        // which runs AFTER the consumer's kmpFlavors { } DSL has evaluated but BEFORE
        // AGP creates its variants. Calling AGP setters directly (as we used to do in
        // afterEvaluate) was too late — AGP had already read productFlavors and built
        // its variant matrix.
        val finalized = runCatching {
            val componentsClass = Class.forName("com.android.build.api.variant.AndroidComponentsExtension")
            val components = project.extensions.findByType(componentsClass)
                ?: return@runCatching false
            val finalizeDsl = componentsClass.methods.firstOrNull { it.name == "finalizeDsl" && it.parameterCount == 1 }
                ?: return@runCatching false
            // AGP < 9: finalizeDsl(action: Action<CommonExtension<*,*,*,*,*,*>>)
            // AGP 9.x: DslLifecycle.finalizeDsl(action: Function1<CommonExtension<...>, Unit>)
            // The Proxy implements BOTH interfaces so the same instance dispatches
            // for either signature. Method-name routing handles "execute" (Action)
            // and "invoke" (Function1). See GOAL.md D42.
            val actionClass = Class.forName("org.gradle.api.Action")
            val function1Class = Class.forName("kotlin.jvm.functions.Function1")
            val proxy = java.lang.reflect.Proxy.newProxyInstance(
                actionClass.classLoader,
                arrayOf(actionClass, function1Class),
            ) { proxyInstance, method, args ->
                when (method.name) {
                    "execute", "invoke" -> {
                        val androidExt = args[0]
                        if (bridgeProductFlavors) {
                            propagateFlavors(androidExt, kmpDimensions, kmpFlavors, logger)
                        }
                        if (bridgeBuildTypes) {
                            propagateBuildTypes(androidExt, kmpBuildTypes, logger)
                        }
                        null
                    }

                    "toString" -> "KmpFlavors.finalizeDsl"

                    "hashCode" -> System.identityHashCode(proxyInstance)

                    "equals" -> args[0] === proxyInstance

                    else -> null
                }
            }
            finalizeDsl.invoke(components, proxy)
            // v2.6 Phase 2 — forward KMP-side variantFilter exclusions to AGP variants
            // via beforeVariants(null, action). Registered on the same components
            // extension so AGP fires the disable-action AFTER finalizeDsl locks the
            // DSL and BEFORE it materialises the variant matrix.
            if (allowedVariantNames.isNotEmpty()) {
                propagateVariantFilterToAgp(components, allowedVariantNames, logger)
            }
            true
        }.getOrDefault(false)

        if (!finalized) {
            // Fallback for very old AGP versions without androidComponents.finalizeDsl —
            // run the bridge synchronously now. May be too late for variant resolution
            // on those AGPs, but better than nothing.
            val androidExt = findApplicationExtension(project)
            if (androidExt == null) {
                logger.warn(
                    "[KMP Flavors] com.android.application is applied but ApplicationExtension " +
                        "could not be resolved — AGP bridge skipped.",
                )
                return
            }
            if (bridgeProductFlavors) {
                propagateFlavors(androidExt, kmpDimensions, kmpFlavors, logger)
            }
            if (bridgeBuildTypes) {
                propagateBuildTypes(androidExt, kmpBuildTypes, logger)
            }
        }
    }

    private fun findApplicationExtension(project: Project): Any? = runCatching {
        val cls = Class.forName("com.android.build.api.dsl.ApplicationExtension")
        project.extensions.findByType(cls)
    }.getOrNull()

    /**
     * v2.6 Phase 2 — forwards KMP-side variant filtering to AGP via `beforeVariants`.
     *
     * Called from [apply] after [propagateFlavors]. AGP fires `beforeVariants` per
     * variant after `finalizeDsl` locks the DSL and before the variant matrix is
     * materialised. The registered `Action<VariantBuilder>` disables variants whose
     * names are not in [allowedVariantNames].
     *
     * Visibility is `internal` so unit tests can invoke directly with a
     * reflection-shaped [MockAndroidComponentsExtension] — same pattern Phase 1
     * established for [propagateFlavorsLegacy] / [propagateFlavorsCrossProduct].
     *
     * Version compatibility:
     * - `beforeVariants(selector, action)` exists since AGP 7.0 (plugin floor 8.0).
     * - `VariantBuilder.setEnabled(boolean)` setter has been stable since 7.0.
     * - Reflective lookup means future setter renames degrade to WARN, not throw.
     */
    internal fun propagateVariantFilterToAgp(components: Any, allowedVariantNames: Set<String>, logger: Logger) {
        runCatching {
            val componentsClass = components.javaClass
            val beforeVariants = componentsClass.methods.firstOrNull {
                it.name == "beforeVariants" && it.parameterCount == 2
            } ?: run {
                logger.warn(
                    "[KMP Flavors] AGP fallback — beforeVariants() not available on this AGP " +
                        "version; KMP↔AGP variantFilter parity disabled.",
                )
                return@runCatching
            }
            // AGP < 9: beforeVariants(action: Action<VariantBuilder>)
            // AGP 9.x: beforeVariants(action: Function1<VariantBuilder, Unit>)
            // Dual-interface proxy — same dispatch contract as the finalizeDsl proxy
            // above. See GOAL.md D42.
            val actionClass = Class.forName("org.gradle.api.Action")
            val function1Class = Class.forName("kotlin.jvm.functions.Function1")
            val proxy = java.lang.reflect.Proxy.newProxyInstance(
                actionClass.classLoader,
                arrayOf(actionClass, function1Class),
            ) { proxyInstance, method, args ->
                when (method.name) {
                    "execute", "invoke" -> {
                        val variantBuilder = args[0]
                        val getName = variantBuilder.javaClass.methods.firstOrNull { it.name == "getName" }
                        val variantName = getName?.invoke(variantBuilder) as? String
                        if (variantName != null && variantName !in allowedVariantNames) {
                            // AGP < 9: ComponentBuilder.setEnabled(boolean)
                            // AGP 9.x: ComponentBuilder.enabled → .enable (Property<Boolean>).
                            // AgpReflectiveSetters tries setEnabled then setEnable; on
                            // AGP 9 the Property-of-T pattern routes via getter.set(false).
                            // See GOAL.md D43.
                            val applied = AgpReflectiveSetters.set(variantBuilder, "enabled", false) ||
                                AgpReflectiveSetters.set(variantBuilder, "enable", false)
                            if (applied) {
                                logger.lifecycle(
                                    "[KMP Flavors] Disabled AGP variant '$variantName' " +
                                        "(excluded by kmpFlavors.variantFilter).",
                                )
                            } else {
                                logger.warn(
                                    "[KMP Flavors] KMP↔AGP parity: variant '$variantName' should be " +
                                        "disabled but neither setEnabled() nor setEnable() resolved " +
                                        "on this AGP version.",
                                )
                            }
                        }
                        null
                    }

                    "toString" -> "KmpFlavors.variantFilter"

                    "hashCode" -> System.identityHashCode(proxyInstance)

                    "equals" -> args[0] === proxyInstance

                    else -> null
                }
            }
            // beforeVariants(selector = null, action = proxy) — null selector matches every variant.
            beforeVariants.invoke(components, null, proxy)
            logger.info(
                "[KMP Flavors] KMP↔AGP variantFilter forwarding registered " +
                    "(${allowedVariantNames.size} allowed variants).",
            )
        }.onFailure { e ->
            logger.warn(
                "[KMP Flavors] propagateVariantFilterToAgp failed: ${e.message}; " +
                    "KMP↔AGP variantFilter parity not enforced.",
            )
        }
    }

    /**
     * v2.5 refactor — dispatch on dimension count.
     *
     * The body of this method is intentionally a thin router. Both branches
     * (1-dim fast path and ≥2-dim cross-product) populate the AGP DSL surface
     * with `flavorDimensions = [...]` + `productFlavors { ... }` in the exact
     * same shape — AGP handles cross-product Cartesian internally. The split
     * exists for two reasons:
     *
     * 1. **Regression bounding** — single-dimension consumers (the vast majority
     *    of v2.4 adopters) traverse [propagateFlavorsLegacy] which is
     *    byte-identical to v2.4.3 behavior. No new code path means no new bugs
     *    for the common case.
     *
     * 2. **Forward extension surface** — the ≥2-dim branch
     *    [propagateFlavorsCrossProduct] is where v2.5+ multi-dim enhancements
     *    land: explicit KMPF-V25 conflict detection on re-apply with a
     *    different dimension assignment, future synthesized-variant-named
     *    flavors (if AGP API requires them in some future release), and
     *    variantFilter integration for cross-product pruning.
     *
     * See `01-dsl-bridge.md` AC 3 + AC 4 + AC 5 + AC 7 for the discipline.
     */
    private fun propagateFlavors(androidExt: Any, kmpDimensions: List<FlavorDimension>, kmpFlavors: List<FlavorConfig>, logger: Logger) {
        if (kmpDimensions.isEmpty() || kmpFlavors.isEmpty()) {
            logger.info("[KMP Flavors] No KMP dimensions/flavors to propagate to AGP.")
            return
        }
        if (kmpDimensions.size <= 1) {
            propagateFlavorsLegacy(androidExt, kmpDimensions, kmpFlavors, logger)
        } else {
            propagateFlavorsCrossProduct(androidExt, kmpDimensions, kmpFlavors, logger)
        }
    }

    /**
     * v2.4.3 byte-identical 1-dimension path. Locked Decision D9 (`01-dsl-bridge.md`)
     * mandates this branch is byte-identical to v2.4.3 — single-dim consumers see
     * zero behavior drift.
     */
    internal fun propagateFlavorsLegacy(androidExt: Any, kmpDimensions: List<FlavorDimension>, kmpFlavors: List<FlavorConfig>, logger: Logger) {
        // Collision check.
        // - If AGP productFlavors already contains every KMP flavor name (e.g. the
        //   consumer's convention plugin called configureFlavors() synchronously inside
        //   a withPlugin("com.android.application") callback) the bridge is a no-op:
        //   log info and return silently — bridge stays idempotent.
        // - If existing flavors differ (consumer wrote a manual android { productFlavors {} }
        //   block that conflicts), warn so the user knows the kmpFlavors {} DSL won't drive
        //   AGP for those flavors.
        val existing = readAgpProductFlavors(androidExt)
        if (existing.isNotEmpty()) {
            val kmpNames = kmpFlavors.map { it.name }.toSet()
            if (existing.toSet().containsAll(kmpNames)) {
                logger.info(
                    "[KMP Flavors] AGP productFlavors already registered (${existing.joinToString()}) — " +
                        "bridge is a no-op (idempotent).",
                )
                return
            }
            logger.warn(
                "[KMP Flavors] AGP productFlavors already declares: ${existing.joinToString()} " +
                    "which does not cover the kmpFlavors {} set (${kmpNames.joinToString()}) — " +
                    "skipping bridge propagation to avoid corrupting the existing config. " +
                    "Remove the hand-written android { productFlavors {} } block to let " +
                    "kmpFlavors {} drive AGP, or set bridgeAgpProductFlavors.set(false).",
            )
            return
        }

        // Append dimensions in priority order (higher priority first, matching FlavorVariantResolver).
        val orderedDims = kmpDimensions.sortedBy { -(it.priority.orNull ?: 0) }
        val agpDimensions = orderedDims.map { it.name }
        appendDimensions(androidExt, agpDimensions)

        // Register flavors.
        val productFlavors = readMutableProductFlavorsContainer(androidExt) ?: run {
            logger.warn("[KMP Flavors] Could not access AGP productFlavors container — bridge skipped.")
            return
        }
        for (kmp in kmpFlavors) {
            registerAgpFlavor(productFlavors, kmp)
        }
        logger.lifecycle(
            "[KMP Flavors] Bridged ${kmpFlavors.size} flavor(s) across ${agpDimensions.size} dimension(s) into AGP.",
        )
    }

    /**
     * v2.5 ≥2-dimension cross-product path.
     *
     * Architectural note: AGP handles cross-product Cartesian natively. Given
     * `flavorDimensions = ["tier", "env"]` + `productFlavors { free; paid; dev; prod }`
     * (each with `dimension =` set), AGP automatically creates the 4 variants
     * `freeDev`, `freePrd`, `paidDev`, `paidPrd`. We do NOT need to register
     * variant-named flavors ourselves — AGP would reject `productFlavor.dimension = "tier-env"`
     * as an invalid composite-dimension reference.
     *
     * What this branch adds over [propagateFlavorsLegacy]:
     *
     * 1. **KMPF-V25 re-apply conflict detection** — if an AGP flavor with the same
     *    name already exists but with a DIFFERENT `dimension =` assignment than the
     *    KMP-side declaration, log a structured WARNING with the KMPF-V25 code so
     *    downstream tooling (CI grep, dashboard) can surface the divergence.
     *    Hard-throwing here would break the existing idempotency-on-re-apply
     *    contract, so this is a warn-only signal until v3.x.
     *
     * 2. **Cross-product variant count log** — telemetry that signals AGP will
     *    materialize `product(|dim_i|)` variants — useful for `multi-dim-3d` sample
     *    debugging.
     */
    internal fun propagateFlavorsCrossProduct(androidExt: Any, kmpDimensions: List<FlavorDimension>, kmpFlavors: List<FlavorConfig>, logger: Logger) {
        // Same idempotency check as legacy — short-circuit on exact-coverage re-apply.
        val existing = readAgpProductFlavors(androidExt)
        if (existing.isNotEmpty()) {
            val kmpNames = kmpFlavors.map { it.name }.toSet()
            if (existing.toSet().containsAll(kmpNames)) {
                logger.info(
                    "[KMP Flavors] AGP productFlavors already registered (${existing.joinToString()}) — " +
                        "bridge is a no-op (idempotent, ≥2-dim re-apply).",
                )
                return
            }
            logger.warn(
                "[KMP Flavors] KMPF-V25 — AGP productFlavors already declares ${existing.joinToString()}, " +
                    "which does not cover the kmpFlavors {} set (${kmpNames.joinToString()}) — " +
                    "skipping bridge propagation to avoid corrupting the existing config. " +
                    "Possible cross-vault hand-edit conflict on multi-dim re-apply. " +
                    "Remove the hand-written android { productFlavors {} } block to let " +
                    "kmpFlavors {} drive AGP, or set bridgeAgpProductFlavors.set(false).",
            )
            return
        }

        val orderedDims = kmpDimensions.sortedBy { -(it.priority.orNull ?: 0) }
        val agpDimensions = orderedDims.map { it.name }
        appendDimensions(androidExt, agpDimensions)

        val productFlavors = readMutableProductFlavorsContainer(androidExt) ?: run {
            logger.warn("[KMP Flavors] Could not access AGP productFlavors container — bridge skipped.")
            return
        }
        for (kmp in kmpFlavors) {
            registerAgpFlavor(productFlavors, kmp)
        }

        // Cross-product variant count telemetry — AGP will materialize the product of
        // per-dimension member counts. Useful for debugging combinatorial blowup
        // (see docs/MULTI_DIM_GUIDE.md when authored in Phase 4).
        val perDimCounts = orderedDims.map { dim ->
            kmpFlavors.count { it.dimension.orNull == dim.name }
        }
        val variantProduct = perDimCounts.fold(1) { acc, n -> acc * n }
        logger.lifecycle(
            "[KMP Flavors] Bridged ${kmpFlavors.size} flavor(s) across ${agpDimensions.size} dimension(s) into AGP " +
                "(cross-product = $variantProduct variants from ${perDimCounts.joinToString(" × ")} per-dimension members).",
        )
    }

    private fun propagateBuildTypes(androidExt: Any, kmpBuildTypes: List<BuildTypeConfig>, logger: Logger) {
        if (kmpBuildTypes.isEmpty()) {
            logger.info("[KMP Flavors] No KMP buildTypes to propagate to AGP.")
            return
        }

        val container = readMutableBuildTypesContainer(androidExt) ?: run {
            logger.warn("[KMP Flavors] Could not access AGP buildTypes container — bridge skipped.")
            return
        }

        // Idempotency check for build types.
        // - 'debug' and 'release' are AGP defaults (always present) so they don't count
        //   as a consumer-authored collision.
        // - If the remaining (non-default) AGP build types already cover every KMP
        //   buildType, treat as no-op (e.g. consumer's convention plugin already
        //   registered them synchronously). Bridge is idempotent.
        // - Else warn — the consumer has hand-written android { buildTypes {} } that
        //   conflicts and we don't want to corrupt it.
        val existingNames = readAgpBuildTypeNames(androidExt)
        val nonDefault = existingNames.filterNot { it == "debug" || it == "release" }
        if (nonDefault.isNotEmpty()) {
            val kmpNames = kmpBuildTypes.map { it.name }.filterNot { it == "debug" || it == "release" }.toSet()
            if (existingNames.toSet().containsAll(kmpNames)) {
                logger.info(
                    "[KMP Flavors] AGP buildTypes already registered (${existingNames.joinToString()}) — " +
                        "bridge is a no-op (idempotent).",
                )
                return
            }
            logger.warn(
                "[KMP Flavors] AGP buildTypes already declares custom types: ${nonDefault.joinToString()} " +
                    "which does not cover the kmpFlavors {} set (${kmpNames.joinToString()}) — " +
                    "skipping bridge propagation. Remove the hand-written android { buildTypes {} } " +
                    "additions to let kmpFlavors {} drive AGP, or set bridgeAgpBuildTypes.set(false).",
            )
            return
        }

        for (kmp in kmpBuildTypes) {
            registerAgpBuildType(container, kmp)
        }
        logger.lifecycle("[KMP Flavors] Bridged ${kmpBuildTypes.size} buildType(s) into AGP.")
    }

    // --- Reflective AGP accessors ---------------------------------------------------------

    private fun appendDimensions(androidExt: Any, names: List<String>) {
        val getter = androidExt.javaClass.methods.firstOrNull { it.name == "getFlavorDimensions" }
            ?: return

        @Suppress("UNCHECKED_CAST")
        val mutable = getter.invoke(androidExt) as? MutableList<String> ?: return
        for (name in names) {
            if (name !in mutable) mutable.add(name)
        }
    }

    private fun readAgpProductFlavors(androidExt: Any): List<String> {
        val getter = androidExt.javaClass.methods.firstOrNull { it.name == "getProductFlavors" }
            ?: return emptyList()
        val container = getter.invoke(androidExt) ?: return emptyList()
        val nameGetter = container.javaClass.methods.firstOrNull { it.name == "getNames" }
            ?: return emptyList()
        @Suppress("UNCHECKED_CAST")
        return (nameGetter.invoke(container) as? Set<String>)?.toList() ?: emptyList()
    }

    private fun readMutableProductFlavorsContainer(androidExt: Any): Any? {
        val getter = androidExt.javaClass.methods.firstOrNull { it.name == "getProductFlavors" }
            ?: return null
        return getter.invoke(androidExt)
    }

    private fun readMutableBuildTypesContainer(androidExt: Any): Any? {
        val getter = androidExt.javaClass.methods.firstOrNull { it.name == "getBuildTypes" }
            ?: return null
        return getter.invoke(androidExt)
    }

    private fun readAgpBuildTypeNames(androidExt: Any): List<String> {
        val container = readMutableBuildTypesContainer(androidExt) ?: return emptyList()
        val nameGetter = container.javaClass.methods.firstOrNull { it.name == "getNames" }
            ?: return emptyList()
        @Suppress("UNCHECKED_CAST")
        return (nameGetter.invoke(container) as? Set<String>)?.toList() ?: emptyList()
    }

    private fun registerAgpFlavor(container: Any, kmp: FlavorConfig) {
        val maybeCreate = container.javaClass.methods.firstOrNull {
            it.name == "maybeCreate" && it.parameterCount == 1
        } ?: return
        val agpFlavor = maybeCreate.invoke(container, kmp.name) ?: return

        kmp.dimension.orNull?.let { AgpReflectiveSetters.set(agpFlavor, "dimension", it) }
        kmp.applicationIdSuffix.orNull?.let { AgpReflectiveSetters.set(agpFlavor, "applicationIdSuffix", it) }
        kmp.versionNameSuffix.orNull?.let { AgpReflectiveSetters.set(agpFlavor, "versionNameSuffix", it) }

        // matchingFallbacks: Gradle's getMatchingFallbacks() returns a mutable list — we
        // append to it so AGP defaults are preserved.
        val fallbacks = kmp.matchingFallbacks.getOrElse(emptyList())
        if (fallbacks.isNotEmpty()) {
            appendMatchingFallbacks(agpFlavor, fallbacks)
        }
    }

    private fun appendMatchingFallbacks(agpFlavor: Any, fallbacks: List<String>) {
        runCatching {
            val getter = agpFlavor.javaClass.methods.firstOrNull { it.name == "getMatchingFallbacks" }
                ?: return

            @Suppress("UNCHECKED_CAST")
            val list = getter.invoke(agpFlavor) as? MutableList<String> ?: return
            for (fb in fallbacks) {
                if (fb !in list) list.add(fb)
            }
        }
    }

    private fun registerAgpBuildType(container: Any, kmp: BuildTypeConfig) {
        val maybeCreate = container.javaClass.methods.firstOrNull {
            it.name == "maybeCreate" && it.parameterCount == 1
        } ?: return
        val agpBuildType = maybeCreate.invoke(container, kmp.name) ?: return

        kmp.isDebuggable.orNull?.let { AgpReflectiveSetters.set(agpBuildType, "debuggable", it) }
        kmp.isMinifyEnabled.orNull?.let { AgpReflectiveSetters.set(agpBuildType, "minifyEnabled", it) }
        kmp.applicationIdSuffix.orNull?.let { AgpReflectiveSetters.set(agpBuildType, "applicationIdSuffix", it) }
    }
}
