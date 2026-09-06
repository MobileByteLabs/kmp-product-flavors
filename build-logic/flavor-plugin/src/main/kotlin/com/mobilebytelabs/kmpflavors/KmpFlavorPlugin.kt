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

package com.mobilebytelabs.kmpflavors

import com.mobilebytelabs.kmpflavors.internal.AggregateTasksRegistrar
import com.mobilebytelabs.kmpflavors.internal.AgpBridge
import com.mobilebytelabs.kmpflavors.internal.BuildScanConfigurator
import com.mobilebytelabs.kmpflavors.internal.CompilationRegistrar
import com.mobilebytelabs.kmpflavors.internal.ComposeResourcesConfigurator
import com.mobilebytelabs.kmpflavors.internal.DependencyConfigurator
import com.mobilebytelabs.kmpflavors.internal.DependencyGuardHelper
import com.mobilebytelabs.kmpflavors.internal.DetektPerVariantHelper
import com.mobilebytelabs.kmpflavors.internal.FeatureFlagHelpers
import com.mobilebytelabs.kmpflavors.internal.FlavorVariantResolver
import com.mobilebytelabs.kmpflavors.internal.GenerateBuildConfigTasksRegistrar
import com.mobilebytelabs.kmpflavors.internal.IntermediateSourceSetConfigurator
import com.mobilebytelabs.kmpflavors.internal.KmpFlavorPluginValidator
import com.mobilebytelabs.kmpflavors.internal.KmpFlavorValidationSeverity
import com.mobilebytelabs.kmpflavors.internal.MatrixModeResolver
import com.mobilebytelabs.kmpflavors.internal.PerVariantComposeHotReloadConfigurator
import com.mobilebytelabs.kmpflavors.internal.PerVariantIosPublishConfigurator
import com.mobilebytelabs.kmpflavors.internal.PerVariantIosXcframeworkConfigurator
import com.mobilebytelabs.kmpflavors.internal.PerVariantJsPublishConfigurator
import com.mobilebytelabs.kmpflavors.internal.PerVariantNpmPublishConfigurator
import com.mobilebytelabs.kmpflavors.internal.PerVariantPublishConfigurator
import com.mobilebytelabs.kmpflavors.internal.PerVariantSbomConfigurator
import com.mobilebytelabs.kmpflavors.internal.PlatformDetector
import com.mobilebytelabs.kmpflavors.internal.PlatformPropertiesConfigurator
import com.mobilebytelabs.kmpflavors.internal.ProjectIsolationCompatChecker
import com.mobilebytelabs.kmpflavors.internal.SourceSetConfigurator
import com.mobilebytelabs.kmpflavors.internal.SpotlessDetektScopeHelper
import com.mobilebytelabs.kmpflavors.internal.TestCompilationRegistrar
import com.mobilebytelabs.kmpflavors.internal.VariantBuildCacheKeyConfigurator
import com.mobilebytelabs.kmpflavors.internal.VariantPromotionConfigurator
import com.mobilebytelabs.kmpflavors.tasks.DiagnoseVariantTask
import com.mobilebytelabs.kmpflavors.tasks.GenerateBuildConfigTask
import com.mobilebytelabs.kmpflavors.tasks.GenerateRunConfigurationsTask
import com.mobilebytelabs.kmpflavors.tasks.GenerateSpmManifestTask
import com.mobilebytelabs.kmpflavors.tasks.GenerateVariantRunConfigurationsTask
import com.mobilebytelabs.kmpflavors.tasks.InitFlavorSourceSetsTask
import com.mobilebytelabs.kmpflavors.tasks.ListActiveVariantTask
import com.mobilebytelabs.kmpflavors.tasks.ListFlavorsTask
import com.mobilebytelabs.kmpflavors.tasks.ListVariantCompilationsTask
import com.mobilebytelabs.kmpflavors.tasks.PrintFlavorPropertiesTask
import com.mobilebytelabs.kmpflavors.tasks.SwitchVariantAndReloadTask
import com.mobilebytelabs.kmpflavors.tasks.ValidateFlavorsTask
import org.gradle.api.Action
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

/**
 * KMP Product Flavors Gradle Plugin.
 *
 * This plugin brings Android-style product flavor support to Kotlin Multiplatform projects.
 * It enables conditional compilation, source set management, and build config generation
 * based on the active flavor variant.
 *
 * ## Usage
 *
 * ```kotlin
 * plugins {
 *     kotlin("multiplatform")
 *     id("io.github.mobilebytelabs.kmp-product-flavors")
 * }
 *
 * kmpFlavors {
 *     generateBuildConfig.set(true)
 *     buildConfigPackage.set("com.example.app")
 *
 *     flavorDimensions {
 *         register("tier") { priority.set(0) }
 *         register("environment") { priority.set(1) }
 *     }
 *
 *     flavors {
 *         register("free") {
 *             dimension.set("tier")
 *             isDefault.set(true)
 *             buildConfigField("Boolean", "IS_PREMIUM", "false")
 *         }
 *         register("paid") {
 *             dimension.set("tier")
 *             buildConfigField("Boolean", "IS_PREMIUM", "true")
 *         }
 *         register("dev") {
 *             dimension.set("environment")
 *             isDefault.set(true)
 *             buildConfigField("String", "BASE_URL", "\"https://dev-api.example.com\"")
 *         }
 *         register("prod") {
 *             dimension.set("environment")
 *             buildConfigField("String", "BASE_URL", "\"https://api.example.com\"")
 *         }
 *     }
 * }
 * ```
 *
 * ## Gradle Properties
 *
 * - `kmpFlavor`: Override the active flavor variant (e.g., `-PkmpFlavor=paidProd`)
 *
 * ## Tasks
 *
 * - `generateFlavorBuildConfig`: Generates the BuildConfig Kotlin object
 * - `validateFlavors`: Validates the flavor configuration
 * - `listFlavors`: Lists all available flavor variants
 */
class KmpFlavorPlugin : Plugin<Project> {

