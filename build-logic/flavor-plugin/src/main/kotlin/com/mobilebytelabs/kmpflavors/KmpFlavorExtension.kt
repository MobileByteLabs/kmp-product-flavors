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

import org.gradle.api.Action
import org.gradle.api.NamedDomainObjectContainer
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import javax.inject.Inject

/**
 * Main extension for configuring KMP Product Flavors.
 *
 * This extension is registered as `kmpFlavors` in the build script and provides
 * the DSL for configuring product flavors in a Kotlin Multiplatform project.
 *
 * Example usage:
 * ```kotlin
 * kmpFlavors {
 *     // Generate BuildConfig object
 *     generateBuildConfig.set(true)
 *     buildConfigPackage.set("com.example.app")
 *     buildConfigClassName.set("AppConfig")
 *
 *     // Active flavor (can be overridden via -PkmpFlavor=xxx)
 *     activeFlavor.set("freeDev")
 *
 *     // Create intermediate source sets (webMain, nativeMain)
 *     createIntermediateSourceSets.set(true)
 *
 *     // Define dimensions
 *     flavorDimensions {
 *         register("tier") { priority.set(0) }
 *         register("environment") { priority.set(1) }
 *     }
 *
 *     // Define flavors
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
 *
 *     // Filter out invalid variant combinations
 *     variantFilter {
 *         if (flavorNames.containsAll(listOf("free", "prod"))) {
 *             exclude()
 *         }
 *     }
 * }
 * ```
 */
abstract class KmpFlavorExtension @Inject constructor(objects: ObjectFactory) {
    /**
     * Whether to generate a BuildConfig Kotlin object.
     * When true, a Kotlin object will be generated in commonMain with
     * variant information and custom build config fields.
     *
     * Convention: true
     */
    abstract val generateBuildConfig: Property<Boolean>

    /**
     * Explicit override for multi-module codegen.
     *
     * When MORE than one module applies this plugin under the same
     * `buildConfigPackage + buildConfigClassName`, the plugin needs to choose
     * exactly one module to actually generate the class — otherwise downstream
     * builds hit duplicate-class errors at DEX merge or compilation.
     *
     * - Default (unset / null): claim mechanism — first module configured wins.
     *   This is non-deterministic across builds, so prefer explicit `set(true)`
     *   in a designated host module (e.g. `cmp-shared`).
     * - `set(true)`: this module MUST generate the class. Subsequent claimants
     *   that aren't host log an info skip.
     * - `set(false)`: this module MUST NOT generate the class (e.g. a library
     *   module that consumes the host's output transitively).
     */
    abstract val codegenHost: Property<Boolean>

    /**
     * The package name for the generated BuildConfig object.
     * Required when [generateBuildConfig] is true.
     *
     * Example: "com.example.app"
     */
    abstract val buildConfigPackage: Property<String>

    /**
     * The class name for the generated BuildConfig object.
     *
     * Convention: "BuildKonfig"
     */
    abstract val buildConfigClassName: Property<String>

    /**
     * The active flavor/variant name.
     * Can be overridden via the Gradle property `-PkmpFlavor=xxx`.
     *
     * If not set, defaults to the variant formed by default flavors
     * from each dimension, or the first flavor if no defaults are set.
     */
    abstract val activeFlavor: Property<String>

    /**
     * Whether to create intermediate source sets like webMain and nativeMain.
     * These provide shared code between related platforms:
     * - webMain: shared between js and wasmJs
     * - nativeMain: shared between iOS, macOS, Linux, Windows
     *
     * Convention: true
     */
    abstract val createIntermediateSourceSets: Property<Boolean>

    /**
     * Container for flavor dimensions.
     * Dimensions define independent axes of variation.
     */
    val flavorDimensions: NamedDomainObjectContainer<FlavorDimension> =
        objects.domainObjectContainer(FlavorDimension::class.java)

    /**
     * Container for flavor configurations.
     * Each flavor belongs to a dimension.
     */
    val flavors: NamedDomainObjectContainer<FlavorConfig> =
        objects.domainObjectContainer(FlavorConfig::class.java)

    /**
     * Container for build type configurations.
     * Build types define different build configurations (e.g., debug, release).
     * When build types are configured, variants become combinations of flavors + build type.
     */
    val buildTypes: NamedDomainObjectContainer<BuildTypeConfig> =
        objects.domainObjectContainer(BuildTypeConfig::class.java)

