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

import com.mobilebytelabs.kmpflavors.annotations.KmpFlavorsExperimental
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
     * The app's base application/bundle id (without any flavor suffix), e.g.
     * "com.example.app". Consumed by the generated `KmpFlavorsRuntime.bundleId`
     * (with the active flavor's id suffix appended). Optional — when unset,
     * `KmpFlavorsRuntime.bundleId` is left empty rather than guessed from the
     * module name.
     */
    abstract val appId: Property<String>

    /**
     * The app's human-facing display name, e.g. "Money Toolkit". Consumed by the
     * generated `KmpFlavorsRuntime.appDisplayName`. Optional — when unset, that
     * field is left empty rather than guessed from the root project name.
     */
    abstract val appDisplayName: Property<String>

    // ──────────────────────────────────────────────────────────────────────────
    // iOS xcconfig + manifest generation (opt-in; see docs/REFERENCE.md).
    //
    // When [iosXcconfigGeneration] is true the plugin registers, on the module
    // that has a sibling iOS project, a `generateIosFlavorXcconfigs` task that
    // writes one self-contained `Configs/{variant}.xcconfig` per flavor×buildType
    // and a `kmpFlavorsBootstrapXcode` task that points each Xcode flavor build
    // configuration's base config at its own per-variant xcconfig (NOT a broken
    // `$(CONFIGURATION)` umbrella include). `exportKmpFlavorsManifest` (variants.json)
    // is registered when [iosManifestExport] is true.
    // ──────────────────────────────────────────────────────────────────────────

    /** Master switch for the per-variant iOS xcconfig generation + pbxproj wiring. Default: false. */
    abstract val iosXcconfigGeneration: Property<Boolean>

    /** Register the `exportKmpFlavorsManifest` (variants.json) task. Default: false. */
    abstract val iosManifestExport: Property<Boolean>

    /**
     * Relative path (from the registering module) to the iOS project's `Configs/`
     * directory where per-variant xcconfigs are written. Default: `"../cmp-ios/Configs"`.
     */
    abstract val iosConfigsDir: Property<String>

    /**
     * Relative path (from the registering module) to `project.pbxproj`.
     * Default: `"../cmp-ios/iosApp.xcodeproj/project.pbxproj"`.
     */
    abstract val iosPbxprojPath: Property<String>

    /**
     * Bundle-id base expression written into each per-variant xcconfig, before the
     * flavor's `bundleIdSuffix`. Empty → use the resolved [appId] (self-contained).
     * A consumer keeping identity in an Xcode config sets an expr like `"$(APP_BUNDLE_ID)"`.
     */
    abstract val iosBundleIdBaseExpr: Property<String>

    /**
     * `DEVELOPMENT_TEAM` value written into each per-variant xcconfig. Empty → the
     * line is omitted. A consumer keeping the team in an Xcode config sets `"$(TEAM_ID)"`.
     */
    abstract val iosDevelopmentTeamExpr: Property<String>

    /**
     * Optional identity xcconfig each per-variant file `#include`s at the top (relative
     * to the `Configs/` dir), e.g. `"../Configuration/Config.xcconfig"`. Makes each
     * per-variant file self-contained so repointing any build config at it is safe.
     * Empty → no identity include.
     */
    abstract val iosIdentityInclude: Property<String>

    /**
     * Emit a per-configuration CocoaPods `#include?` in each per-variant xcconfig
     * (`../Pods/Target Support Files/Pods-iosApp/Pods-iosApp.{lowercasevariant}.xcconfig`)
     * so Pods link/framework settings flow in despite the custom base config. Default: false.
     */
    abstract val iosCocoapodsIntegration: Property<Boolean>

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
     * v2.6 Tier E.1 — controls creation of inactive-flavor source sets when their
     * on-disk content exists but matrix mode is OFF.
     *
     * **Default `false`** — when an inactive flavor has files in
     * `src/common<Flavor>/kotlin/` or `src/<platform><Flavor>/kotlin/` but matrix
     * mode is OFF and the flavor isn't the active one, the plugin silently SKIPS
     * source set creation (and logs an informational WARN telling the consumer
     * their code is currently unreachable). This eliminates KGP's
     * "Unused Kotlin Source Sets" warning by structural avoidance — the source
     * set never gets registered, so KGP never sees an orphan.
     *
     * Set to `true` to restore the v2.5 behaviour (lazy-create when content
     * exists, accept the KGP warning as the cost of keeping IDE editing alive
     * for inactive flavor code).
     *
     * Background: Hypothesis A (`commonProd.dependsOn(commonMain)`) was tried in
     * v2.5.0-alpha.2 and disproved — KGP's check is compilation-membership-based,
     * not `dependsOn`-graph-based. See `docs/SOURCE_SET_DISCIPLINE.md`.
     *
     * Convention: false
     */
    abstract val createInactiveFlavorSourceSets: Property<Boolean>

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
     * v2.8 — Container for per-flavor signing configs. Reflectively bridged into
     * `android.signingConfigs {}` by [com.mobilebytelabs.kmpflavors.internal.SigningConfigBridge]
     * during the AGP propagation phase. See [SigningConfig] for the DSL shape.
     *
     * No-op when the consumer module doesn't apply `com.android.application` /
     * `com.android.library` — non-Android targets ignore signing.
     */
    val signingConfigs: NamedDomainObjectContainer<SigningConfig> =
        objects.domainObjectContainer(SigningConfig::class.java)

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
     * v2.3 Phase 1 — opt-in for per-(variant × target) Detekt depth.
     *
     * Extends [detektPerVariant]: instead of one `detekt{Variant}` task per
     * inactive variant, registers `detekt{Variant}{Target}` (e.g.
     * `detektFreeDevDesktop`, `detektFreeDevIosArm64`) so per-target rule
     * overrides + per-target baselines are first-class.
     *
     * Motivation: a `commonPaid` symbol that compiles cleanly on Desktop may
     * fail Detekt on iOS because some Detekt rules depend on the target's
     * stdlib. v2.1's per-variant scope mixes target-specific signals into one
     * task, blunting the signal-to-noise.
     *
     * Per-target baseline path: `config/detekt/{variant}/{target}/baseline.xml`.
     * Variant-level aggregate tasks (`detekt{Variant}`) continue to exist +
     * depend on the per-target subtasks when this flag is on.
     *
     * Requires `detektPerVariant=true`. No-op when `detektPerVariant=false`.
     *
     * Convention: `false` (opt-in — narrows the v2.1 scope; consumers who don't
     * want N×M tasks stay on the per-variant default).
     */
    @KmpFlavorsExperimental("Needs Phase 1 sample smoke (multi-target Detekt scope) before Stable promotion")
    abstract val detektPerVariantPerTarget: Property<Boolean>

    /**
     * v2.3 Phase 2 — opt-in for variant-scoped Gradle build cache namespacing.
     *
     * **Status: stub (ships as no-op pending telemetry).** The v2.3 plan's Phase 2
     * acceptance gate splits two paths:
     *   - (a) v2.2 / v2.3 Build Scan data shows acceptable cache-hit rates on
     *     8+ variant modules → ship this property + a no-op
     *     `VariantBuildCacheKeyConfigurator`, documented as such.
     *   - (b) Real cache-miss data → ship the implementation + flip the
     *     convention to `true`.
     *
     * This v2.3 cycle ships path (a). When `true`, the configurator hooks the
     * `compileKotlin*` tasks' cache-key inputs to inject variant-scoped
     * namespacing — but the implementation is currently a documented no-op
     * pending real cache-miss data. The property is exposed now so consumers
     * who want to forward-declare opt-in for future v2.3.x / v2.4 releases
     * can do so without a DSL break.
     *
     * Symptom that would unblock the path-(b) implementation: a 50-variant
     * module on a multi-target KMP project pushing the cache key space past
     * Gradle's default 10K-entry limit, causing eviction → cache-miss
     * explosion in Build Scan reports.
     *
     * Convention: `false` (opt-in stub).
     */
    @KmpFlavorsExperimental("Needs real cache-hit telemetry on 8+ variant modules to justify default-flip")
    abstract val variantCacheNamespacing: Property<Boolean>

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
    @KmpFlavorsExperimental("RFC §10 closer; KGP cross-tree dep warnings cosmetic but persistent")
    abstract val createIntermediateBuildTypeSourceSets: Property<Boolean>

    /**
     * v2.2 Phase 3B — per-variant SBOM (Software Bill of Materials) emission.
     *
     * When `true` AND `publishMatrix` is on AND the consumer applies
     * `org.cyclonedx.bom`, the plugin attaches a CycloneDX SBOM artifact
     * (`coordinate:1.0.0:<variant>-sbom`) to every per-variant MavenPublication.
     * Consumers' supply-chain tooling (Snyk, Dependabot, GitHub Dependency Graph)
     * can then audit per-variant dependency graphs separately.
     *
     * Convention: `false` (opt-in).
     */
    abstract val publishMatrixSbom: Property<Boolean>

    /**
     * v2.2 Phase 5A — keep v2.1's Zip-shaped iOS MavenPublications as deprecation
     * aliases when XCFramework path lands. Default `true` in v2.2 (migration window);
     * flip to `false` once consumers have migrated their dependency coordinates from
     * `coordinate:1.0.0:paid-iosArm64` (Zip) to `coordinate:1.0.0:paid-xcframework`
     * (XCFramework). Removed in a future major.
     *
     * Convention: `true` (preserves v2.1 behavior on v2.2 upgrade).
     */
    abstract val publishMatrixLegacyIosClassifiers: Property<Boolean>

    /**
     * v2.2 Phase 5C — opt-in for plugin-side npm publishing of JS / WasmJs variants.
     *
     * When `true` AND `publishMatrix=true` AND a `js(IR)` or `wasmJs()` target is declared,
     * the plugin generates a per-variant npm package with `package.json.name = "{prefix}-{variant}"`.
     * npm credentials remain consumer-side (`~/.npmrc`); the plugin doesn't manage them.
     *
     * Convention: `false` (opt-in — consumer-side npm tarball production is the v2.1 default).
     */
    @KmpFlavorsExperimental("Minimal real-world npm publish testing; needs adopter signal")
    abstract val npmPublishMatrix: Property<Boolean>

    /**
     * v2.2 Phase 5C — npm package-name prefix override. Used as `package.json.name = "{prefix}-{variant}"`.
     * When unset, defaults to `rootProject.name`.
     */
    abstract val npmPackagePrefix: Property<String>

    /**
     * v2.3 Phase 7 — opt-in per-variant Compose hot-reload task registration.
     *
     * When `true` AND `org.jetbrains.compose` is applied AND matrix mode is enabled,
     * registers a `composeHotReload{Variant}` task per inactive variant (Option A from
     * the v2.3 plan). Each task is wired to the variant's compilation so editing a
     * variant-specific source file triggers a hot-reload scoped to that variant.
     *
     * **Limitations** (Phase 7A research outcome):
     * - Switching the active variant still requires Gradle daemon restart on most
     *   CMP versions (Option B requires CMP-internal classloader changes — deferred).
     * - The CMP hot-reload subsystem is unstable across CMP versions; v2.3 supports
     *   CMP 1.7-1.9. Outside that matrix, KMPF-V18 surfaces a WARNING.
     *
     * Convention: `false` (opt-in — Option B's CLI helper + IDE plugin variant
     * switcher remain the v2.3 default UX).
     */
    @KmpFlavorsExperimental("Option A only; Option B graduation gated on CMP reset API (issue #75)")
    abstract val composeHotReloadPerVariant: Property<Boolean>

    /**
     * Internal list of variant filter actions.
     */
    internal val variantFilterActions = mutableListOf<Action<VariantFilter>>()

    /**
     * v2.2 Phase 4A — list of variant promotions declared via `kmpFlavors.promote(...)`.
     * `KmpFlavorPlugin` reads this at configurePlugin() time and hands it to
     * `VariantPromotionConfigurator` which registers a `promote{From}To{To}` task per entry.
     */
    internal val variantPromotions = mutableListOf<VariantPromotion>()

    /**
     * Configure flavor dimensions using a DSL block.
     *
     * Setting this block marks the legacy flat DSL as used (see [legacyFlatDslUsed]) —
     * mutually exclusive with [dimensions] at validation time (`KMPF-V24`).
     */
    fun flavorDimensions(action: Action<NamedDomainObjectContainer<FlavorDimension>>) {
        markLegacyFlatDslUsed()
        action.execute(flavorDimensions)
    }

    /**
     * Configure flavors using a DSL block.
     *
     * Setting this block marks the legacy flat DSL as used (see [legacyFlatDslUsed]) —
     * mutually exclusive with [dimensions] at validation time (`KMPF-V24`).
     */
    fun flavors(action: Action<NamedDomainObjectContainer<FlavorConfig>>) {
        markLegacyFlatDslUsed()
        action.execute(flavors)
    }

    // ─────────────────────────────────────────────────────────────────────
    // v2.5 — `dimensions { dimension("tier") { flavor("free") } }` ergonomic
    // DSL sugar. See DimensionsDsl.kt for full behavior + KMPF-V24 mutex.
    // ─────────────────────────────────────────────────────────────────────

    /**
     * v2.5 — mutex tracking flag. Flipped to `true` the first time
     * [dimensions] is called. Read by `KmpFlavorPlugin.kt` and threaded into
     * [KmpFlavorPluginValidator.validate] for `KMPF-V24` enforcement.
     */
    @get:org.gradle.api.tasks.Internal
    var dimensionsDslUsed: Boolean = false
        internal set

    /**
     * v2.5 — mutex tracking flag for the legacy flat DSL. Flipped to `true`
     * the first time [flavorDimensions] or [flavors] is called. Pair with
     * [dimensionsDslUsed] — both `true` simultaneously fires `KMPF-V24`.
     */
    @get:org.gradle.api.tasks.Internal
    var legacyFlatDslUsed: Boolean = false
        internal set

    internal fun markDimensionsDslUsed() {
        dimensionsDslUsed = true
    }

    internal fun markLegacyFlatDslUsed() {
        legacyFlatDslUsed = true
    }

    /**
     * v2.5 ergonomic sugar for declaring flavor dimensions + their member flavors in one
     * tree-shaped block. Equivalent to `flavorDimensions { } + flavors { }` pair under the
     * hood — populates the same containers. Mutually exclusive with the legacy flat DSL
     * (mixing both in the same `kmpFlavors {}` fires `KMPF-V24` ERROR).
     *
     * Example:
     * ```kotlin
     * kmpFlavors {
     *     dimensions {
     *         dimension("tier") {
     *             flavor("free")
     *             flavor("paid")
     *         }
     *         dimension("env") {
     *             flavor("dev")
     *             flavor("prod")
     *         }
     *     }
     * }
     * ```
     *
     * See [DimensionsDsl] for the full pattern + migration guidance.
     */
    fun dimensions(action: Action<DimensionsDsl>) {
        // Lazy-construct: avoid allocating the DSL object when consumers stick with the
        // flat DSL. DimensionsDsl has an `internal` constructor so consumers can't bypass
        // the markDimensionsDslUsed() side effect by constructing it themselves.
        val dsl = DimensionsDsl(this)
        action.execute(dsl)
    }

    // ─────────────────────────────────────────────────────────────────────
    // v2.5 — `buildKonfig { secret(); enum(); customField(); perTarget {} }`
    // codegen-expansion DSL block. See BuildKonfigDsl.kt for the four entry
    // points + their codegen semantics.
    // ─────────────────────────────────────────────────────────────────────

    /**
     * v2.5 — accumulated BuildKonfig DSL state across all `buildKonfig {}`
     * invocations in the same `kmpFlavors {}` block. Multiple `buildKonfig {}`
     * blocks merge — secrets concat, enums concat, customFields concat,
     * perTarget blocks merge by target name.
     *
     * Read by `KmpFlavorPlugin.kt` (validator dispatch + task registration) and
     * `BuildKonfigSecretResolver.kt` (vault lookup).
     */
    @get:org.gradle.api.tasks.Internal
    val buildKonfigDsl: BuildKonfigDsl = BuildKonfigDsl()

    /**
     * v2.5 ergonomic block for vault-integrated secrets, dimension enums,
     * custom-type fields, and per-target conditional codegen. See [BuildKonfigDsl]
     * for the full pattern + KMPF-V26/V27/V28 validation contract.
     *
     * Example:
     * ```kotlin
     * kmpFlavors {
     *     buildKonfig {
     *         secret("API_KEY")
     *         enum("tier")
     *         customField("scopes", "List<String>", "listOf(\"read\", \"write\")")
     *         perTarget("iosMain") {
     *             field("BUNDLE_ID_SUFFIX", "String", "\".dev\"")
     *         }
     *     }
     * }
     * ```
     */
    fun buildKonfig(action: Action<BuildKonfigDsl>) {
        action.execute(buildKonfigDsl)
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
     * v2.2 Phase 4A — declare a variant promotion. Registers a Gradle task
     * `promote{From}To{To}` that copies files from `src/common{From}/` to
     * `src/common{To}/` with optional content transforms applied per file.
     *
     * Example:
     * ```kotlin
     * kmpFlavors {
     *     promote(from = "freeDev", to = "freeStaging") {
     *         applyTransform("renamePackage", "com.example.dev" to "com.example.staging")
     *         copyResources(true)
     *         copyTests(true)
     *     }
     * }
     * ```
     *
     * Resolve via `./gradlew :module:promoteFreeDevToFreeStaging` (add `-Pdry-run=true`
     * for preview).
     */
    @KmpFlavorsExperimental("Consumer demand signal unclear; DSL shape may evolve")
    fun promote(from: String, to: String, action: Action<VariantPromotionScope> = Action {}): VariantPromotion {
        val promotion = VariantPromotion(from = from, to = to)
        action.execute(VariantPromotionScope(promotion))
        variantPromotions.add(promotion)
        return promotion
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
     * v2.8 — Configure per-flavor signing configs using a DSL block.
     *
     * Example:
     * ```kotlin
     * kmpFlavors {
     *     signingConfigs {
     *         create("release") {
     *             storeFile.set(file("keystore/release.jks"))
     *             storePasswordFromEnv("KEYSTORE_PASSWORD")
     *             keyAlias.set("kmpflavors")
     *             keyPasswordFromEnv("KEY_PASSWORD")
     *         }
     *     }
     * }
     * ```
     */
    fun signingConfigs(action: Action<NamedDomainObjectContainer<SigningConfig>>) {
        action.execute(signingConfigs)
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
     * v2.2 Phase 4B — feature-flag integration scope. See [FeatureFlagsConfig] for
     * per-platform sub-configs (GrowthBook / Statsig / LaunchDarkly).
     *
     * No-op when none of the platform sub-configs have `defaultPayload` set.
     */
    @KmpFlavorsExperimental("SDK integration patterns may evolve as adopters wire it against real GrowthBook / Statsig / LaunchDarkly deployments")
    val featureFlags: FeatureFlagsConfig = objects.newInstance(FeatureFlagsConfig::class.java)

    /**
     * Configure the SPM manifest generator using a DSL block.
     */
    fun spm(action: Action<SpmConfig>) {
        action.execute(spm)
    }

    /**
     * v2.2 Phase 4B — configure feature-flag integrations.
     */
    fun featureFlags(action: Action<FeatureFlagsConfig>) {
        action.execute(featureFlags)
    }

    /**
     * v2.6 Phase 3 — DI integration scope (Koin only in v2.6; Kodein/Hilt-KMP/dagger
     * future). See [DiDsl] for the nested `koin { variantModule(...) { ... } }` DSL.
     *
     * Per-variant codegen emits `expect val ${name}Module` in commonMain + per-flavor
     * `actual val` + a `flavorDependentModules(): List<Module>` aggregator helper.
     *
     * No-op when no `variantModule(...)` blocks are declared. The plugin does NOT
     * inject `io.insert-koin:koin-core` — consumer brings their own Koin dependency.
     */
    val di: org.gradle.api.provider.Property<DiDsl> =
        objects.property(DiDsl::class.java).convention(objects.newInstance(DiDsl::class.java))

    /**
     * v2.6 Phase 3 — configure DI integration.
     */
    fun di(action: Action<DiDsl>) {
        action.execute(di.get())
    }

    /**
     * v2.6 Phase 3 — cross-platform analytics tags scope. See [AnalyticsTagsConfig] for
     * the `customTag(name) { variant -> ... }` DSL.
     *
     * Codegen emits `AnalyticsTags.kt` per variant with `VARIANT_NAME` + `BUILD_TYPE`
     * constants plus consumer-declared custom tags + an `attachTo(target)` extension
     * helper that reflectively wires into Firebase Crashlytics-shaped targets.
     *
     * No-op when `analytics.enabled = false` (default).
     */
    val analytics: AnalyticsTagsConfig = objects.newInstance(AnalyticsTagsConfig::class.java)

    /**
     * v2.6 Phase 3 — configure analytics tag integration.
     */
    fun analytics(action: Action<AnalyticsTagsConfig>) {
        action.execute(analytics)
    }

    init {
        // Set conventions
        generateBuildConfig.convention(true)
        buildConfigClassName.convention("BuildKonfig")
        createIntermediateSourceSets.convention(true)
        // v2.6 Tier E.1 — default false: silently skip inactive flavor source sets
        // even when their src/ dirs have on-disk content. Eliminates KGP's
        // "Unused Kotlin Source Sets" warning by structural avoidance.
        createInactiveFlavorSourceSets.convention(false)
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
        detektPerVariantPerTarget.convention(false)
        variantCacheNamespacing.convention(false)
        // v2.2 Phase 0G — master opt-out. Default `true` for fully-automatic-plugin
        // ergonomics; consumers wanting strict v2.0 / v2.1 semantics set to `false`.
        autoEnable.convention(true)
        // v2.2 Phase 1A — cross-variant intermediate source sets, opt-in.
        createIntermediateBuildTypeSourceSets.convention(false)
        // v2.2 Phase 3B — per-variant SBOM, opt-in.
        publishMatrixSbom.convention(false)
        // v2.2 Phase 5A — keep v2.1's Zip-shaped iOS publications during the migration window.
        publishMatrixLegacyIosClassifiers.convention(true)
        // v2.2 Phase 5C — npm registry publish opt-in.
        npmPublishMatrix.convention(false)
    }
}