    override fun apply(project: Project) {
        // Register the extension
        val extension = project.extensions.create(
            "kmpFlavors",
            KmpFlavorExtension::class.java,
        )

        // RFC §3 Q17 — pre-create per-flavor `commonFlavor` source sets eagerly
        // as flavors are registered, so consumers can reference them in the
        // standard KMP DSL (`kotlin { sourceSets { val commonPaid by getting
        // { dependencies { ... } } } }`) without having to wrap in
        // `maybeCreate` or wait for `afterEvaluate`. The callback fires
        // synchronously when each flavor is added to `extension.flavors`.
        //
        // Order constraint for consumers: the `kmpFlavors { flavors {
        // register(...) } }` block must come BEFORE any `kotlin { sourceSets
        // { val commonFlavor by getting } }` block in the same build file.
        // This is documented in docs/MATRIX_MODE.md.
        project.plugins.withId("org.jetbrains.kotlin.multiplatform") {
            val kotlin = project.extensions.getByType(KotlinMultiplatformExtension::class.java)
            // `whenObjectAdded` fires synchronously and eagerly whenever a
            // flavor is registered. Avoid `.all` here — Kotlin's stdlib
            // `Iterable.all(predicate)` shadows Gradle's
            // `NamedDomainObjectContainer.all(Action)` and produces a
            // confusing "Return type mismatch: expected 'Boolean'" error.
            val createPerFlavorSourceSet = object : Action<FlavorConfig> {
                override fun execute(flavor: FlavorConfig) {
                    // v2.6 Tier E.1 — only create eagerly when the consumer opted into
                    // matrix mode OR `createInactiveFlavorSourceSets`. Both flags must be
                    // set BEFORE `flavors {}` to be observed by this hook (Property values
                    // surface here via `getOrElse`; the convention default is consulted
                    // only as a fallback). When neither is true (the v2.6 default),
                    // SourceSetConfigurator creates the active flavor's source set in
                    // `afterEvaluate` and skips inactive ones outright — eliminating
                    // KGP's "Unused Kotlin Source Sets" warning by structural avoidance.
                    val matrixModeOptIn = extension.buildMatrix.getOrElse(false)
                    val inactiveOptIn = extension.createInactiveFlavorSourceSets.getOrElse(false)
                    if (!matrixModeOptIn && !inactiveOptIn) return
                    val ssName = "common${flavor.name.replaceFirstChar { it.uppercase() }}"
                    kotlin.sourceSets.maybeCreate(ssName)
                }
            }
            extension.flavors.whenObjectAdded(createPerFlavorSourceSet)
        }

        // v2.2 Phase 0D — auto-flip `enableBuildTypes` when the consumer registers any
        // `buildTypes { register(...) }`. Most consumers forget to flip the flag after
        // declaring buildTypes; the auto-flip is the obvious DWIM.
        //
        // Explicit `enableBuildTypes.set(false)` still wins (consumer wants flavor-only
        // matrix despite declaring build types) — the hook only fires when the flag is
        // still false at the time the first buildType is registered.
        val enableBuildTypesAutoFlip = object : Action<BuildTypeConfig> {
            override fun execute(buildType: BuildTypeConfig) {
                if (extension.autoEnable.get() && !extension.enableBuildTypes.get()) {
                    extension.enableBuildTypes.set(true)
                    project.logger.info(
                        "[KMP Flavors] Phase 0D — auto-flipping enableBuildTypes to true " +
                            "because consumer registered buildType '${buildType.name}'. " +
                            "Set `kmpFlavors.enableBuildTypes.set(false)` to keep a flavor-only matrix.",
                    )
                }
            }
        }
        extension.buildTypes.whenObjectAdded(enableBuildTypesAutoFlip)

        // v2.2 Phase 0B + 0C — auto-enable publishMatrix + adjacent-plugin helpers when
        // their plugins are detected. Each withPlugin callback latches a flag that we
        // read inside afterEvaluate so the autoEnable check sees the consumer's
        // `kmpFlavors { autoEnable.set(false) }` value rather than the convention default.
        var mavenPublishApplied = false
        var dependencyGuardApplied = false
        var spotlessApplied = false
        var detektApplied = false
        project.pluginManager.withPlugin("maven-publish") { mavenPublishApplied = true }
        project.pluginManager.withPlugin("com.dropbox.dependency-guard") { dependencyGuardApplied = true }
        project.pluginManager.withPlugin("com.diffplug.spotless") { spotlessApplied = true }
        project.pluginManager.withPlugin("io.gitlab.arturbosch.detekt") { detektApplied = true }
        project.afterEvaluate {
            // Phase 0 auto-enables run inside afterEvaluate so the consumer's `kmpFlavors
            // { autoEnable.set(false) }` has been evaluated by now.
            if (!extension.autoEnable.get()) return@afterEvaluate
            if (mavenPublishApplied && !extension.publishMatrix.isPresent) {
                extension.publishMatrix.set(true)
                project.logger.info(
                    "[KMP Flavors] Phase 0B — auto-enabling publishMatrix because " +
                        "`maven-publish` is applied. Set `kmpFlavors.publishMatrix.set(false)` to opt out.",
                )
            }
            if (dependencyGuardApplied && !extension.dependencyGuardPerVariant.get()) {
                extension.dependencyGuardPerVariant.set(true)
                project.logger.info("[KMP Flavors] Phase 0C — auto-enabling dependencyGuardPerVariant.")
            }
            if ((spotlessApplied || detektApplied) && !extension.excludeGeneratedFromFormatters.get()) {
                extension.excludeGeneratedFromFormatters.set(true)
                project.logger.info("[KMP Flavors] Phase 0C — auto-enabling excludeGeneratedFromFormatters.")
            }
            if (detektApplied && !extension.detektPerVariant.get()) {
                extension.detektPerVariant.set(true)
                project.logger.info("[KMP Flavors] Phase 0C — auto-enabling detektPerVariant.")
            }
        }

        // AGP productFlavor registration must happen BEFORE AGP processes its
        // finalizeDsl queue. Gradle fires afterEvaluate hooks in registration order
        // — if AGP's hook fires before ours, finalizeDsl is locked and our hook
        // never executes. By registering during apply() via pluginManager.withPlugin
        // (synchronous when the plugin is already applied, deferred otherwise),
        // the finalizeDsl proxy gets queued early. The proxy reads `extension.flavors`
        // lazily at execute time, so flavors populated by downstream convention-plugin
        // configuration are still visible.
        project.pluginManager.withPlugin("com.android.application") {
            runCatching {
                com.mobilebytelabs.kmpflavors.internal.AgpProductFlavorRegistrar.apply(
                    project,
                    extension,
                    project.logger,
                )
                com.mobilebytelabs.kmpflavors.internal.SigningConfigBridge.apply(
                    project,
                    extension,
                    project.logger,
                )
            }.onFailure { e ->
                project.logger.warn("[KMP Flavors] early AGP productFlavor registration failed: ${e.message}")
            }
        }
        project.pluginManager.withPlugin("com.android.library") {
            runCatching {
                com.mobilebytelabs.kmpflavors.internal.AgpProductFlavorRegistrar.apply(
                    project,
                    extension,
                    project.logger,
                )
                com.mobilebytelabs.kmpflavors.internal.SigningConfigBridge.apply(
                    project,
                    extension,
                    project.logger,
                )
            }.onFailure { e ->
                project.logger.warn("[KMP Flavors] early AGP productFlavor registration failed: ${e.message}")
            }
        }

        // Defer the remaining phase dispatch until after project evaluation.
        project.afterEvaluate {
            configurePlugin(project, extension)
            // Phase dispatch — composes phaseKmp + phaseIos + phaseDesktop + phaseWeb.
            // phaseAgp's productFlavor registration was already handled above via
            // pluginManager.withPlugin so it lands ahead of AGP's variant graph.
            runCatching {
                com.mobilebytelabs.kmpflavors.internal.FlavorPhaseDispatcher.apply(project, extension)
            }.onFailure { e ->
                project.logger.warn("[KMP Flavors] phase dispatch failed: ${e.message}")
            }
        }
    }