    /**
     * v2.0 public variant API (RFC §3 Q19-B). One [KmpFlavorVariant]
     * element is populated per matrix-mode variant after
     * `CompilationRegistrar` runs. Consumers customise variants via
     * standard Gradle container mechanics:
     *
     * ```kotlin
     * kmpFlavors.variants.matching { it.flavors.contains("paid") }
     *     .configureEach { /* per-variant customisation */ }
     * ```
     *
     * Only populated when `buildMatrix.set(true)` AND the project has at
     * least one flavor. Reads in `configureEach { }` are safe; eager
     * `forEach` before `afterEvaluate` returns an empty collection.
     */
    val variants: NamedDomainObjectContainer<KmpFlavorVariant> =
        objects.domainObjectContainer(KmpFlavorVariant::class.java)

    /**
     * Whether to enable build types support.
     * When true, variants will include the build type suffix (e.g., freeDebug, paidRelease).
     *
     * Convention: false (build types are disabled by default for simplicity)
     */
    abstract val enableBuildTypes: Property<Boolean>

    /**
     * v2.0 matrix mode opt-in. When `true`, the plugin registers one
     * `KotlinCompilation` per (variant × target) across every detected
     * KMP non-Android target, so the full variant matrix builds in one
     * Gradle invocation (AGP-style). When `false` (default), the plugin
     * preserves v1.x behaviour: only the active variant's source set is
     * wired into the existing `main` compilation per target.
     *
     * Single-point opt-in (RFC §3 Q16-C hybrid):
     * - `gradle.properties: kmpFlavors.buildMatrix=true` — project-wide default.
     * - `kmpFlavors { buildMatrix.set(true) }` in the convention plugin —
     *   per-project override of the property. Extension wins on conflict.
     *
     * Per the Zero-Touch Adoption Design Tenet (RFC §1.1), no per-module
     * `build.gradle.kts` ever sets this. The convention plugin or the
     * root `gradle.properties` is the only consumer touch-point.
     *
     * Convention: not set (so `isPresent == false`). The plugin's
     * `MatrixModeResolver` treats unset extension + missing property as
     * `false` (v1.x behaviour). Explicit `set(false)` opts a project out
     * of an enabling gradle.properties override.
     */
    abstract val buildMatrix: Property<Boolean>

    /**
     * v2.0 per-variant Maven publishing opt-in (RFC §3 Q21-D).
     *
     * When `true` AND the consumer's project applies `maven-publish` (or
     * `com.vanniktech.maven.publish`), the plugin registers one
     * [org.gradle.api.publish.maven.MavenPublication] per inactive variant
     * × non-Android target, with a Maven classifier equal to the variant
     * name. Consumer libraries can then ship classifier-tagged variant
     * artifacts (e.g. `coordinate:1.0.0:paid`) without touching their
     * per-module DSL.
     *
     * The plugin itself stays single-published (Q21-A behaviour for
     * kmp-product-flavors's own artifacts). Q21-D ships the *mechanism*
     * so consumer libraries opt in.
     *
     * Convention: not set (so `isPresent == false`). The plugin treats
     * unset extension + missing property as `false` — no per-variant
     * publications are registered.
     */
    abstract val publishMatrix: Property<Boolean>

    /**
     * Whether to propagate KMP flavor dimensions and product flavors into the
     * Android Gradle Plugin (AGP) extension on modules that apply
     * `com.android.application`.
     *
     * When `true` and the consumer module applies `com.android.application`:
     * - KMP `flavorDimensions {}` are appended to AGP `flavorDimensions += [...]`.
     * - KMP `flavors {}` are registered as AGP product flavors with matching
     *   dimension, `applicationIdSuffix`, and `versionNameSuffix`.
     * - If AGP `productFlavors {}` is already populated by hand, the bridge
     *   logs a warning and skips propagation rather than silently overriding.
     *
     * In v1.1.0 the bridge supports `com.android.application` only. Library
     * (`com.android.library`) and KMP-library (`com.android.kotlin.multiplatform.library`)
     * support is planned for v1.2.0.
     *
     * Convention: false
     */
    abstract val bridgeAgpProductFlavors: Property<Boolean>

    /**
     * Whether to propagate KMP build types into AGP `buildTypes {}` on modules
     * that apply `com.android.application`.
     *
     * When `true` and the consumer module applies `com.android.application`:
     * - KMP `buildTypes {}` are registered as AGP build types with `isDebuggable`,
     *   `isMinifyEnabled`, and `applicationIdSuffix` carried over.
     * - The default AGP `debug` and `release` build types are preserved (the
     *   bridge calls `maybeCreate`).
     * - If AGP `buildTypes {}` already declares custom flavor-style content
     *   beyond `debug`/`release`, the bridge logs a warning and skips the
     *   propagation rather than overriding.
     *
     * Convention: false
     */
    abstract val bridgeAgpBuildTypes: Property<Boolean>

    /**
     * v2.1 Phase 4 — per-variant dependency-guard baselines.
     *
     * When `true` AND the consumer applies `com.dropbox.dependency-guard`,
     * the plugin auto-registers one `dependencyGuard { configuration(...) }`
     * entry per (variant × target). Closes Q24's documented limitation
     * around manually listing per-variant compileClasspath configurations.
     *
     * Convention: false (opt-in).
     */
    abstract val dependencyGuardPerVariant: Property<Boolean>

    /**
     * v2.1 Phase 4 — auto-exclude generated `BuildKonfig` directories
     * from Spotless and Detekt globs.
     *
     * When `true`, the plugin adds an `exclude` for the generated path
     * entry to every Spotless task and every Detekt task. Closes Q24's
     * documented "watch source-set scope" caveat.
     *
     * No-op when neither Spotless nor Detekt is applied.
     *
     * Convention: false (opt-in).
     */
    abstract val excludeGeneratedFromFormatters: Property<Boolean>

    /**
     * v2.2 Phase 0 — master opt-out for every auto-detection in Phase 0.
     *
     * When `true` (default in v2.2+), the plugin auto-enables several
     * features based on detected conditions:
     *   - `buildMatrix` auto-fires when >=2 non-Android targets + >=2 flavors.
     *   - `publishMatrix` auto-fires when `maven-publish` is applied + matrix on.
     *   - `dependencyGuardPerVariant` / `excludeGeneratedFromFormatters` /
     *     `detektPerVariant` auto-fire when their adjacent plugins are detected.
     *   - `enableBuildTypes` auto-flips to `true` on first `buildTypes { register(...) }`.
     *
     * Consumers who want strict v2.0 / v2.1 explicit-opt-in semantics flip this
     * to `false`. Individual auto-detections can also be overridden via the
     * corresponding explicit `set(false)` on their property — but the master
     * switch is the simplest path.
     *
     * Convention: true (in v2.2+).
     */
    abstract val autoEnable: Property<Boolean>

    /**
     * v2.1 Phase 4 — per-variant Detekt analysis.
     *
     * When `true` AND the consumer applies `io.gitlab.arturbosch.detekt`,
     * the plugin registers one `detekt{Variant}` task per variant whose
     * source scope is the variant's source-set hierarchy and whose
     * baseline resolves to `config/detekt/{variant}/baseline.xml`.
     *
     * Equivalent UX to AGP's "Lint per variant" — the most-requested
     * adjacent-plugin gap on the v2.0 alpha feedback.
     *
     * Convention: false (opt-in).
     */
    abstract val detektPerVariant: Property<Boolean>

    /**
     * v2.2 Phase 1A — opt-in for cross-variant intermediate `common{BuildType}` source sets.
     *
     * When `true` (AND `enableBuildTypes = true` AND matrix mode is on), the plugin
     * creates `common{BuildType}` source sets (e.g. `commonStaging`) that
     * `dependsOn(commonMain)`. Every variant whose `buildType` matches
     * (e.g. `freeStaging` + `paidStaging`) gains a `dependsOn(commonStaging)` edge
     * on its compilation's defaultSourceSet — so symbols declared in `commonStaging`
     * are visible to ALL staging-flavored variants but not to `freeProd` / `paidProd`.
     *
     * Closes RFC §10 deferral (cross-variant intermediate source sets). Disabled by
     * default because the source-set DAG change is opinionated; consumers opt in.
     *
     * Convention: `false` (opt-in).
     */
    abstract val createIntermediateBuildTypeSourceSets: Property<Boolean>

    /**
     * Internal list of variant filter actions.
     */
    internal val variantFilterActions = mutableListOf<Action<VariantFilter>>()

    /**
     * Configure flavor dimensions using a DSL block.
     */
    fun flavorDimensions(action: Action<NamedDomainObjectContainer<FlavorDimension>>) {
        action.execute(flavorDimensions)
    }

    /**
     * Configure flavors using a DSL block.
     */
    fun flavors(action: Action<NamedDomainObjectContainer<FlavorConfig>>) {
        action.execute(flavors)
    }

    /**
     * Filter variant combinations to exclude invalid or unwanted variants.
     *
     * This allows you to exclude specific variant combinations from the build.
     * The filter action is called for each variant during resolution.
     *
     * Example:
     * ```kotlin
     * variantFilter {
     *     // Exclude free + prod combination
     *     if (flavorNames.containsAll(listOf("free", "prod"))) {
     *         exclude()
     *     }
     * }
     * ```
     *
     * @param action The filter action to execute for each variant
     */
    fun variantFilter(action: Action<VariantFilter>) {
        variantFilterActions.add(action)
    }

    /**
     * Configure build types using a DSL block.
     *
     * Example:
     * ```kotlin
     * buildTypes {
     *     register("debug") {
     *         isDefault.set(true)
     *         isDebuggable.set(true)
     *         buildConfigField("Boolean", "DEBUG", "true")
     *     }
     *     register("release") {
     *         isMinifyEnabled.set(true)
     *         buildConfigField("Boolean", "DEBUG", "false")
     *     }
     * }
     * ```
     */
    fun buildTypes(action: Action<NamedDomainObjectContainer<BuildTypeConfig>>) {
        action.execute(buildTypes)
    }

    /**
     * SPM (Swift Package Manager) iOS framework distribution configuration.
     * See [SpmConfig] for available properties.
     *
     * CocoaPods is intentionally **not** supported — JetBrains has deprecated CocoaPods
     * in the KMP roadmap. Use SPM via [spm] for iOS framework distribution.
     */
    val spm: SpmConfig = objects.newInstance(SpmConfig::class.java)

    /**
     * Configure the SPM manifest generator using a DSL block.
     */
    fun spm(action: Action<SpmConfig>) {
        action.execute(spm)
    }

    init {
        // Set conventions
        generateBuildConfig.convention(true)
        buildConfigClassName.convention("BuildKonfig")
        createIntermediateSourceSets.convention(true)
        enableBuildTypes.convention(false)
        // NOTE: deliberately NO `buildMatrix.convention(false)` — the resolver
        // relies on `extension.buildMatrix.isPresent` returning `false` when the
        // consumer didn't explicitly opt in, so it can fall back to the Gradle
        // property `kmpFlavors.buildMatrix`. Setting a convention here would
        // short-circuit that fallback. See `MatrixModeResolver` for details.
        // Safe defaults: the AGP bridge is idempotent in v1.1.5+. It detects already-
        // registered product flavors / build types (e.g. consumer convention plugins that
        // call configureFlavors() synchronously inside a withPlugin callback) and skips
        // silently. Consumers no longer need to manually toggle these flags.
        bridgeAgpProductFlavors.convention(true)
        bridgeAgpBuildTypes.convention(true)
        // Phase-4 helpers default to off (opt-in). Consumers wire them via:
        //   kmpFlavors { dependencyGuardPerVariant.set(true) }
        //   kmpFlavors { excludeGeneratedFromFormatters.set(true) }
        //   kmpFlavors { detektPerVariant.set(true) }
        // Phase 4 helpers default to off (opt-in). v2.2 Phase 0C auto-flips them to
        // true via withPlugin callbacks when their adjacent plugin is detected.
        dependencyGuardPerVariant.convention(false)
        excludeGeneratedFromFormatters.convention(false)
        detektPerVariant.convention(false)
        // v2.2 Phase 0G — master opt-out. Default `true` for fully-automatic-plugin
        // ergonomics; consumers wanting strict v2.0 / v2.1 semantics set to `false`.
        autoEnable.convention(true)
        // v2.2 Phase 1A — cross-variant intermediate source sets, opt-in.
        createIntermediateBuildTypeSourceSets.convention(false)
    }
}