    private fun configurePlugin(project: Project, extension: KmpFlavorExtension) {
        val logger = project.logger

        // Find KMP extension
        val kotlin = project.extensions.findByType(KotlinMultiplatformExtension::class.java)
        if (kotlin == null) {
            logger.warn("[KMP Flavors] Kotlin Multiplatform plugin not found. Skipping flavor configuration.")
            return
        }

        val flavors = extension.flavors.toList()
        val dimensions = extension.flavorDimensions.toList()

        // Skip if no flavors configured (v1.x behaviour preserved unless matrix
        // mode is explicitly opted in, in which case V08 fires through the validator
        // below). The early-return only kicks in for v1.x no-flavor builds.
        if (flavors.isEmpty() && !MatrixModeResolver.isEnabled(project, extension)) {
            logger.info("[KMP Flavors] No flavors configured. Skipping.")
            return
        }

        logger.lifecycle("[KMP Flavors] Configuring ${flavors.size} flavors across ${dimensions.size} dimensions")

        val buildTypesList = extension.buildTypes.toList()
        val enableBuildTypesFlag = extension.enableBuildTypes.get()

        // Resolve all variants (with filtering). When enableBuildTypes is true and
        // at least one buildType is declared, the matrix expands by buildType axis.
        val allVariants = FlavorVariantResolver.resolveAllVariants(
            dimensions = dimensions,
            flavors = flavors,
            variantFilters = extension.variantFilterActions,
            buildTypes = buildTypesList,
            enableBuildTypes = enableBuildTypesFlag,
            // v2.6 Phase 4 — surface the project's Kotlin target names to the variantFilter
            // so consumers can call `excludeTargets("watchosArm64", ...)`. `android` and
            // `metadata` are filtered out — they're either a separate (AGP) bridge concern
            // or KGP internals.
            availableTargets = kotlin.targets.map { it.name }
                .filterNot { it == "android" || it == "metadata" }.toSet(),
        )
        // Don't early-return on empty allVariants — the validator below needs to see them so
        // V03/V04/V08 surface as structured findings. We still log the warn line for v1.x
        // parity, but defer the actual return until after validation.
        if (allVariants.isEmpty()) {
            logger.warn("[KMP Flavors] No variants resolved. Check dimension assignments or variant filters.")
        }

        // Log filtered variants if any were excluded
        if (allVariants.isNotEmpty()) {
            val baseTotal = if (dimensions.isEmpty()) {
                flavors.size
            } else {
                dimensions.fold(1) { acc, dim ->
                    acc * flavors.count { it.dimension.orNull == dim.name }
                }
            }
            val totalPossible = if (enableBuildTypesFlag && buildTypesList.isNotEmpty()) baseTotal * buildTypesList.size else baseTotal
            if (allVariants.size < totalPossible) {
                logger.lifecycle("[KMP Flavors] Variant filter excluded ${totalPossible - allVariants.size} variants")
            }
        }

        // Capture the requested variant name (-PkmpFlavor or activeFlavor.set(...)) so the
        // validator can surface V06 when the value doesn't match a registered combination.
        val requestedVariantName: String? = project.findProperty("kmpFlavor")?.toString()
            ?: extension.activeFlavor.orNull

        // Determine active variant. When allVariants is empty (V03/V04/V08 will throw
        // shortly via the validator) we leave activeVariant null and unwrap after validation.
        val activeVariant: FlavorVariant? = if (allVariants.isEmpty()) {
            null
        } else {
            resolveActiveVariant(project, extension, dimensions, flavors, allVariants, buildTypesList, enableBuildTypesFlag).also {
                logger.lifecycle("[KMP Flavors] Active variant: ${it.name}")
            }
        }

        // Detect platforms
        val platforms = PlatformDetector.detect(kotlin, logger)
        val createIntermediates = extension.createIntermediateSourceSets.get()

        // v2.0 fail-fast validation (RFC §3 Q23). Run before the matrix-mode
        // registrar so structured errors are surfaced with stable KMPF-Vxx codes
        // instead of opaque downstream stack traces.
        // v2.2 Phase 0A — compute nonAndroidTargets BEFORE the resolver so the auto-
        // heuristic has access to target + flavor counts.
        val nonAndroidTargets = kotlin.targets.filter {
            it.name != "android" && it.name != "metadata"
        }
        val matrixModeEnabled = MatrixModeResolver.isEnabled(
            project = project,
            extension = extension,
            nonAndroidTargetCount = nonAndroidTargets.size,
            flavorCount = flavors.size,
        )
        if (matrixModeEnabled &&
            !extension.buildMatrix.isPresent &&
            project.findProperty(MatrixModeResolver.GRADLE_PROPERTY) == null
        ) {
            logger.lifecycle(
                "[KMP Flavors] Phase 0A — auto-enabling matrix mode because " +
                    "${nonAndroidTargets.size} non-Android target(s) + ${flavors.size} flavor(s) " +
                    "satisfy the heuristic. Set `kmpFlavors.buildMatrix.set(false)` or " +
                    "`kmpFlavors.autoEnable.set(false)` to opt out.",
            )
        }
        // v2.2 Phase 1B — Gradle 9 Project Isolation compatibility audit. No-op on
        // Gradle < 9 or when --project-isolation isn't enabled. Emits KMPF-V13 when
        // the codegen-claim mechanism triggers a cross-project state violation.
        ProjectIsolationCompatChecker.check(project, logger)

        val validationFindings = KmpFlavorPluginValidator.validate(
            flavors = flavors,
            buildTypes = buildTypesList,
            resolvedVariants = allVariants,
            matrixModeEnabled = matrixModeEnabled,
            detectedTargetCount = nonAndroidTargets.size,
            dimensions = dimensions,
            requestedVariantName = requestedVariantName,
            // v2.5 — thread the mutex tracking flags through so KMPF-V24 fires when
            // both DSLs are used in the same kmpFlavors {} block.
            dimensionsDslUsed = extension.dimensionsDslUsed,
            legacyFlatDslUsed = extension.legacyFlatDslUsed,
        )
        // v2.2 Phase 0I + 0L — platform + version compatibility findings (V15/V16/V17).
        val iosTargetNames = nonAndroidTargets.map { it.name }.filter { it.startsWith("ios") }.toSet()
        val kgpVersion = resolveKgpVersion(project)
        val cmpVersion = resolveCmpVersion(project)
        val platformFindings = if (extension.autoEnable.get()) {
            KmpFlavorPluginValidator.validatePlatformAndVersionCompatibility(
                hostOsArch = System.getProperty("os.arch") ?: "unknown",
                gradleVersion = project.gradle.gradleVersion,
                kgpVersion = kgpVersion,
                cmpVersion = cmpVersion,
                declaredIosTargetNames = iosTargetNames,
            )
        } else {
            emptyList()
        }
        // v2.5 Phase 3 — validate the buildKonfig {} DSL block (V26/V27/V28).
        // Returns empty list when no buildKonfig {} declared (default zero state).
        val buildKonfigDsl = extension.buildKonfigDsl
        val manifestSchemaVersion = if (buildKonfigDsl.secrets.isNotEmpty()) {
            com.mobilebytelabs.kmpflavors.internal.BuildKonfigSecretResolver(project.projectDir)
                .manifestSchemaVersion()
        } else {
            null
        }
        // v2.5 Phase 4 follow-up — KMP intermediate source sets (`iosMain`, `nativeMain`,
        // `webMain`, etc.) are NOT in kotlin.sourceSets at config-time when V28 is
        // evaluated; they're created on-demand by KGP's hierarchy template. Derive the
        // family intermediates from declared target names so consumers can reach for
        // idiomatic perTarget("iosMain") without false-positive V28 errors.
        val familyIntermediates = mutableSetOf(
            "commonMain", "commonTest",
            "nativeMain", "nativeTest",
            "webMain", "webTest",
            "appleMain", "appleTest",
            "androidMain", "androidTest",
        )
        kotlin.targets.forEach { target ->
            // ios* → iosMain (e.g. iosArm64 → ios). Strip first uppercase + tail.
            val prefix = target.name.takeWhile { !it.isUpperCase() }
            if (prefix.isNotBlank()) {
                familyIntermediates.add("${prefix}Main")
                familyIntermediates.add("${prefix}Test")
            }
        }
        val buildKonfigFindings = KmpFlavorPluginValidator.validateBuildKonfigDsl(
            buildKonfigSecretIds = buildKonfigDsl.secrets.toList(),
            secretsManifestSchemaVersion = manifestSchemaVersion,
            // customField type validation is best-effort at config time — DSL strings
            // don't reflect type information. V27 mainly fires from codegen path in
            // v2.5.x. Empty here means "no type-level findings detected at config time".
            customFieldUnsupportedTypes = emptyList(),
            perTargetNamesDeclared = buildKonfigDsl.perTargetBlocks.keys.toSet(),
            kotlinTargetNames = kotlin.targets.map { it.name }.toSet() +
                kotlin.sourceSets.map { it.name }.toSet() +
                familyIntermediates,
            // v2.6 Phase 4 inputs — V29 + V30. The baseUrl keys are declared flavor
            // names; registered flavor names are derived from `flavors` (top-level +
            // dimension members); active-flavor per variant is the primary flavor
            // (first dimension's flavor in the variant).
            buildKonfigBaseUrlFlavors = buildKonfigDsl.network.baseUrls.keys,
            registeredFlavorNames = flavors.map { it.name }.toSet(),
            variantActiveFlavors = allVariants.associate { v ->
                v.name to (v.flavorNames.firstOrNull() ?: "")
            },
        )
        val combinedFindings = validationFindings + platformFindings + buildKonfigFindings
        val errors = combinedFindings.filter { it.severity == KmpFlavorValidationSeverity.ERROR }
        val warnings = combinedFindings.filter { it.severity == KmpFlavorValidationSeverity.WARNING }
        warnings.forEach { warning ->
            logger.warn("[KMP Flavors] ${warning.code} — ${warning.message} Fix: ${warning.fix}")
        }
        if (errors.isNotEmpty()) {
            val formatted = errors.joinToString("\n\n") { error ->
                "  ${error.code}: ${error.message}\n  Fix: ${error.fix}"
            }
            throw GradleException(
                "kmpFlavors plugin configuration is invalid (${errors.size} error(s)):\n\n" +
                    "$formatted\n\n" +
                    "See docs/ERROR_CODES.md for the full catalog.",
            )
        }
        // Safe to unwrap — if we had an empty matrix the validator would have thrown.
        // The early-return is defensive: only reachable if a future validator change drops
        // one of V03/V04/V08 without compensating logic upstream.
        val activeVariantResolved: FlavorVariant = activeVariant ?: run {
            logger.info("[KMP Flavors] Empty variant matrix after validation — skipping task wiring.")
            return
        }

        // Wire intermediate source sets if needed
        if (createIntermediates) {
            PlatformDetector.wireIntermediateSourceSets(kotlin, platforms)
        }

        // Resolve platform source sets
        val platformSourceSets = PlatformDetector.resolveSourceSets(kotlin, platforms)

        // Configure flavor source sets (v1.x active-variant model). Runs in
        // BOTH v1.x and v2.0 matrix mode: the active variant always compiles
        // through the standard `main` compilation. Matrix mode adds
        // compilations for the INACTIVE variants on top — the active
        // variant doesn't get a duplicate `compile{Active}Kotlin{Target}`
        // task because that would collide with v1.x's source-set wiring.
        val sourceSetConfigurator = SourceSetConfigurator(logger)
        sourceSetConfigurator.configure(
            project = project,
            kotlin = kotlin,
            activeVariant = activeVariantResolved,
            allFlavors = flavors,
            platforms = platforms,
            platformSourceSets = platformSourceSets,
            createIntermediates = createIntermediates,
            // v2.6 Tier E.1 — matrix mode ON makes inactive source sets safe (they
            // become members of inactive compilations); matrix mode OFF makes them
            // orphans → KGP "Unused" warning unless the flag opts in.
            matrixModeEnabled = matrixModeEnabled,
            createInactiveFlavorSourceSets = extension.createInactiveFlavorSourceSets.get(),
        )

        // v2.2 Phase 1A — intermediate source-set map; populated inside the matrix-mode
        // CompilationRegistrar block and read inside the matrix-mode variant API block.
        // Declared at function scope so both blocks can see it.
        var intermediateSourceSetsByVariant: Map<String, List<org.jetbrains.kotlin.gradle.plugin.KotlinSourceSet>> = emptyMap()

        // v2.0 matrix mode (RFC §3 Q1-Q4): register one KotlinCompilation per
        // (variant × target) across every non-Android target. Source-set wiring,
        // per-variant BuildConfig, and per-variant dependencies land in W2 of the
        // v2.0 impl plan. This W1 hook only stamps the compilations so the task
        // graph surfaces compileFreeDevKotlinDesktop etc. for downstream wiring.
        //
        // RFC §1 non-goal: "Change Android target behaviour (AGP already handles
        // matrix; we don't touch it)." → skip target name "android".
        //
        // The synthetic "metadata" target rejects custom compilations
        // ("Can't create custom metadata compilations by name") — skip it too.
        // Both exclusions are name-based following PlatformDetector's convention.
        if (matrixModeEnabled) {
            // Matrix mode adds compilations for INACTIVE variants only — the
            // active variant continues to compile through the standard `main`
            // compilation wired by v1.x SourceSetConfigurator above. This
            // preserves v1.x semantics for the active variant and avoids the
            // `compile{Active}Kotlin{Target}` naming collision with the
            // existing source-set wiring (KGP rejects sources being in two
            // compilations and dependsOn into a default source set).
            val activeVariantName = activeVariantResolved.name
            val variantNames = allVariants
                .map { it.name }
                .filter { it != activeVariantName }
            // Index variants by name so the per-variant lookup is O(1).
            val variantByName = allVariants.associateBy { it.name }
            // RFC §3 Q2-C hybrid, revised v2.9 — PER-VARIANT common source set.
            //
            // Previously each variant compilation was wired `dependsOn(commonFree)` /
            // `dependsOn(commonProd)` — the SHARED per-flavor source sets that
            // SourceSetConfigurator creates. KGP treats every compilation as its own
            // "Kotlin Source Set Tree", so a single shared node ended up depended on from
            // several trees at once (the active variant's `main` tree plus every matrix
            // variant containing that flavor). KGP flagged each one:
            //
            //   w: ⚠️ Invalid Source Set Dependency Across Trees
            //      Following Kotlin Source Set groups can't depend on 'commonFree' together
            //      as they belong to different Kotlin Source Set Trees.
            //
            // …249 times in a single CI run. The edges also encode a shape KGP explicitly
            // does not support, so it is forward-compat risk, not just noise.
            //
            // Fix: share the flavor DIRECTORIES, never the source-set NODES. Each variant
            // gets its own `common{Variant}` source set carrying the same `src/common<Flavor>/`
            // directories, so every node belongs to exactly one tree.
            //
            // The Q11/Q12 guarantees are preserved:
            //   Q11 (expect/actual) — `expect` still lives in commonMain, `actual` in a
            //     SEPARATE downstream source set (`common{Variant}` or the platform
            //     variant source set), which is what KMP requires.
            //   Q12 (cross-variant isolation) — stronger than before: variants no longer
            //     share any source-set node at all, so a flavor's symbols cannot leak
            //     between variant compilations.
            val commonMainSourceSet = kotlin.sourceSets.getByName("commonMain")
            val parentSourceSetsFor: (String) -> List<org.jetbrains.kotlin.gradle.plugin.KotlinSourceSet> = { name ->
                val variant = variantByName[name]
                if (variant == null) {
                    emptyList()
                } else {
                    val variantCommonName = "common${name.replaceFirstChar { it.uppercase() }}"
                    val variantCommon = kotlin.sourceSets.maybeCreate(variantCommonName)
                    // commonMain is the universal root of every tree, so this edge is
                    // legitimate from any number of trees and KGP does not flag it.
                    variantCommon.dependsOn(commonMainSourceSet)
                    // Share the flavor directories by path. `src/common<Flavor>/{kotlin,resources}`
                    // is the same convention SourceSetConfigurator uses.
                    variant.flavors.forEach { flavor ->
                        val cap = flavor.name.replaceFirstChar { it.uppercase() }
                        variantCommon.kotlin.srcDir("src/common$cap/kotlin")
                        variantCommon.resources.srcDir("src/common$cap/resources")
                    }
                    // Build-type-level shared code, when the variant carries a build type.
                    variant.buildType?.let { bt ->
                        val cap = bt.name.replaceFirstChar { it.uppercase() }
                        variantCommon.kotlin.srcDir("src/common$cap/kotlin")
                        variantCommon.resources.srcDir("src/common$cap/resources")
                    }
                    listOf(variantCommon)
                }
            }
            // Variant-specific srcDir (e.g., `src/freeDev/kotlin`) — only used
            // for code that lives in a single variant and isn't expressible
            // as a per-flavor common source set. Most projects won't need this.
            val variantSpecificSrcDirsFor: (String) -> List<String> = { name ->
                listOf("src/$name/kotlin")
            }
            // v2.6 Phase 4 — build per-variant target-exclusion lookup so the registrar
            // can skip (variant, target) pairs the consumer pruned via
            // `variantFilter { excludeTargets(...) }`.
            val variantTargetExclusions: Map<String, Set<String>> =
                allVariants.filter { it.excludedTargets.isNotEmpty() }
                    .associate { it.name to it.excludedTargets }

            nonAndroidTargets.forEach { target ->
                CompilationRegistrar.register(
                    target = target,
                    variantNames = variantNames,
                    parentSourceSetsFor = parentSourceSetsFor,
                    variantSpecificSrcDirsFor = variantSpecificSrcDirsFor,
                    logger = logger,
                    shouldRegisterVariantOnTarget = { variantName, targetName ->
                        variantTargetExclusions[variantName]?.contains(targetName) != true
                    },
                )
            }
            logger.lifecycle(
                "[KMP Flavors] Matrix mode: registered ${variantNames.size} inactive-variant " +
                    "compilations across ${nonAndroidTargets.size} non-Android target(s) " +
                    "(active variant '$activeVariantName' continues to compile through `main`)",
            )

            // v2.2 Phase 1A — wire cross-variant intermediate source sets when opted in.
            // No-op when createIntermediateBuildTypeSourceSets=false OR no buildTypes registered.
            intermediateSourceSetsByVariant = IntermediateSourceSetConfigurator.configure(
                kotlin = kotlin,
                buildTypes = buildTypesList,
                allVariants = allVariants,
                nonAndroidTargets = nonAndroidTargets,
                enabled = extension.createIntermediateBuildTypeSourceSets.get(),
                logger = logger,
            )

            // v2.1 Phase 2 (Q10) — register `compile{Variant}TestKotlin{Target}` per inactive
            // variant × target. The active variant uses KGP's standard `test` compilation.
            //
            // Per-variant test source-set wiring: for each flavor in the variant, ensure
            // `common{Flavor}Test` exists and `dependsOn(commonTest)`. The registrar then
            // wires the variant test compilation's defaultSourceSet to dependsOn each
            // per-flavor test source set, so per-flavor test helpers + `internal` symbols
            // from the variant main are visible exactly the way KMP expects.
            //
            // Skipped silently when the module declares no `commonTest` (test-less modules).
            val commonTestSourceSet = kotlin.sourceSets.findByName("commonTest")
            if (commonTestSourceSet != null) {
                // v2.9 — per-variant common TEST source set, mirroring the main-tree fix
                // above. Sharing the `common{Flavor}Test` NODE across variant test
                // compilations produced the same cross-tree KGP warning; sharing the
                // DIRECTORY does not.
                val parentTestSourceSetsFor: (String) -> List<org.jetbrains.kotlin.gradle.plugin.KotlinSourceSet> = { name ->
                    val variant = variantByName[name]
                    if (variant == null) {
                        emptyList()
                    } else {
                        val variantCommonTestName = "common${name.replaceFirstChar { it.uppercase() }}Test"
                        val variantCommonTest = kotlin.sourceSets.maybeCreate(variantCommonTestName)
                        variantCommonTest.dependsOn(commonTestSourceSet)
                        variant.flavors.forEach { flavor ->
                            val ssName = "common${flavor.name.replaceFirstChar { it.uppercase() }}Test"
                            variantCommonTest.kotlin.srcDir("src/$ssName/kotlin")
                            variantCommonTest.resources.srcDir("src/$ssName/resources")
                        }
                        variant.buildType?.let { bt ->
                            val ssName = "common${bt.name.replaceFirstChar { it.uppercase() }}Test"
                            variantCommonTest.kotlin.srcDir("src/$ssName/kotlin")
                            variantCommonTest.resources.srcDir("src/$ssName/resources")
                        }
                        listOf(variantCommonTest)
                    }
                }
                val variantSpecificTestSrcDirsFor: (String) -> List<String> = { name ->
                    listOf("src/${name}Test/kotlin")
                }
                nonAndroidTargets.forEach { target ->
                    TestCompilationRegistrar.register(
                        target = target,
                        variantNames = variantNames,
                        parentTestSourceSetsFor = parentTestSourceSetsFor,
                        variantSpecificTestSrcDirsFor = variantSpecificTestSrcDirsFor,
                        logger = logger,
                    )
                }
                logger.lifecycle(
                    "[KMP Flavors] Matrix mode: registered ${variantNames.size} inactive-variant " +
                        "TEST compilations across ${nonAndroidTargets.size} non-Android target(s) " +
                        "(active variant '$activeVariantName' continues to use the standard `test` compilation)",
                )
            } else {
                logger.info(
                    "[KMP Flavors] No `commonTest` source set on this module — skipping per-variant " +
                        "test compilation registration (Q10 no-op for test-less modules).",
                )
            }
        }

        // v2.1 Phase 3A (per-variant Compose resources) — auto-discovery via CMP convention.
        // Only logs a lifecycle line when CMP is applied; no source-set wiring needed because
        // CMP iterates kotlin.sourceSets to apply its composeResources/ convention.
        ComposeResourcesConfigurator.configure(
            project = project,
            kotlin = kotlin,
            allFlavors = flavors,
            matrixModeEnabled = matrixModeEnabled,
            logger = logger,
        )

        // v2.1 Phase 4 — adjacent-plugin helpers (opt-in).
        // Each helper is a no-op unless the consumer flips the corresponding
        // kmpFlavors property AND applies the matching adjacent plugin.
        val nonAndroidTargetNames = nonAndroidTargets.map { it.name }
        DependencyGuardHelper.configure(
            project = project,
            allVariants = allVariants,
            targetNames = nonAndroidTargetNames,
            enabled = extension.dependencyGuardPerVariant.get(),
            logger = logger,
        )
        SpotlessDetektScopeHelper.configure(
            project = project,
            enabled = extension.excludeGeneratedFromFormatters.get(),
            logger = logger,
        )
        DetektPerVariantHelper.configure(
            project = project,
            allVariants = allVariants,
            enabled = extension.detektPerVariant.get(),
            logger = logger,
            // v2.3 Phase 1 — opt-in per-(variant × target) Detekt depth.
            perTarget = extension.detektPerVariantPerTarget.get(),
            nonAndroidTargets = nonAndroidTargets,
        )
        // v2.3 Phase 2 stub — variant-scoped build cache namespacing. Currently
        // a no-op pending real cache-miss telemetry on 8+ variant modules;
        // exposed for forward-compat opt-in. See configurator KDoc.
        VariantBuildCacheKeyConfigurator.configure(
            project = project,
            extension = extension,
        )

        // Q3-A — register one GenerateBuildConfigTask per INACTIVE variant in matrix
        // mode and wire each task's output directory into the corresponding
        // variant compilation's defaultSourceSet. Active variant's BuildConfig
        // is registered by registerTasks() below as `generateFlavorBuildConfig`
        // (v1.x behaviour preserved).
        if (matrixModeEnabled) {
            val inactiveVariants = allVariants.filter { it.name != activeVariantResolved.name }
            GenerateBuildConfigTasksRegistrar.register(
                project = project,
                extension = extension,
                inactiveVariants = inactiveVariants,
                flavors = flavors,
                kotlin = kotlin,
                shouldGenerate = shouldGenerateCodegen(project, extension),
            )

            // v2.6 Phase 3 — DI (Koin) per-variant module codegen. No-op when no
            // `kmpFlavors { di { koin { variantModule(...) { ... } } } }` declared.
            // Wires active variant into each target's `main` compilation + inactive
            // variants into their per-variant compilations.
            com.mobilebytelabs.kmpflavors.internal.PerVariantKoinDiConfigurator.configure(
                project = project,
                extension = extension,
                activeVariant = activeVariantResolved,
                inactiveVariants = inactiveVariants,
                kotlin = kotlin,
            )

            // v2.6 Phase 3 — cross-platform analytics tags codegen. No-op when
            // `kmpFlavors { analytics { enabled.set(false) } }` (default).
            com.mobilebytelabs.kmpflavors.internal.PerVariantAnalyticsTagConfigurator.configure(
                project = project,
                extension = extension,
                activeVariant = activeVariantResolved,
                inactiveVariants = inactiveVariants,
                kotlin = kotlin,
            )

            // v2.5 Phase 3 — register FrameworkSchemaCheckTask when buildKonfig { secret(...) }
            // is declared. Provides an artifact + WARN log for KMPF-V26 (graceful-degrade
            // when secrets-manifest.yaml is schema v2.0). Bound as a dependency of every
            // generate*BuildConfig task so it runs before codegen.
            val bkSecrets = extension.buildKonfigDsl.secrets.toList()
            if (bkSecrets.isNotEmpty()) {
                val schemaCheck = project.tasks.register(
                    "checkSecretsManifestSchema",
                    com.mobilebytelabs.kmpflavors.tasks.FrameworkSchemaCheckTask::class.java,
                ) {
                    val manifest = project.file("secrets-manifest.yaml")
                    if (manifest.exists()) {
                        this.secretsManifestFile.set(manifest)
                    }
                    this.declaredSecretIds.set(bkSecrets)
                    this.outputMarker.set(
                        project.layout.buildDirectory.file("generated/kmpFlavors/secrets-schema-check.txt"),
                    )
                }
                project.tasks
                    .matching { it.name.startsWith("generate") && it.name.endsWith("BuildConfig") }
                    .configureEach { dependsOn(schemaCheck) }
            }
        }

        // Q19-B — populate the public `kmpFlavors.variants` container with one
        // KmpFlavorVariant per resolved variant. Consumers can then use
        // `kmpFlavors.variants.matching { ... }.configureEach { ... }` to
        // customise variants. Lazy fields (`targets`, `compilations`) are
        // populated below after the variant compilations are resolvable.
        if (matrixModeEnabled) {
            allVariants.forEach { v ->
                // Pre-configure the variant instance outside the container, then
                // add it. This guarantees `flavors`/`buildType` are populated
                // BEFORE any consumer-registered configureEach callback fires.
                // (Empirically, NamedDomainObjectContainer.create(name, action)
                // fires configureEach BEFORE the action runs for this container
                // — surfaced by VariantApiTest in W3.2.)
                if (extension.variants.findByName(v.name) == null) {
                    val pv = project.objects.newInstance(KmpFlavorVariant::class.java, v.name)
                    pv.flavors = v.flavors.map { it.name }
                    pv.buildType = v.buildType?.name
                    extension.variants.add(pv)
                }
            }
            // Resolve target + compilation references for the inactive variants
            // (the active variant compiles through `main`, so its `compilations`
            // map is intentionally empty — consumers asking about the active
            // variant's compilation should query `target.compilations.main`).
            val inactiveVariantNames = allVariants.map { it.name }.toSet() - activeVariantResolved.name
            val resolvedTargets = nonAndroidTargets.toSet()
            // configureEach { } SAM-converts to KmpFlavorVariant.() -> Unit (receiver-style),
            // not (KmpFlavorVariant) -> Unit. So use `this` rather than a named param.
            extension.variants.configureEach {
                if (name in inactiveVariantNames) {
                    targets = resolvedTargets
                    compilations = resolvedTargets.associateWith { target ->
                        @Suppress("UNCHECKED_CAST")
                        (target.compilations as org.gradle.api.NamedDomainObjectContainer<org.jetbrains.kotlin.gradle.plugin.KotlinCompilation<*>>)
                            .findByName(name)
                            ?: target.compilations.getByName("main")
                    }
                    // v2.2 Phase 1A — expose intermediate source sets per variant.
                    intermediateSourceSets = intermediateSourceSetsByVariant[name].orEmpty()
                }
            }
        }

        // Q18-C — register aggregate `assembleAll{Target}Variants` per target +
        // super-aggregate `assembleAllVariants` walking every target × variant.
        if (matrixModeEnabled) {
            val inactiveVariants = allVariants.filter { it.name != activeVariantResolved.name }
            AggregateTasksRegistrar.register(
                project = project,
                nonAndroidTargets = nonAndroidTargets,
                activeVariant = activeVariantResolved,
                inactiveVariants = inactiveVariants,
            )
        }

        // Q21-D — per-variant Maven publishing mechanism. No-op when
        // publishMatrix isn't opted in or when maven-publish isn't applied.
        // v2.0 ships JVM; v2.1 Phase 5 extends to iOS + JS/WasmJs.
        if (matrixModeEnabled) {
            val inactiveVariants = allVariants.filter { it.name != activeVariantResolved.name }
            PerVariantPublishConfigurator.configure(
                project = project,
                extension = extension,
                inactiveVariants = inactiveVariants,
                nonAndroidTargets = nonAndroidTargets,
            )
            // v2.1 Phase 5A — iOS Zip + MavenPublication path (klib bundle). v2.2 keeps this
            // behind `publishMatrixLegacyIosClassifiers` (default `true` for migration window)
            // alongside Phase 5A's XCFramework path. Consumers depending on v2.1 classifier
            // coordinates can leave the flag on; ready-to-migrate consumers set it to `false`.
            if (extension.publishMatrixLegacyIosClassifiers.get()) {
                PerVariantIosPublishConfigurator.configure(
                    project = project,
                    extension = extension,
                    inactiveVariants = inactiveVariants,
                    nonAndroidTargets = nonAndroidTargets,
                )
            }
            // v2.2 Phase 5A — XCFramework + MavenPublication path (real Apple framework binaries).
            // No-op when no iOS targets declared OR maven-publish not applied.
            PerVariantIosXcframeworkConfigurator.configure(
                project = project,
                extension = extension,
                inactiveVariants = inactiveVariants,
                nonAndroidTargets = nonAndroidTargets,
            )
            // v2.1 Phase 5B+5C — JS/WasmJs per-variant publishing (classifier-tagged
            // MavenPublication per (inactive variant × JS-family target); npm registry
            // publishing is consumer-side — see docs/PUBLISHING.md).
            PerVariantJsPublishConfigurator.configure(
                project = project,
                extension = extension,
                inactiveVariants = inactiveVariants,
                nonAndroidTargets = nonAndroidTargets,
            )
            // v2.2 Phase 5C — opt-in per-variant npm tarballs. No-op when npmPublishMatrix=false
            // OR no js/wasmJs targets declared.
            PerVariantNpmPublishConfigurator.configure(
                project = project,
                extension = extension,
                inactiveVariants = inactiveVariants,
                nonAndroidTargets = nonAndroidTargets,
            )
            // v2.2 Phase 3B — per-variant SBOM artifacts attached to each MavenPublication.
            // No-op when publishMatrixSbom=false OR org.cyclonedx.bom not applied.
            PerVariantSbomConfigurator.configure(
                project = project,
                extension = extension,
                inactiveVariants = inactiveVariants,
                logger = logger,
            )
            // v2.3 Phase 7 — opt-in per-variant Compose hot-reload (Option A).
            // No-op when composeHotReloadPerVariant=false OR org.jetbrains.compose
            // not applied OR no JVM-family targets declared. Option B (daemon-
            // restart-free switcher) is deferred to v2.4 pending CMP-internal
            // classloader API surface stabilisation — see configurator KDoc.
            PerVariantComposeHotReloadConfigurator.configure(
                project = project,
                extension = extension,
                inactiveVariants = inactiveVariants,
                nonAndroidTargets = nonAndroidTargets,
            )
        }

        // Configure dependencies
        val dependencyConfigurator = DependencyConfigurator(logger)
        dependencyConfigurator.configure(project, activeVariantResolved)
        // v2.4 Phase 5 — apply per-variant `dependencies { exclude(...) }` registrations
        // collected via `kmpFlavors.variants.matching { … }.configureEach { … }`.
        // No-op when no variant registered an exclude (common case for consumers who
        // don't use the Phase 5 DSL).
        project.afterEvaluate {
            dependencyConfigurator.applyVariantExcludes(extension.variants)
        }

        // AGP bridge — propagate KMP flavors / build types into the Android
        // Gradle Plugin extension when consumer opts in via bridgeAgp* flags.
        // v1.1.0 scope: com.android.application only.
        AgpBridge.apply(
            project = project,
            bridgeProductFlavors = extension.bridgeAgpProductFlavors.get(),
            bridgeBuildTypes = extension.bridgeAgpBuildTypes.get(),
            kmpDimensions = dimensions,
            kmpFlavors = flavors,
            kmpBuildTypes = extension.buildTypes.toList(),
            logger = logger,
            // v2.6 Phase 2 — KMP↔AGP variantFilter parity. The post-filter KMP variant
            // names define the canonical set; AgpBridge registers a beforeVariants
            // callback that disables AGP variants whose names are not in this set.
            allowedVariantNames = allVariants.map { it.name }.toSet(),
        )

        // SPM manifest generator — ON BY DEFAULT since v2.9 (SPM is the default iOS
        // framework-distribution path; CocoaPods is opt-in and never the default).
        // Gated on an actual iOS target so flipping the default adds ZERO tasks to
        // Android-only / desktop-only / JVM consumers.
        // v2.2 Phase 5B: when matrix mode is ALSO on, register one SPM task per variant
        // (build/spm/{variantName}/Package.swift). v1.x consumers without matrix mode get
        // the single-variant SPM manifest unchanged.
        val hasIosTarget = nonAndroidTargets.any {
            it.name in com.mobilebytelabs.kmpflavors.internal.SpmXcframeworkResolver.IOS_TARGET_NAMES
        }
        if (extension.spm.generateManifest.get() && hasIosTarget) {
            if (matrixModeEnabled) {
                for (variant in allVariants) {
                    registerSpmTaskForVariant(project, extension, variant)
                }
                logger.lifecycle(
                    "[KMP Flavors] Phase 5B — registered ${allVariants.size} per-variant SPM " +
                        "manifest task(s) at `build/spm/{variant}/Package.swift`.",
                )
            } else {
                registerSpmTask(project, extension, activeVariantResolved)
            }

            // v2.9 — the Xcode half of end-to-end SPM. A Package.swift with no script to
            // assemble + stage the binary it references is only half an integration.
            if (extension.spm.generateEmbedScript.get()) {
                registerSpmEmbedScriptTask(project, extension, allVariants)
            }
        }

        // Configure platform-specific properties
        val platformPropertiesConfigurator = PlatformPropertiesConfigurator(logger)
        platformPropertiesConfigurator.configure(project, activeVariantResolved)

        // Q22/Q13 — register diagnostic tasks (diagnoseVariant + listVariantCompilations).
        // Always-on (not gated by matrix mode) so they remain useful for v1.x consumers too.
        registerDiagnosticTasks(
            project = project,
            extension = extension,
            kotlin = kotlin,
            allVariants = allVariants,
            activeVariant = activeVariantResolved,
            nonAndroidTargets = nonAndroidTargets,
            matrixModeEnabled = matrixModeEnabled,
        )

        // v2.2 Phase 4A — variant promotion tasks. No-op when no promotions declared.
        VariantPromotionConfigurator.configure(
            project = project,
            flavors = flavors,
            promotions = extension.variantPromotions,
            logger = logger,
        )

        // v2.2 Phase 4B — feature-flag generator (GrowthBook / Statsig / LaunchDarkly).
        // No-op when no platform sub-config has defaultPayload set, OR matrix mode is off.
        FeatureFlagHelpers.configure(
            project = project,
            extension = extension,
            allVariants = allVariants,
            matrixModeEnabled = matrixModeEnabled,
            logger = logger,
        )

        // v2.2 Phase 3A — per-variant Build Scan tagging. No-op when Develocity not applied.
        BuildScanConfigurator.configure(
            project = project,
            allVariants = allVariants,
            nonAndroidTargets = nonAndroidTargets,
            matrixModeEnabled = matrixModeEnabled,
            logger = logger,
        )

        // Register tasks
        registerTasks(project, extension, allVariants, activeVariantResolved, flavors, dimensions, platforms)

        // v2.1 Phase 4 — per-variant × target IDE run configurations. Registered here
        // (not in registerTasks) because we need `nonAndroidTargets` to derive the
        // exact KMP target names (jvm("desktop") + jvm("server") → two separate
        // entries, not collapsed under a single "jvm" platform prefix).
        project.tasks.register(
            "generateVariantRunConfigurations",
            GenerateVariantRunConfigurationsTask::class.java,
        ).configure {
            projectName.set(project.name)
            projectPath.set(project.path)
            variantNames.set(allVariants.map { it.name })
            targetNames.set(nonAndroidTargetNames)
            activeVariantName.set(activeVariantResolved.name)
            outputDirectory.set(project.rootProject.layout.projectDirectory.dir(".run"))
        }

        // Wire build config generation to compilation if enabled.
        // Multi-module-safe: in a build with multiple subprojects applying this
        // plugin under the same buildConfigPackage+ClassName, only the FIRST
        // subproject to apply (in Gradle's configuration order) generates the
        // class. Subsequent applications skip silently to prevent DEX-merge
        // duplicate-class collisions. See shouldGenerateCodegen() below.
        if (shouldGenerateCodegen(project, extension)) {
            wireGenerateBuildConfigToCompilation(
                project = project,
                kotlin = kotlin,
                matrixModeEnabled = matrixModeEnabled,
                nonAndroidTargets = nonAndroidTargets,
            )
        }
    }

    /**
     * Decide whether this project should generate FlavorConfig codegen.
     *
     * In a multi-module Gradle build where many subprojects apply this plugin
     * with the same `buildConfigPackage` + `buildConfigClassName`, every
     * subproject generating the class would produce duplicate class files
     * that collide at DEX merge time (Android) or at compile time (KMP).
     *
     * The first subproject to apply the plugin under a given
     * "package.ClassName" key claims codegen via a rootProject extra
     * property. Subsequent subprojects observe the existing claim and
     * silently skip codegen — they still get source-set wiring, variant
     * registration, and AGP bridging from this plugin; they just won't
     * emit a competing FlavorConfig.
     *
     * Consumers that want a specific module to be the codegen host can
     * force it to be the first applier (e.g. by adjusting include order
     * in settings.gradle.kts or by applying the plugin to that module
     * before any other).
     */
    private fun shouldGenerateCodegen(project: Project, extension: KmpFlavorExtension): Boolean {
        if (!extension.generateBuildConfig.get()) return false

        // Explicit per-module override via kmpFlavors { codegenHost.set(false) }.
        // Module declares it will NOT generate codegen even if it would have
        // claimed under the auto mechanism below.
        val explicitHost = extension.codegenHost.orNull
        if (explicitHost == false) {
            project.logger.info(
                "[KMP Flavors] ${project.path} skipping codegen — codegenHost = false",
            )
            return false
        }

        val pkg = extension.buildConfigPackage.orNull
        if (pkg.isNullOrBlank()) return true // No conflict possible without a package.
        val cls = extension.buildConfigClassName.orNull ?: "FlavorConfig"
        val key = "kmpFlavors.codegenClaim:$pkg.$cls"
        val rootExtras = project.rootProject.extensions.extraProperties
        val existing = if (rootExtras.has(key)) rootExtras.get(key) as? String else null

        // Explicit host always wins, even over an earlier non-explicit claim:
        // override the claim if codegenHost.set(true).
        if (explicitHost == true) {
            if (existing != null && existing != project.path) {
                project.logger.info(
                    "[KMP Flavors] ${project.path} taking over codegen claim from $existing (codegenHost = true)",
                )
            }
            rootExtras.set(key, project.path)
            return true
        }

        if (existing == null) {
            rootExtras.set(key, project.path)
            project.logger.info(
                "[KMP Flavors] ${project.path} claimed FlavorConfig codegen for $pkg.$cls (auto)",
            )
            return true
        }
        if (existing == project.path) return true
        // v2.2 Phase 0J — deterministic election: when multiple projects compete for
        // the same codegen claim, the lexicographically-lower `project.path` always wins.
        // v1.1.5's "first applier wins" was deterministic per build but non-obvious; the
        // lex-comparison makes the winner predictable across builds AND under parallel
        // configuration (Gradle 8.5+ with `org.gradle.parallel=true` may interleave
        // shouldGenerateCodegen calls). Explicit `codegenHost.set(true)` still wins
        // (handled in the explicitHost branch above).
        if (project.path < existing) {
            project.logger.info(
                "[KMP Flavors] ${project.path} taking over codegen claim from $existing " +
                    "(Phase 0J deterministic election — '${project.path}' < '$existing')",
            )
            rootExtras.set(key, project.path)
            return true
        }
        project.logger.lifecycle(
            "[KMP Flavors] ${project.path} skipping FlavorConfig codegen — already generated by $existing",
        )
        return false
    }

    /**
     * Q22 + Q13 — diagnostic tasks. Captures the variant × target compilation
     * matrix and the per-variant source-set closure at configuration time so
     * the tasks remain configuration-cache-friendly.
     */
    private fun registerDiagnosticTasks(
        project: Project,
        extension: KmpFlavorExtension,
        kotlin: KotlinMultiplatformExtension,
        allVariants: List<FlavorVariant>,
        activeVariant: FlavorVariant,
        nonAndroidTargets: List<org.jetbrains.kotlin.gradle.plugin.KotlinTarget>,
        matrixModeEnabled: Boolean,
    ) {
        if (allVariants.isEmpty()) return

        // Variant -> list of (target, registered-compilation-name) entries.
        val compilationByVariantTargetData = mutableMapOf<String, String>()
        val targetsByVariantData = mutableMapOf<String, MutableList<String>>()
        val sourceSetsByVariantData = mutableMapOf<String, MutableList<String>>()

        for (variant in allVariants) {
            val targetsForVariant = mutableListOf<String>()
            val sourceSetsForVariant = mutableSetOf<String>()
            for (target in nonAndroidTargets) {
                // Active variant compiles through `main`; inactive variants (in matrix mode)
                // have their own compilation named after the variant.
                val compilationName = if (variant.name == activeVariant.name) "main" else variant.name
                val compilation = target.compilations.findByName(compilationName)
                    ?: if (variant.name == activeVariant.name) {
                        target.compilations.findByName("main")
                    } else {
                        null
                    }
                if (compilation != null) {
                    compilationByVariantTargetData["${variant.name}::${target.name}"] = compilation.name
                    targetsForVariant += target.name
                    // BFS the dependsOn closure starting from defaultSourceSet.
                    val visited = mutableSetOf<String>()
                    val frontier = ArrayDeque<org.jetbrains.kotlin.gradle.plugin.KotlinSourceSet>()
                    frontier.addLast(compilation.defaultSourceSet)
                    while (frontier.isNotEmpty()) {
                        val ss = frontier.removeFirst()
                        if (visited.add(ss.name)) {
                            ss.dependsOn.forEach { frontier.addLast(it) }
                        }
                    }
                    sourceSetsForVariant += visited
                }
            }
            targetsByVariantData[variant.name] = targetsForVariant.distinct().sorted().toMutableList()
            sourceSetsByVariantData[variant.name] = sourceSetsForVariant.sorted().toMutableList()
        }

        val flavorsByVariantData = allVariants.associate { v -> v.name to v.flavorNames }
        val buildTypeByVariantData = allVariants.associate { v -> v.name to (v.buildType?.name ?: "") }
        val buildConfigFieldsByVariantData = allVariants.associate { v ->
            v.name to v.mergedBuildConfigFields.mapValues { (_, field) ->
                "${field.type}::${field.value}"
            }
        }
        val targetNames = nonAndroidTargets.map { it.name }

        project.tasks.register(
            "diagnoseVariant",
            DiagnoseVariantTask::class.java,
        ).configure {
            flavorsByVariant.set(flavorsByVariantData)
            buildTypeByVariant.set(buildTypeByVariantData)
            sourceSetsByVariant.set(sourceSetsByVariantData.mapValues { it.value.toList() })
            targetsByVariant.set(targetsByVariantData.mapValues { it.value.toList() })
            buildConfigFieldsByVariant.set(buildConfigFieldsByVariantData)
            activeVariantName.set(activeVariant.name)
            variantFilterCount.set(extension.variantFilterActions.size)
        }

        project.tasks.register(
            "listVariantCompilations",
            ListVariantCompilationsTask::class.java,
        ).configure {
            allVariantNames.set(allVariants.map { it.name })
            allTargetNames.set(targetNames)
            compilationByVariantTarget.set(compilationByVariantTargetData)
            activeVariantName.set(activeVariant.name)
        }

        if (!matrixModeEnabled) {
            project.logger.info(
                "[KMP Flavors] Matrix mode is off — diagnoseVariant / listVariantCompilations " +
                    "will report only the active variant's `main` compilation per target.",
            )
        }
    }

    private fun resolveActiveVariant(
        project: Project,
        extension: KmpFlavorExtension,
        dimensions: List<FlavorDimension>,
        flavors: List<FlavorConfig>,
        allVariants: List<FlavorVariant>,
        buildTypes: List<BuildTypeConfig>,
        enableBuildTypes: Boolean,
    ): FlavorVariant {
        // Priority: 1) Gradle property, 2) Extension property, 3) Default variant
        val gradleProperty = project.findProperty("kmpFlavor")?.toString()
        val extensionProperty = extension.activeFlavor.orNull

        val activeName = gradleProperty ?: extensionProperty

        return if (activeName != null) {
            val resolved = FlavorVariantResolver.resolveVariantByName(activeName, allVariants)
            if (resolved != null) {
                resolved
            } else {
                // V06 (unknown active variant) is surfaced by the validator as a WARNING-level
                // finding before this point — see KmpFlavorPluginValidator.validate(). Here we
                // just soft-fall to the default variant so the build can continue.
                FlavorVariantResolver.resolveDefaultVariant(dimensions, flavors, buildTypes, enableBuildTypes)
                    ?: allVariants.first()
            }
        } else {
            FlavorVariantResolver.resolveDefaultVariant(dimensions, flavors, buildTypes, enableBuildTypes)
                ?: allVariants.first()
        }
    }

    private fun registerTasks(
        project: Project,
        extension: KmpFlavorExtension,
        allVariants: List<FlavorVariant>,
        activeVariantResolved: FlavorVariant,
        flavors: List<FlavorConfig>,
        dimensions: List<FlavorDimension>,
        platforms: List<com.mobilebytelabs.kmpflavors.internal.PlatformGroup>,
    ) {
        // Generate BuildConfig task — gated by claim (see shouldGenerateCodegen).
        if (shouldGenerateCodegen(project, extension)) {
            project.tasks.register(
                "generateFlavorBuildConfig",
                GenerateBuildConfigTask::class.java,
            ) {
                packageName.set(extension.buildConfigPackage)
                className.set(extension.buildConfigClassName)
                variantName.set(activeVariantResolved.name)
                allFlavorNames.set(flavors.map { it.name }.toSet())
                activeFlavorNames.set(activeVariantResolved.flavorNames.toSet())
                allBuildTypeNames.set(extension.buildTypes.map { it.name }.toSet())
                activeBuildTypeName.set(activeVariantResolved.buildType?.name ?: "")
                buildConfigFields.set(activeVariantResolved.mergedBuildConfigFields)
                // v2.6 Phase 4 — wire network constants for the active variant too. The
                // codegen task uses activeFlavorNames to pick the right baseUrl mapping.
                val bkNetwork = extension.buildKonfigDsl.network
                if (bkNetwork.baseUrls.isNotEmpty()) {
                    networkConfigSpec.set(
                        com.mobilebytelabs.kmpflavors.NetworkConfigSpec(
                            baseUrls = bkNetwork.baseUrls.toMap(),
                            timeoutSeconds = bkNetwork.timeoutSeconds,
                        ),
                    )
                }
                outputDirectory.set(
                    project.layout.buildDirectory.dir("generated/kmpFlavors/commonMain/kotlin"),
                )
            }
        }

        // Validate flavors task
        project.tasks.register(
            "validateFlavors",
            ValidateFlavorsTask::class.java,
        ) {
            dimensionNames.set(dimensions.map { it.name }.toSet())
            flavorDimensions.set(flavors.associate { it.name to (it.dimension.orNull ?: "") })
            flavorDefaults.set(flavors.associate { it.name to it.isDefault.getOrElse(false) })
            validVariantNames.set(allVariants.map { it.name }.toSet())
            activeVariantName.set(activeVariantResolved.name)
            allFlavorNames.set(flavors.map { it.name })
        }

        // List flavors task
        val variantsData = allVariants.associate { it.name to it.flavorNames }
        val activeVariantNameValue = activeVariantResolved.name
        val dimensionsData = dimensions.associate { it.name to it.priority.getOrElse(0) }
        val platformsData = platforms.filter { !it.isIntermediate }.map { it.prefix }

        project.tasks.register("listFlavors", ListFlavorsTask::class.java).configure {
            this.variants.set(variantsData)
            this.activeVariant.set(activeVariantNameValue)
            this.dimensions.set(dimensionsData)
            this.platforms.set(platformsData)
        }

        // v2.2 Phase 2A (Option B) — listActiveVariant task. CLI helper for the
        // documented "Compose hot-reload still active-only" UX.
        project.tasks.register(
            "listActiveVariant",
            ListActiveVariantTask::class.java,
        ).configure {
            activeVariantName.set(activeVariantNameValue)
            allVariantNames.set(allVariants.map { it.name })
        }

        // v2.4 Phase 3 — switchVariantAndReload task. Best-effort Option B
        // workaround until CMP exposes a public hot-reload reset API. Wires
        // even when CMP isn't applied — the task body itself is a no-op-ish
        // helper that prints next steps; consumers can dispatch it from any
        // matrix-mode project.
        project.tasks.register(
            "switchVariantAndReload",
            SwitchVariantAndReloadTask::class.java,
        ).configure {
            knownVariants.set(allVariants.map { it.name })
        }

        // Generate run configurations task
        project.tasks.register(
            "generateRunConfigurations",
            GenerateRunConfigurationsTask::class.java,
        ).configure {
            projectName.set(project.name)
            projectPath.set(project.path)
            variants.set(variantsData)
            this.activeVariant.set(activeVariantNameValue)
            outputDirectory.set(project.rootProject.layout.projectDirectory.dir(".run"))
        }

        // Init flavor source sets task
        val leafPlatformPrefixes = platforms.filter { !it.isIntermediate }.map { it.prefix }.toSet()
        val intermediatePrefixes = platforms.filter { it.isIntermediate }.map { it.prefix }.toSet()

        project.tasks.register(
            "kmpFlavorInit",
            InitFlavorSourceSetsTask::class.java,
        ).configure {
            flavorNames.set(flavors.map { it.name }.toSet())
            platformPrefixes.set(leafPlatformPrefixes)
            this.intermediatePrefixes.set(intermediatePrefixes)
            createIntermediates.set(extension.createIntermediateSourceSets)
            createGitKeep.set(true)
            createExampleFiles.set(false)
            createReadmePerSourceSet.set(true)
            examplePackage.set(extension.buildConfigPackage)
            // v2.2 Phase 0K — wire sample-code-generation inputs.
            generateSampleCode.set(extension.autoEnable)
            buildConfigClassName.set(extension.buildConfigClassName)
            sourceDirectory.set(project.layout.projectDirectory.dir("src"))
        }

        // Print flavor properties task
        project.tasks.register(
            "printFlavorProperties",
            PrintFlavorPropertiesTask::class.java,
        ).configure {
            variantName.set(activeVariantResolved.name)
            applicationIdSuffix.set(
                activeVariantResolved.combinedApplicationIdSuffix.ifEmpty { null },
            )
            bundleIdSuffix.set(
                activeVariantResolved.combinedBundleIdSuffix.ifEmpty { null },
            )
            versionNameSuffix.set(
                activeVariantResolved.combinedVersionNameSuffix.ifEmpty { null },
            )
            desktopTitleSuffix.set(
                activeVariantResolved.combinedDesktopTitleSuffix.ifEmpty { null },
            )
            webTitleSuffix.set(
                activeVariantResolved.combinedWebTitleSuffix.ifEmpty { null },
            )
        }
    }

    /**
     * v2.9 — registers `generateSpmEmbedScript`, which emits the flavor-aware Xcode
     * Run-Script phase body. The `{flavor}{BuildType}` → `NativeBuildType` mapping is taken
     * from the DSL (each build type's `isDebuggable`), reproducing exactly what the Kotlin
     * CocoaPods plugin's `xcodeConfigurationToNativeBuildType[...]` block used to do.
     */
    private fun registerSpmEmbedScriptTask(project: org.gradle.api.Project, extension: KmpFlavorExtension, allVariants: List<FlavorVariant>) {
        // Xcode configuration names ARE the variant names produced by the flavor matrix.
        // Debuggability comes from the declared build type, NOT from a name glob — a
        // build type called `staging` with isDebuggable = true must select the Debug slice.
        val mapping: Map<String, String> = allVariants.associate { variant ->
            val debuggable = variant.buildType?.isDebuggable?.getOrElse(false) ?: false
            variant.name to if (debuggable) "Debug" else "Release"
        }

        // Derive the iOS project dir from the configured pbxproj path
        // ("../cmp-ios/iosApp.xcodeproj/project.pbxproj" → "cmp-ios").
        val pbxPath = extension.iosPbxprojPath.getOrElse("../cmp-ios/iosApp.xcodeproj/project.pbxproj")
        val iosDirName = pbxPath.removePrefix("../").substringBefore('/').ifBlank { "cmp-ios" }
        val scriptPath = extension.spm.embedScriptPath
            .getOrElse("$iosDirName/scripts/embed-xcframework.sh")

        project.tasks.register(
            "generateSpmEmbedScript",
            com.mobilebytelabs.kmpflavors.tasks.GenerateSpmEmbedScriptTask::class.java,
        ).configure {
            xcframeworkName.set(extension.spm.xcframeworkName)
            sharedModulePath.set(project.path)
            iosProjectDirName.set(iosDirName)
            configurationToBuildType.set(mapping)
            outputFile.set(project.rootProject.layout.projectDirectory.file(scriptPath))
            rootDirPath.set(project.rootProject.layout.projectDirectory)
        }
    }

    /**
     * v2.2 Phase 5B — per-variant SPM manifest registration. Mirrors `registerSpmTask` but
     * produces one `generate{Variant}SpmManifest` task per variant, writing to
     * `build/spm/{variant}/Package.swift`.
     */
    private fun registerSpmTaskForVariant(project: org.gradle.api.Project, extension: KmpFlavorExtension, variant: FlavorVariant) {
        val flavorName = variant.flavors.firstOrNull()?.name ?: variant.name
        val variantCap = variant.name.replaceFirstChar { it.uppercase() }
        project.tasks.register(
            "generate${variantCap}SpmManifest",
            GenerateSpmManifestTask::class.java,
        ).configure {
            group = "kmpFlavors variants"
            description = "Generate per-variant Package.swift manifest for SPM distribution " +
                "of variant '${variant.name}' (Phase 5B)."
            variantName.set(variant.name)
            this.flavorName.set(flavorName)
            xcframeworkName.set(extension.spm.xcframeworkName)
            distribution.set(extension.spm.distribution)
            binaryUrlTemplate.set(extension.spm.binaryUrlTemplate)
            xcframeworkPath.set(extension.spm.xcframeworkPath)
            projectVersion.set(project.provider { project.version.toString() })
            checksumStrategy.set(extension.spm.checksumStrategy)
            outputDirectory.set(project.layout.buildDirectory.dir("spm/${variant.name}"))
            rootDirPath.set(project.rootProject.layout.projectDirectory)
        }
        wireXcframeworkProducer(project, extension, variant, "generate${variantCap}SpmManifest")
    }

    /**
     * v2.9 — connect a generated manifest to the task that actually BUILDS the XCFramework
     * it references, so `Package.swift` can never point at a binary nothing produces.
     *
     * Resolution is deferred to `afterEvaluate` because the consumer's `XCFramework()` DSL
     * (and this plugin's own per-variant publishing tasks) register their producers during
     * the consumer's own configuration phase.
     */
    private fun wireXcframeworkProducer(project: org.gradle.api.Project, extension: KmpFlavorExtension, variant: FlavorVariant, manifestTaskName: String) {
        project.afterEvaluate {
            val name = extension.spm.xcframeworkName.get()
            // Kotlin build type (debug/release), NOT the flavor's build-type name —
            // KGP names both its producer tasks and its output buckets by native type.
            val debuggable = variant.buildType?.isDebuggable?.getOrElse(false) ?: false
            val buildType = com.mobilebytelabs.kmpflavors.internal.SpmXcframeworkResolver
                .nativeBuildTypeFor(debuggable)
            val explicit = extension.spm.xcframeworkTask.orNull
            val producer = explicit
                ?: com.mobilebytelabs.kmpflavors.internal.SpmXcframeworkResolver.resolveProducer(
                    xcframeworkName = name,
                    variantName = variant.name,
                    buildTypeName = buildType,
                    existingTaskNames = project.tasks.names,
                )

            if (explicit != null && !project.tasks.names.contains(explicit)) {
                // Explicit user intent pointing at a task that does not exist is a
                // configuration ERROR — fail loudly, this can only be a typo.
                throw org.gradle.api.InvalidUserDataException(
                    "[KMP Flavors] spm.xcframeworkTask is set to '$explicit' but no such task " +
                        "exists in project '${project.path}'.",
                )
            }

            if (producer == null) {
                // No producer in this build. REMOTE distribution is fine (the binary comes
                // from a CDN), and `requireXcframework = false` is an explicit opt-out.
                // Otherwise SKIP generation rather than emitting a dangling manifest — and
                // rather than failing the build, because SPM is ON BY DEFAULT since v2.9 and
                // plenty of iOS-targeted modules legitimately publish klibs and never
                // aggregate an XCFramework. Breaking those on upgrade would be unacceptable.
                val remote = extension.spm.distribution.get() == SpmDistribution.REMOTE
                if (remote || !extension.spm.requireXcframework.get()) {
                    return@afterEvaluate
                }
                project.tasks.named(manifestTaskName).configure {
                    onlyIf { false }
                }
                project.logger.warn(
                    com.mobilebytelabs.kmpflavors.internal.SpmXcframeworkResolver
                        .missingProducerMessage(name, variant.name),
                )
                return@afterEvaluate
            }

            project.tasks.named(manifestTaskName).configure {
                dependsOn(producer)
            }
            // Point the manifest at the binary the resolved producer emits, unless the
            // consumer pinned an explicit path.
            if (!extension.spm.xcframeworkPath.isPresent) {
                val relative = com.mobilebytelabs.kmpflavors.internal.SpmXcframeworkResolver
                    .conventionalOutputPath(name, buildType)
                project.tasks.named(manifestTaskName, GenerateSpmManifestTask::class.java).configure {
                    xcframeworkPath.set(relative)
                }
            }
            project.logger.info(
                "[KMP Flavors] SPM manifest '$manifestTaskName' now depends on producer '$producer'.",
            )
        }
    }

    private fun registerSpmTask(project: org.gradle.api.Project, extension: KmpFlavorExtension, activeVariantResolved: FlavorVariant) {
        // Resolve the active flavor's name. activeVariantResolved.flavors holds the list of
        // FlavorConfig objects forming this variant; we use the first dimension's flavor
        // for {flavor} interpolation, matching the convention applicationIdSuffix uses.
        val flavorName = activeVariantResolved.flavors.firstOrNull()?.name ?: activeVariantResolved.name

        project.tasks.register(
            "generateSpmManifest",
            GenerateSpmManifestTask::class.java,
        ).configure {
            group = "kmp flavors"
            description = "Generate Package.swift manifest for SPM distribution of the active flavor variant"
            variantName.set(activeVariantResolved.name)
            this.flavorName.set(flavorName)
            xcframeworkName.set(extension.spm.xcframeworkName)
            distribution.set(extension.spm.distribution)
            binaryUrlTemplate.set(extension.spm.binaryUrlTemplate)
            xcframeworkPath.set(extension.spm.xcframeworkPath)
            projectVersion.set(project.provider { project.version.toString() })
            checksumStrategy.set(extension.spm.checksumStrategy)
            outputDirectory.set(project.layout.buildDirectory.dir("spm/${activeVariantResolved.name}"))
            rootDirPath.set(project.rootProject.layout.projectDirectory)
        }

        wireXcframeworkProducer(project, extension, activeVariantResolved, "generateSpmManifest")

        // Hook into :assemble so generation happens automatically alongside builds.
        project.tasks.matching { it.name == "assemble" }.configureEach {
            dependsOn("generateSpmManifest")
        }
    }

    /**
     * v2.2 Phase 0L — reflective KGP version read from the buildscript classpath.
     * Returns null when KGP isn't resolvable. Used by `validatePlatformAndVersionCompatibility`
     * for V17 (KGP × Gradle compat).
     */
    private fun resolveKgpVersion(project: Project): String? {
        for (cfg in project.buildscript.configurations) {
            if (!cfg.isCanBeResolved) continue
            try {
                for (dep in cfg.resolvedConfiguration.firstLevelModuleDependencies) {
                    if (dep.moduleGroup == "org.jetbrains.kotlin" &&
                        dep.moduleName == "kotlin-gradle-plugin"
                    ) {
                        return dep.moduleVersion
                    }
                }
            } catch (e: Exception) {
                // Best-effort.
            }
        }
        return null
    }

    /**
     * v2.2 Phase 0L — reflective Compose Multiplatform version read. Returns null when
     * CMP isn't applied. Used by `ComposeResourcesConfigurator` (V14) AND by
     * `validatePlatformAndVersionCompatibility` (V16, CMP × KGP compat).
     */
    private fun resolveCmpVersion(project: Project): String? {
        for (cfg in project.buildscript.configurations) {
            if (!cfg.isCanBeResolved) continue
            try {
                for (dep in cfg.resolvedConfiguration.firstLevelModuleDependencies) {
                    if (dep.moduleGroup == "org.jetbrains.compose" &&
                        (dep.moduleName == "compose-gradle-plugin" || dep.moduleName.endsWith(".gradle.plugin"))
                    ) {
                        return dep.moduleVersion
                    }
                }
            } catch (e: Exception) {
                // Best-effort.
            }
        }
        return null
    }

    private fun wireGenerateBuildConfigToCompilation(
        project: Project,
        kotlin: KotlinMultiplatformExtension,
        matrixModeEnabled: Boolean,
        nonAndroidTargets: List<org.jetbrains.kotlin.gradle.plugin.KotlinTarget>,
    ) {
        val generateTask = project.tasks.named(
            "generateFlavorBuildConfig",
            GenerateBuildConfigTask::class.java,
        )
        val outputDirProvider = generateTask.flatMap { it.outputDirectory }

        if (matrixModeEnabled) {
            // Matrix mode: wire the active-variant BuildKonfig into each
            // non-Android target's `main` compilation's defaultSourceSet
            // (e.g., `desktopMain`). Wiring into commonMain would cause
            // inactive-variant compilations to inherit the active-variant
            // BuildKonfig via the source-set hierarchy AND have their own
            // per-variant BuildKonfig — Kotlin rejects this with
            // "Redeclaration" at the variant compile.
            for (target in nonAndroidTargets) {
                val mainCompilation = target.compilations.findByName("main") ?: continue
                mainCompilation.defaultSourceSet.kotlin.srcDir(outputDirProvider)
            }
        } else {
            // v1.x active-only mode: wire into commonMain (existing behaviour).
            kotlin.sourceSets.getByName("commonMain").kotlin.srcDir(outputDirProvider)
        }

        // Make Kotlin compilation depend on generation
        project.tasks.matching { it.name.startsWith("compileKotlin") }.configureEach {
            dependsOn(generateTask)
        }
    }
}
