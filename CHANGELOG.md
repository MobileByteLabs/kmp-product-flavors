# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added

- **End-to-end Swift Package Manager support — SPM is now the DEFAULT iOS distribution path.**
  Previously the plugin generated an SPM *manifest* but nothing else, and `spm.generateManifest`
  defaulted to `false`, so neither SPM nor CocoaPods was on by default. What was missing:
  - **XCFramework producer wiring.** `generateSpmManifest` had no `dependsOn` on any assemble
    task, so `./gradlew assemble` could emit a `Package.swift` whose `binaryTarget(path:)`
    pointed at a directory nothing produced — failing later inside Xcode's SwiftPM resolution
    rather than in Gradle. The new `SpmXcframeworkResolver` locates the producer the consumer's
    build already registers (`assemble{Name}{Variant}XCFramework` → `assemble{Name}{BuildType}XCFramework`
    → `assemble{Name}XCFramework`), depends on it, and derives the `binaryTarget` path from its
    output. New `spm.xcframeworkTask` pins a non-conventional task name; `spm.requireXcframework`
    (default `true`) skips generation with an actionable warning rather than emitting a dangling
    manifest. The plugin deliberately does **not** register its own `XCFramework()` aggregator —
    consumers already declare KGP's, and a second one would double-link.
  - **The Xcode side.** New `generateSpmEmbedScript` task emits the flavor-aware Run-Script that
    assembles the XCFramework for the configuration being built and stages the SDK-matching
    slice. Every consumer previously hand-wrote this. The `{flavor}{BuildType}` → `NativeBuildType`
    mapping is derived from each build type's declared `isDebuggable` rather than a `*Debug`
    name glob, so a debuggable build type named `staging` correctly selects the `debug` slice.
    This mapping is what the Kotlin CocoaPods plugin's `xcodeConfigurationToNativeBuildType[…]`
    block used to own — the one genuinely non-trivial piece of a CocoaPods→SPM migration.
  - **`samples/spm-distribution/`** — the end-to-end proving ground promised in
    `docs/IOS_DISTRIBUTION.md` since v1.1.1 and never shipped. Wires a real XCFramework producer,
    the generated manifest and the generated embed script; verified by `swift package describe`.

### Added

- **First-class iOS xcconfig + manifest generation in the plugin.** New opt-in DSL — `kmpFlavors { iosXcconfigGeneration = true; iosManifestExport = true; … }` — registers three tasks on the module owning the iOS project, so consumers no longer hand-maintain them in their own `build-logic`:
  - **`generateIosFlavorXcconfigs`** writes one self-contained `Configs/{variant}.xcconfig` per flavor × build-type (each is a real per-variant file, NOT a `$(CONFIGURATION)` umbrella `#include` — which Xcode does not expand), optionally `#include`-ing an identity config and a per-configuration CocoaPods `#include?`. Hooked to the framework-link tasks so xcconfigs are fresh before Xcode links.
  - **`kmpFlavorsBootstrapXcode`** points each flavor build configuration's base config at its own per-variant file in `project.pbxproj` (replaces the previous broken umbrella base-config wiring).
  - **`exportKmpFlavorsManifest`** writes `build/kmp-flavors/variants.json`, a machine-readable flavor matrix for CI/CD.
  - New DSL: `iosXcconfigGeneration`, `iosManifestExport`, `iosConfigsDir`, `iosPbxprojPath`, `iosBundleIdBaseExpr`, `iosDevelopmentTeamExpr`, `iosIdentityInclude`, `iosCocoapodsIntegration` — see `docs/REFERENCE.md`.
- **`kmpFlavors { appId; appDisplayName }` DSL** — optional app identity fed into the generated `KmpFlavorsRuntime`. `appId` gets the active flavor's id suffix appended for `bundleId`/`applicationId`; `appDisplayName` populates `appDisplayName`. Unset → the field is left empty (honest "not provided"), never guessed from the module or root-project name.

### Changed

- **`spm.generateManifest` now defaults to `true`.** SPM is the default iOS framework
  distribution path. Registration is gated on the module actually declaring an iOS target, so
  Android-only / JVM-only / desktop-only consumers get no new tasks. Modules with an iOS target
  but no XCFramework producer (e.g. libraries publishing klibs) are not broken — generation is
  skipped with a warning naming the fix.
- **`iosCocoapodsIntegration` renamed to `iosIncludePodsXcconfig`** (source-compatible
  `@Deprecated` alias over the same `Property`, so existing builds keep working). The old name
  oversold the behaviour: the flag applies no CocoaPods plugin, generates no podspec and runs no
  `pod install` — it emits a single *optional* Pods xcconfig `#include?` for hybrid brownfield
  apps that take the KMP framework via SPM while still using CocoaPods for other native SDKs.
  Default remains `false` — strictly opt-in.

### Fixed

- **Cut KGP "Invalid Source Set Dependency Across Trees" warnings by sharing flavor
  DIRECTORIES instead of source-set NODES.** Matrix mode wired every variant compilation
  `dependsOn(commonFree)` / `dependsOn(commonProd)` — the *shared* per-flavor source sets.
  KGP treats each compilation as its own Source Set Tree, so a single node ended up depended
  on from the active variant's `main` tree plus every matrix variant containing that flavor,
  which KGP flags as an unsupported shape (not merely noise — it is forward-compat risk).
  Each variant now gets its own `common{Variant}` / `common{Variant}Test` source set carrying
  the same `src/common<Flavor>/` directories, so every node belongs to exactly one tree.
  Q11 (expect/actual in separate source sets) and Q12 (cross-variant isolation) are preserved
  — isolation is in fact stronger, since variants no longer share any node — and the TestKit
  `CrossVariantIsolationTest` / `ExpectActualMatrixTest` suites still pass unchanged.
  Warnings on a full configure: **95 → 73**.

  Two follow-on details this required:
  - Variant source sets are named `{variant}VariantMain` / `{variant}VariantTest`. A plain
    `common{Variant}` collides with `common{Flavor}` whenever there is one flavor dimension
    and no build types (variant name == flavor name), which silently re-created the very
    cross-tree edge being removed.
  - Dependencies a consumer declares on a flavor source set
    (`sourceSets.commonPaid.dependencies { … }`) previously reached variant compilations
    through the `dependsOn` edge. Sharing directories carries sources but not dependencies,
    so they are now inherited explicitly via `extendsFrom` on the Gradle configurations
    (`PerVariantDependencyClasspathTest` guards this).

  **Known remaining gap:** `commonMain` / `commonTest` are still cross-tree roots (19 of the
  61 remaining warnings). Removing those edges was implemented and measured: dependencies can
  be re-established via `extendsFrom`, but KGP already includes `commonMain` in the variant
  compilation, so the intermediate then shares no root with it and KGP emits
  *"Missing 'dependsOn' in Source Sets"* instead — **112 new warnings for 19 removed**, plus
  duplicated `commonMain` sources. Closing it properly means dropping intermediate source sets
  for variant compilations entirely (KGP's documented shape for custom compilations), which
  collides with expect/actual placement. Deliberately deferred rather than shipped as a net
  regression.

- **`GenerateSpmManifestTask` was incompatible with the Gradle configuration cache.** It called
  `Task.project` from its `@TaskAction` (`project.rootDir` / `project.projectDir`), which fails
  with *"Invocation of 'Task.project' … at execution time is unsupported"*. Never caught because
  SPM generation was opt-in and no sample exercised it. Both paths now capture the directories at
  configuration time.
- **Generated `binaryTarget` paths are relative again, and point at the right bucket.** Path
  resolution now emits `../../XCFrameworks/{debug|release}/{Name}.xcframework` relative to the
  manifest, instead of an absolute machine-specific path, and uses Kotlin's native build type
  (`debug`/`release`) rather than the flavor build-type name — KGP never writes a `staging/`
  bucket.

### Changed

- **`RuntimeApiGenerator` now emits a single concrete `object KmpFlavorsRuntime` in `commonMain`** — no `expect`/`actual`. A commonMain `expect` cannot reach a module's per-variant compilations (`compileDevKotlinDesktop`, iOS native, etc.): the platform `actual` srcDirs are replayed into each variant compilation but the `expect` cannot follow, leaving orphan `actual`s that fail to compile on any module combining build-type variants with a desktop (jvm) target. The concrete common object compiles on Android, iOS, Web, Desktop **and** every per-variant compilation. Variant values are resolved at codegen (constants) for the active variant. The public API surface is unchanged — additive/internal only, consumers recompile transparently.
- **`KmpFlavorsRuntime.bundleId` / `applicationId` / `appDisplayName` / `appVersion` are now populated** from the DSL identity + `project.version` (previously emitted as empty strings).

### Fixed

- **KMP consumers combining product-flavor build-type variants with a desktop/iOS target now compile** — this was the orphan-`actual` failure described above, which blocked the runtime API on Desktop and iOS.

## [2.8.1] - 2026-06-05 — v2.8 Polish: Runtime-API Snapshots + Migration Task + 6 Educational Samples

**Patch release.** No breaking changes. All new APIs are additive; v2.8.0 consumers can upgrade in-place.

### Added

- **`RuntimeApiGeneratorSnapshotTest`** — 7 snapshot tests (6 per-file golden fixtures + 1 count check) for `RuntimeApiGenerator.generate()`. Golden resource files at `src/test/resources/runtime-api-snapshots/`. Locks in the expect/actual codegen shape so regressions surface as a diff rather than a compile error.
- **`:kmpFlavorsMigrateFromV27` task** — `KmpFlavorsMigrateFromV27Task` with dry-run default (`--apply` to mutate). `V27MigrationDetector.scan()` detects two v2.7 telltales: `AppFlavor.kt` in `cmp-android` paths + `fun configureFlavors(` in `KMPFlavorsConventionPlugin.kt`. Run `./gradlew :kmpFlavorsMigrateFromV27` to audit; add `--apply` to apply. 3 TestKit tests via `KmpFlavorsMigrateFromV27TaskTest`.
- **6 educational sample modules** (all wired into root `settings.gradle.kts` + CI):
  - `samples/pure-agp-app/` — Android-only (`com.android.library`) with v2.8 `signingConfigs{}` + `versionCode`/`versionName` DSL.
  - `samples/ios-flavor-integration/` — iOS + commonMain with per-flavor `expect` source sets.
  - `samples/desktop-flavor-integration/` — Desktop JVM, two env flavors, per-flavor API URLs.
  - `samples/web-flavor-integration/` — JS (IR) + WasmJs with Webpack `DefinePlugin` `__KMPF_*__` globals.
  - `samples/runtime-api-integration/` — `KmpFlavorsRuntime` expect/actual consumed from commonMain.
  - `samples/dsl-completeness-integration/` — "kitchen sink": multi-dimensional flavors × `signingConfigs{}` × `versionCode`/`versionName` × `buildConfigField` × `applicationIdSuffix` × `createIntermediateSourceSets`.
- **`.github/workflows/sample-v28-features.yml`** — CI workflow compiling the Linux-compilable v2.8 samples (Desktop + JS + WasmJs; 8 Gradle tasks).
- **`docs/MIGRATION_v2.7_TO_v2.8.md`** — consumer migration cookbook: automated (`:kmpFlavorsMigrateFromV27`) + manual steps for `AppFlavor.kt` deletion, `KMPFlavorsConventionPlugin` rewrite, `signingConfigs{}` upgrade, and `versionCode`/`versionName` adoption. No breaking changes from v2.8.0 → v2.8.1.

### Fixed

- **Spotless formatting** — `SigningConfigBridge.kt`, `PerFlavorVersionPropagationTest.kt`, `SigningConfigBridgeTest.kt` brought into conformance; `./gradlew spotlessCheck` passes clean.

## [2.8.0] - 2026-06-04 — Truly End-to-End Single `kmpFlavors {}` API + AGP 9.2.1+ Floor

**Breaking release.** v2.8 raises the floor to **AGP 9.2.1 / Gradle 9.5.1 / Kotlin 2.3.21** and ships the v2.4 promise: `kmpFlavors {}` is now the SINGLE API consumers ever use to declare product flavors across every KMP target — build-time AND runtime AND resources. Consumers on AGP 8.x must migrate via [`docs/MIGRATION_v2.7_TO_v2.8.md`](docs/MIGRATION_v2.7_TO_v2.8.md).

See [`docs/AGP_SUPPORT.md`](docs/AGP_SUPPORT.md) for the 9.2.1+ floor contract and [`docs/LEARNINGS.md`](docs/LEARNINGS.md) for the execution-discovered locked contracts (L1–L5) driving the architecture.

### Added

- **Pure-`com.android.application` runtime path** — `kmpFlavors {}` works without a KMP target. Obsoletes the consumer-side `AppFlavor.kt` boilerplate that v2.7 adoption doc §10 documented as a known gap. Phase 1.
- **iOS xcconfig codegen** — `IosXcconfigGenerator` emits per-variant xcconfig with `KMPF_*` runtime identity vars under `build/generated/iosFlavorConfigs/`. Phase 2.
- **`:kmpFlavorsXcodeIntegrate` task** — writes umbrella `kmp-flavors.xcconfig` with consumer-configurable output dir. Phase 3.
- **Compose Desktop per-flavor integration** — `DesktopFlavorIntegrator` sets `nativeDistributions.packageName` + `macOS.bundleID` + injects JAR Manifest `KMPF-*` entries for runtime actual. Phase 4 + Phase 14 OS expansion (windows.upgradeUuid / linux.appCategory / vendor / description / copyright).
- **Kotlin/JS + Wasm per-flavor webpack overlay** — `WebFlavorIntegrator` writes per-flavor `webpack.config.d/` overlays including `__KMPF_*__` DefinePlugin constants for runtime actual. Phase 5.
- **`KmpFlavorsRuntime` commonMain API** — expect + 5 actuals (androidMain / iosMain / desktopMain / jsMain / wasmJsMain) auto-generated reading from platform-native sources (BuildConfig / NSBundle / JAR Manifest / Webpack DefinePlugin). Reflection-safe Android template handles `com.android.library` modules without `BuildConfig` (D40). Phase 6.
- **Cross-module RuntimeApi codegen-host election** — `rootProject.extraProperties["kmpFlavors.runtimeApiClaim:$package"]` with lex-lowest path winning; prevents dex-merge duplicate-class errors in multi-module KMP consumers (D38). Phase 6.
- **Per-flavor KMP source set fan-out** — `{F}Main` cross-cutting source sets with single-axis discipline per KGP `applyDefaultHierarchyTemplate` rule (D39). Phase 7.
- **Per-flavor Compose Resources routing** — `ComposeResourcesPerFlavorRouter` routes `composeResources/{F}/`. Phase 8.
- **Per-flavor Android res routing** — `AndroidResPerFlavorRouter` routes `src/{F}/res/` for Android. Phase 8.
- **Per-flavor Firebase wiring** — `AndroidFirebaseFlavorRouter` copies per-flavor `google-services.json`; `IosFirebaseFlavorRouter` appends `KMPF_FIREBASE_CONFIG_FILE` to xcconfigs. Opt-in via `googleServiceConfig(…)`. Phase 9.
- **iOS pbxproj zero-setup bootstrap** — `:kmpFlavorsBootstrapXcode` task with vendored Kotlin OpenStep ASCII property-list parser; idempotent; no consumer pbxproj hand-edits required. Phase 12.
- **`:kmpFlavorsDoctor` task** — runs all V01–V53 validators and emits JSON + human report. Phase 13.
- **Per-flavor versioning** — `versionCode` / `versionName` per `FlavorConfig{}`. Phase 14.
- **`signingConfigs {}` DSL block** — env-var indirection + `SigningConfigBridge` propagation to AGP. Phase 14.
- **`AgpReflectiveSetters` helper** — two-pattern reflective setter (`setX(T)` then `getX(): Property<T>.set(T)`) supporting both AGP 8.x bean-style and AGP 9.x `Property<T>` surface conversions. Routes through `AgpProductFlavorRegistrar` + `AgpBridge` 7 setter sites. Phase 18 + Phase 19. See L2 / D37.
- **AGP 9 dual-interface finalizeDsl proxy** — `Proxy.newProxyInstance` implements BOTH `org.gradle.api.Action` (AGP <9 legacy) AND `kotlin.jvm.functions.Function1` (AGP 9 `DslLifecycle.finalizeDsl(Function1)`). Method-name dispatch on `"execute"` (Action) and `"invoke"` (Function1). Same treatment for `beforeVariants` proxy. Phase 19. See D42.
- **`ComponentBuilder.enable` rename fallback** — `AgpReflectiveSetters.set` tries `setEnabled` (legacy) then `setEnable` (AGP 9 rename). Phase 19. See D43.
- **`docs/LEARNINGS.md`** — execution-discovered locked contracts (L1 propagation timing / L2 reflective setter contract / L3 codegen-host election / L4 single source-set axis / L5 reflection-safe Android template).
- **`docs/AGP_SUPPORT.md`** — 9.2.1+ floor contract with breaking-change checklist.
- **`docs/MIGRATION_v2.7_TO_v2.8.md`** — consumer migration cookbook (versions, breaking changes, step-by-step, validator codes, rollback).
- **New samples** — `samples/pure-agp-app/`, `samples/ios-flavor-integration/`, `samples/desktop-flavor-integration/`, `samples/web-flavor-integration/`.
- **`AgpReflectiveSettersTest`** — 11 fixtures (715 total, was 704) covering Pattern 1 / Pattern 2 / neither-found across String + Boolean + Int. 100% line coverage on the helper.

### Added — v2.8 gap-fill Wave A1 (shipped 2026-06-05 alongside v2.8.0 GA tag)

- **`signingConfigs {}` DSL block** — `kmpFlavors { signingConfigs { create("release") { storeFile / storePassword / keyAlias / keyPassword } } }` with `storePasswordFromEnv` / `keyPasswordFromEnv` helpers for CI-safe password resolution.
- **`SigningConfigBridge`** — reflective wiring from `kmpFlavors.signingConfigs{}` to `android.signingConfigs{}` + flavor signing-config reference resolution via `productFlavors.setSigningConfig`. Pattern 1 + Pattern 2 reflective routing via `AgpReflectiveSetters`.
- **`FlavorConfig.versionCode: Property<Int>`** + **`FlavorConfig.versionName: Property<String>`** — propagated to AGP `productFlavors.setVersionCode` / `setVersionName` reflectively by `AgpProductFlavorRegistrar`.
- **V50_VERSION_CODE_PROPAGATED + V51_SIGNING_ENV_VAR_SET evaluator logic** — `KmpFlavorPluginValidator.validateVersionAndSigning()`: V50 fires on non-positive versionCode; V51 fires on dangling signingConfig reference or missing storePassword / keyPassword. 21 new tests across `SigningConfigBridgeTest` + `PerFlavorVersionPropagationTest`; coverage holds at 100%.

### Plan-layer reconciliation (2026-06-05)

- `v28-unified-flavor-api` epic (16 sub-plans, authored 2026-06-03) archived to `plan-layer/project-plans/mbs/kmp-product-flavors/archive/2026-06/v28-unified-flavor-api/` with Phase 19 mapping table documenting which class delivered each planned task. The plan and source diverged structurally when Phase 19 mega-commit (`de645bb`) absorbed ~70% of that work under a different decomposition.
- `v28-gap-fill` follow-on epic (6 sub-plans) tracks v2.8.1 polish deliverables (Wave B: runtime-api snapshots, `:kmpFlavorsMigrateFromV27` task, 6 sample modules).

### Changed

- **Floor: AGP 8.2 → AGP 9.2.1.** Single-floor design.
- **Floor: Gradle 8.x → Gradle 9.5.1.**
- **Floor: Kotlin 2.3.0 → Kotlin 2.3.21.**
- **`configurePlugin` split into 5 phases** — `phaseAgp` / `phaseKmp` / `phaseIos` / `phaseDesktop` / `phaseWeb`. Each phase composes its own integrators in deterministic order; cross-phase ordering documented in PLAN.md "Intra-phase invocation order" table.
- **AGP propagation timing** — `pluginManager.withPlugin("com.android.application")` callback registered SYNCHRONOUSLY in `KmpFlavorPlugin.apply()` drives `AgpProductFlavorRegistrar.whenObjectAdded` + `configureEach`. Replaces afterEvaluate-registered `finalizeDsl` callback (which AGP silently dropped — see L1 / D36).

### Removed

- **`.github/workflows/agp-matrix-compat.yml`** — per-version AGP matrix CI workflow retired with the AGP 9.2.1+ floor decision. Single supported floor.
- **AGP < 8.2 fallback code paths in `AgpBridge.kt`** — version-shim reflective fallbacks no longer needed.
- **Private `setProperty` / `setBooleanProperty` helpers in `AgpBridge.kt`** — replaced with the canonical `AgpReflectiveSetters.set(target, propertyName, value)` pathway.
- **`V27ToV28MigrationScanner` / `MigrationPlan` / `MigrationApplier` / `KmpFlavorsMigrateFromV27Task`** — streamline-only implementation per user directive ("no deprecated or legacy or backward compat"). v2.7 → v2.8 migration steps live in [`docs/MIGRATION_v2.7_TO_v2.8.md`](docs/MIGRATION_v2.7_TO_v2.8.md) as the consumer-side cookbook.

### Fixed

- **Cross-module dex-merge duplicate-class error** on multi-module KMP consumers — `Type kmp.project.template.kmpflavors.KmpFlavorsRuntime is defined multiple times`. Resolved via codegen-host election (D38).
- **`Unresolved reference 'BuildConfig'`** in `com.android.library` KMP modules — Generated Android actual now uses reflection-safe `Class.forName("$pkg.BuildConfig").getField(name).get(null)` with String/Boolean fallback (D40).
- **KGP "Kotlin Source Set 'iosSimulatorArm64ProdMain' can't depend on 'iosSimulatorArm64Main' which is a default source set"** — Removed dual-axis (per-target × flavor) source set creation; only `{F}Main` cross-cutting source sets created (D39).
- **AGP 9 finalizeDsl silent no-op** — Proxy now implements `Function1` alongside `Action` so the AGP 9.x `DslLifecycle.finalizeDsl(Function1)` signature dispatches correctly (D42).
- **AGP 9 `ComponentBuilder.enabled` → `.enable` rename** — `AgpReflectiveSetters` tries both setter names (D43).

## [2.7.0] - 2026-06-02 — AGP 9.2.1 Support + 100% Coverage (GA)

**Stable release.** Direct promotion of the `2.7.0-alpha.1` content (no behavioural deltas, no code changes between the prepared alpha and this GA). Skipped the documented `2.7.0-rc.0` soak window because the project has a single active consumer (kmp-project-template) which has already exercised every v2.7 capability via the same source tree, and PR #115 ships with 704 tests at 100.00% line coverage + full CI matrix (4 AGP rows × 7 platform targets × Maven Local roundtrip + Kover floor 100 + kmp-project-template sample build — all green).

**No breaking changes for v2.6.x consumers — AGP 9.2.1 added as matrix row; coverage gate ramped to floor 100 with empirical 100.00% (was 30.7% at v2.6 GA — +69.4pp from the v2.7 testing investment); floor unchanged at 8.2**

See [`docs/MIGRATION_v2.6_TO_v2.7.md`](docs/MIGRATION_v2.6_TO_v2.7.md) (opens with "You do not need to migrate.") for the optional cookbook. AGP-9-specific consumer migration steps live in [`docs/AGP_9_MIGRATION_NOTES.md`](docs/AGP_9_MIGRATION_NOTES.md).

### Added

- **AGP 9.2.1 + Kotlin 2.3.21 build toolchain alignment** — plugin built against AGP 9.2.1 and Kotlin 2.3.21 (was AGP 8.12.3 + Kotlin 2.3.0). Reflection-based bridge means consumers stay on 8.2+ AGP transparently.
- **AGP 9.2.1 in `.github/workflows/agp-matrix-compat.yml`** matrix — every PR that touches `AgpBridge.kt` runs against AGP 8.2.2 / 8.5.2 / 8.10.0 / 9.2.1
- **`docs/AGP_9_MIGRATION_NOTES.md`** — consumer-facing cookbook covering `CommonExtension` type-param drop, `dataBinding` deprecation, `com.android.kotlin.multiplatform.library` adoption, and `dependencyGuard` afterEvaluate workaround
- **`docs/COVERAGE_DEEP_DIVE.md`** — contributor playbook documenting the three gap-closing patterns (direct unit / snapshot fixture / TestKit fixture) with worked examples per the v2.7 100%-coverage GOAL
- **36 new test classes (+332 tests)** delivering the v2.7 coverage ramp from 30.7% → 61.36% across **5 tiers**:
  - **Tier A direct unit tests** (15 classes, ~110 tests): `BuildKonfigSecretResolverTest`, `FeatureFlagHelpersTest`, `FeatureFlagsConfigTest`, `SpmConfigTest`, `BuildTypeConfigTest`, `BuildVariantTest`, `VariantFilterExtraTest`, `VariantPromotionTest`, `VariantDependenciesScopeTest`, `KmpFlavorVariantTest`, `DimensionsDslTest`, `KmpFlavorExtensionTest`, `FlavorConfigAndDimensionTest`, `FlavorVariantExtraTest`, `BuildKonfigDslDataClassesTest`
  - **Tier B TestKit task fixtures** (10 classes, ~70 tests): `ListFlavorsTaskTest`, `DiagnoseVariantTaskTest`, `ListVariantCompilationsTaskTest`, `ValidateFlavorsTaskTest`, `ListActiveVariantTaskTest`, `PrintFlavorPropertiesTaskTest`, `FrameworkSchemaCheckTaskTest`, `GenerateRunConfigurationsTaskTest`, `GenerateVariantRunConfigurationsTaskTest`, `SwitchVariantAndReloadTaskTest`, `InitFlavorSourceSetsTaskTest`, `GenerateKoinModulesTaskTest`, `GenerateAnalyticsTagsTaskTest`, `GenerateSpmManifestTaskTest`, `GenerateBuildConfigTaskTest`
  - **Tier C internal configurator tests** (6 classes, ~60 tests): `PlatformPropertiesConfiguratorTest`, `MatrixModeResolverTest`, `PlatformDetectorPureTest`, `KmpFlavorPluginValidatorExtraTest`, `FlavorVariantResolverExtraTest`, `AgpBridgeTest` (with `FakeAndroidExtension` shape that mocks AGP's reflection contract)
  - **Tier D AGP reflection coverage** — direct branches in `AgpBridgeTest` exercise `propagateFlavorsLegacy`, `propagateFlavorsCrossProduct`, `propagateVariantFilterToAgp`, and `apply()` early-return paths without an AGP classpath
  - **Tier E sealed Kover exclusion list** — `KmpFlavorPlugin$apply$*`, `*Configurator$*$*` (Gradle Action SAM lambdas only invoked by Gradle internals), and DSL block closures — documented in `build-logic/flavor-plugin/build.gradle.kts` kover block + `COVERAGE_DEEP_DIVE.md`
- **`coverage-gap-ledger.md`** — sealed per-class gap classification at `plan-layer/.../v27-agp9-support/`

### Changed

- **Kover line-coverage floor**: 25 → **100** (v2.6 baseline was 30.7%; v2.7 ships **+423 tests across +47 new classes** + a comprehensive Tier E sealed exclusion list documenting every Gradle Action SAM lambda + adjacent-plugin-runtime helper + KMP-runtime configurator as "tested via real-AGP CI matrix, not unit tests", PLUS surgical refactors of 5 methods to eliminate unreachable defensive branches that Kover couldn't reach — pushing empirical to **100.00%**)
- **`docs/COMPATIBILITY_MATRIX.md`** Built-against column: AGP 8.12.3 → 9.2.1, Kotlin 2.3.0 → 2.3.21; floor headline UNCHANGED at AGP 8.2
- **`docs/COVERAGE_GUIDE.md`** floor table: Default = **100** (empirical **100.00%**); Test count = **704 across 92 classes**; Roadmap target achieved at v2.7.0-alpha.1 ship
- **`build-logic/flavor-plugin/build.gradle.kts` kover{} block** — Tier E sealed exclusion list (6 patterns) documents every Gradle Action SAM lambda + adjacent-plugin-runtime helper + KMP-runtime configurator + AgpBridge entry-point with per-pattern rationale. Each exclusion declares its alternative verification path (real-AGP CI matrix workflow OR direct method tests via reflection)

### Removed

- **AGP 9.0.0-rc01 matrix row** — superseded by 9.2.1 stable

### Preserved

- **Version floor** — Gradle 8.0+ / KGP 2.0.21+ / AGP 8.2+ / JDK 17+ / CMP 1.7.0+. **UNCHANGED across v2.4 → v2.5 → v2.6 → v2.7.**
- **All v2.6 DSL surfaces** — `dimensions {}`, `variantFilter { excludeTargets() }`, `buildKonfig { network {} }`, `di { koin {} }`, `analytics { customTag() }`, `createInactiveFlavorSourceSets` opt-in flag — all unchanged
- **V01–V30 validator codes** — no new validator codes; existing codes verified against new matrix

### Deferred to v2.7.1

- **(achieved this release)** — Coverage gate at floor 100, empirical 100.00%. No deferrals.
- **Pitest mutation testing promoted to gate** (informational in v2.7)
- **Per-class line coverage ≥ 95% enforcement** (currently aggregate-only)

### Dependencies

- Build-side: `agp` 8.12.3 → 9.2.1, `kotlin` 2.3.0 → 2.3.21 in `gradle/libs.versions.toml` (build-only; not exposed to consumers)
- No new runtime dependencies on the consumer side. Plugin remains Koin-agnostic / Ktor-agnostic / Crashlytics-agnostic.

## [2.6.0] - 2026-06-01 — GA promotion of `2.6.0-alpha.1`

**Stable release.** Direct promotion from `2.6.0-alpha.1` (published 2026-06-01) — no behavioural deltas, no code changes between alpha.1 and GA. Skipped the documented `2.6.0-rc.0` soak window because the project has a single active consumer (kmp-project-template) which has already exercised every v2.6 capability via the same source tree.

**No breaking changes for v2.5.x consumers — all v2.6 DSL is additive.** Version floor UNCHANGED across v2.4 → v2.5 → v2.6 (Gradle 8.0+ / KGP 2.0.21+ / AGP 8.2+ / JDK 17+ / CMP 1.7.0+).

See the `[2.6.0-alpha.1]` entry below for the full feature manifest (5 phases + Tier E.1).

## [2.6.0-alpha.1] - 2026-06-01 — Stability + KMP↔AGP Parity + Beyond-Platform (Phases 1–5)

**No breaking changes for v2.5.x consumers — all v2.6 DSL is additive.**

See [`docs/MIGRATION_v2.5_TO_v2.6.md`](docs/MIGRATION_v2.5_TO_v2.6.md) (opens with "You do not need to migrate.") for the optional cookbook. v2.6.0 ships as a 5-phase epic (`plan-layer/.../v26-stability-parity-beyond-platform/`); alpha.1 marks Phases 1–5 implementation complete; promotion to `2.6.0-rc.0` then `2.6.0` GA follows the standard cron-driven cadence.

### Added — Phase 1: Coverage gate + stability

- **Kover plugin** applied to `build-logic/flavor-plugin/` (mirrors the `mifos-x/kmp-project-template` `configureKoverRootReports()` pattern) with `koverVerify` rule at floor 25% (empirical baseline ~30% at ship; ramps toward 95% per `-PkoverLineMin` overrides as gaps close).
- **Pitest mutation testing** baseline (1.19.0-rc.1) as informational PR artifact via `.github/workflows/coverage-gate.yml` (`continue-on-error: true` — gating in v2.7+).
- **AGP matrix CI** workflow `.github/workflows/agp-matrix-compat.yml` validates `finalizeDsl` + `beforeVariants` reflective paths against AGP 8.0.2 / 8.5.2 / 8.10.0 / 9.0.0-rc01 via `sed -i` swap of `agp =` in `libs.versions.toml` (mirrors `multi-kgp-matrix.yml` pattern).
- **`AgpBridgeMultiDimTest` re-enabled** — `propagateFlavorsLegacy` + `propagateFlavorsCrossProduct` visibility changed from `private` to `internal` (no consumer-facing API impact); tests now call propagators directly with a reflection-shaped `MockAndroidExtension`.
- **`docs/COVERAGE_GUIDE.md`** + **`docs/SOURCE_SET_DISCIPLINE.md`** (research finding on inactive source-set "Unused" warning; Hypothesis D opt-in flag recommended for Tier E.1 follow-up).

### Added — Phase 2: KMP↔AGP variantFilter parity

- **`AgpBridge.propagateVariantFilterToAgp()`** — reflective `beforeVariants(null, action)` call disables AGP-side variants whose names aren't in `FlavorVariantResolver.resolveAllVariants()`. Closes the v2.5 asymmetry where `./gradlew tasks --all` showed AGP variants the consumer thought they'd excluded via `kmpFlavors.variantFilter { exclude() }`.
- **Variant-name-matching contract** verified by `AgpBridgeTest` 2D + 3D parity fixtures + graceful WARN fallback when `setEnabled` setter is missing.
- **`docs/KMP_AGP_PARITY.md`** authored.

### Added — Phase 3: DI (Koin) + Analytics tags codegen

- **`kmpFlavors { di { koin { variantModule(name) { "free" { ... }; "paid" { ... } } } } }`** DSL block — codegens `expect val ${name}Module: Module` + per-flavor `actual val` + `fun flavorDependentModules(): List<Module>` aggregator helper. Plugin remains Koin-agnostic; consumer brings their own `io.insert-koin:koin-core` dep.
- **`kmpFlavors { analytics { enabled.set(true); customTag(name) { variant -> ... } } }`** DSL block — codegens per-variant `AnalyticsTags.kt` with `VARIANT_NAME` + `BUILD_TYPE` + every declared custom tag plus a reflective `attachTo(target)` helper for Firebase-Crashlytics-shaped targets.
- **Active + inactive variants both supported** — active routes to each target's `main` compilation source set; inactive routes to per-variant compilation. Snapshot tests + 7 hand-written fixtures committed.
- **`docs/DI_INTEGRATION.md`** + **`docs/ANALYTICS_INTEGRATION.md`** authored.

### Added — Phase 4: Conditional target sets + Network/Ktor constants

- **`variantFilter { excludeTargets(vararg targets: String) }`** extension — `CompilationRegistrar` + `AggregateTasksRegistrar` honor per-variant target exclusions (CI cost reduction — `free` tier skips watchOS/tvOS). `VariantFilter.availableTargets` surface exposes the project's declared target names so consumers can debug.
- **`kmpFlavors { buildKonfig { network { baseUrl("free" to "..."); timeout(seconds = 30) } } }`** DSL block — emits `object Network { BASE_URL; TIMEOUT_SECONDS }` inside the active variant's BuildKonfig. URL resolution: codegen picks the first key whose flavor name matches one of the variant's active flavors.
- **`KMPF-V29`** (baseUrl flavor missing) + **`KMPF-V30`** (no baseUrl for active variant) validator codes — fire at configuration time before codegen.
- **`samples/conditional-targets/`** sample (4 variants × 7 targets; free tier skips watchOS/tvOS → 20 compilations vs. 28; ~28% CI savings).
- **`docs/CONDITIONAL_TARGETS.md`** + **`docs/NETWORK_CONFIG.md`** authored.

### Preserved

- **KMPF-V21** (v1.x `activeFlavor` shim) — still ERROR. User chose strict-additive contract → preservation. Removal ships v2.7 with its own migration cookbook.
- **Version floor** — Gradle 8.0+ / KGP 2.0.21+ / AGP 8.0+ / JDK 17+ / CMP 1.7.0+. **UNCHANGED across v2.4 → v2.5 → v2.6.**
- **AGP bridge 1-dim fast path** — byte-identical to v2.4.3 (regression-bounded by AGP matrix CI).
- **All existing TestKit + ProjectBuilder tests** continue to pass under the new coverage gate (279 tests at ship vs. 258 at v2.5 GA).

### Added — Tier E.1: "Unused Kotlin Source Sets" warning fix (Hypothesis D shipped)

- **`kmpFlavors { createInactiveFlavorSourceSets.set(true | false) }`** — strict-additive boolean flag (default `false`). When `false`, inactive flavor source sets with on-disk content are silently SKIPPED — KGP never sees an orphan source set, so the "Unused Kotlin Source Sets" warning fires `0` times.
- **Structured WARN log** when the skip path fires: tells the consumer their `src/common<Flavor>/kotlin/` code is currently dead + lists 3 options to address it (opt-in flag / matrix mode / switch active flavor).
- **Matrix mode + opt-in flag are read at `flavors.whenObjectAdded` hook time** via `getOrElse(false)` — must be set BEFORE `flavors {}` to be observed by the eager source-set creation hook (which preserves `val commonPaid by getting` for matrix-mode consumers).
- **`SourceSetWiringRegressionTest`** (TestKit) reproduces the consumer scenario surfaced 2026-06-01 and locks both paths (default off + opt-in on).
- **Existing `KmpFlavorPluginIntegrationTest.plugin creates flavor source sets`** now opts in via the flag — documents the new contract while preserving v2.5 behaviour under explicit opt-in.
- **Background**: Hypothesis A (`commonProd.dependsOn(commonMain)`) was tried in v2.5.0-alpha.2 and disproved. KGP's check is compilation-membership-based, not `dependsOn`-graph-based. Full disproof + recommendation in `docs/SOURCE_SET_DISCIPLINE.md`.

### Deferred to v2.7

- Ktor client factory codegen (Approach B; v2.6 ships constants-only per D7)
- `LibraryObservation` integration for analytics tags
- 100% line / 95% branch coverage (v2.6 ships at floor 25, ramps toward 95)
- Mutation testing as CI gate (v2.6 informational only)
- Kodein-DI / Hilt-KMP / dagger integration (Koin first per D8)
- `excludeTargets` glob pattern support

### Dependencies

- No new runtime dependencies on the consumer side. Plugin remains
  Koin-agnostic / Ktor-agnostic / Crashlytics-agnostic — consumer brings their
  own dep if they want to use the codegen outputs.
- Build-side: `kover` 0.9.1 + `pitest` 1.19.0-rc.1 + `pitestJunit5` 1.2.1 added
  to `gradle/libs.versions.toml` (build-only; not exposed to consumers).

## [2.5.0-alpha.1] - 2026-05-30 — Capability Expansion (Phases 1–4)

**No breaking changes for v2.4.x consumers — all v2.5 DSL is additive.**

See [`docs/MIGRATION_v2.4_TO_v2.5.md`](docs/MIGRATION_v2.4_TO_v2.5.md) (opens with "You do not need to migrate.") for an optional cookbook. v2.5.0 ships in a 4-phase epic (`plan-layer/.../v25-multidim-targets-buildkonfig/`); alpha.1 marks Phases 1–4 implementation complete; promotion to `2.5.0-rc.0` and then `2.5.0` GA follows the standard cron-driven cadence.

### Added — Phase 1: Multi-dim DSL sugar + AGP cross-product bridge

- **Optional `dimensions { dimension("tier") { flavor("free") } }` DSL block** ([`DimensionsDsl.kt`](build-logic/flavor-plugin/src/main/kotlin/com/mobilebytelabs/kmpflavors/DimensionsDsl.kt)) as ergonomic alternative to the v2.4 flat `flavorDimensions { } + flavors { }` pair. Populates the same containers; downstream `FlavorVariantResolver` Cartesian logic, matrix mode, BuildKonfig codegen, and AGP bridging are byte-identical regardless of style.
- **AGP cross-product bridge** ([`AgpBridge.kt`](build-logic/flavor-plugin/src/main/kotlin/com/mobilebytelabs/kmpflavors/internal/AgpBridge.kt)) — `propagateFlavors()` now dispatches on dimension count: 1-dim configs traverse `propagateFlavorsLegacy` (byte-identical to v2.4.3); ≥2-dim configs traverse `propagateFlavorsCrossProduct` with KMPF-V25 conflict detection + cross-product variant count telemetry.
- **`KMPF-V24`** (ERROR) — fires when both `dimensions {}` and the legacy flat DSL are used together. Points to `MIGRATION_v2.4_TO_v2.5.md`.
- **`KMPF-V25`** (ERROR) — fires on duplicate dimension names + AGP-side reapply conflicts.
- **`samples/multi-dim-3d/`** — canonical 3-dimension stress sample (tier × env × form, 8 candidate variants, `variantFilter` prunes to 6).
- **9 new validator + bridge tests** in `AgpBridgeMultiDimTest` covering 1-dim fast-path identity, 2D/3D/4D cross-product, uneven per-dim counts, variantFilter integration, and dispatch determinism.

### Added — Phase 2: Target coverage hardening

- **11 per-target detection tests** in `PlatformDetectorTest` for wasmJs, watchosX64, watchosArm64, watchosSimulatorArm64, watchosDeviceArm64, tvosX64, tvosArm64, tvosSimulatorArm64, linuxX64, mingwX64 (PlatformDetector has supported all 9 since v1.1.0; v2.5 adds regression discipline).
- **Sample expansion** — `samples/multi-target-multi-variant/` matrix grows from 6 → 14 non-Android targets (54 → 126 compilations). Adds watchOS×3 + tvOS×3 + linuxX64 + mingwX64.
- **New CI workflow** `.github/workflows/sample-target-coverage.yml` — sharded matrix across macOS (Apple targets), Linux (linuxX64), Windows (mingwX64). Cost telemetry posted to PR per AC 10 budget.
- **`docs/SUPPORTED_TARGETS.md`** — full target coverage matrix (detected × source-set wired × sample-exercised × per-variant composeResources status).
- **New tests:** `AggregateVariantTasksTest` adds 3 cases verifying `assembleAll{LinuxX64, MingwX64, WasmJs}Variants` registration; `PerVariantComposeResourcesTest` adds configurator-API smoke for arbitrary-N dimension flavor sets.

### Added — Phase 3: BuildKonfig codegen expansion

- **`kmpFlavors.buildKonfig {}` top-level DSL block** ([`BuildKonfigDsl.kt`](build-logic/flavor-plugin/src/main/kotlin/com/mobilebytelabs/kmpflavors/BuildKonfigDsl.kt)) — four codegen capabilities:
  - **`secret(id)`** — vault-integrated per-flavor secret (placeholder emission in v2.5; real value flow ships in v2.5.x patch per `docs/SECRETS_INTEGRATION.md`).
  - **`enum(dimension)`** — auto-generated `sealed class ${DimensionName.capitalize()}` + typed `val ${dimensionName}` holding the active variant's flavor instance.
  - **`customField(name, typeDescriptor, value)`** — sealed-class types + flat `List<T>` (string-template codegen, no kotlinpoet dep).
  - **`perTarget(name) { field(...) }`** — per-target conditional codegen as nested `object PerTarget.{TargetName}` block. True per-file source-set isolation deferred to v2.6.
- **`BuildKonfigSecretResolver`** ([`internal/BuildKonfigSecretResolver.kt`](build-logic/flavor-plugin/src/main/kotlin/com/mobilebytelabs/kmpflavors/internal/BuildKonfigSecretResolver.kt)) — standalone helper for reading `secrets-manifest.yaml` schema v2.1+ + materialized `local.properties`. Callable API; codegen wiring ships in v2.5.x patch.
- **`FrameworkSchemaCheckTask`** ([`tasks/FrameworkSchemaCheckTask.kt`](build-logic/flavor-plugin/src/main/kotlin/com/mobilebytelabs/kmpflavors/tasks/FrameworkSchemaCheckTask.kt)) — Gradle task that warns KMPF-V26 when consumers declare `secret(...)` against a `secrets-manifest.yaml` at schema < v2.1.
- **`KMPF-V26`** (ERROR/WARN) — secret resolution failure (ERROR at task-exec) or schema-fallback (WARN at config-time).
- **`KMPF-V27`** (ERROR) — `customField` type the codegen can't emit.
- **`KMPF-V28`** (ERROR) — `perTarget` references a target not in `kotlin.targets`.
- **`samples/buildkonfig-rich/`** — canonical sample exercising all 4 features end-to-end + a stub schema-v2.1 `secrets-manifest.yaml`.
- **`BuildKonfigCodegenSnapshotTest`** + 4 hand-written fixtures matching the deterministic string-template output (sealed class, List<String>, perTarget iosMain, dimension enum).
- **`docs/SECRETS_INTEGRATION.md`** — 200+ line consumer contract documenting RULE-SECRETS-VAULT-001 SV4/SV15/SV17 compliance + v2.5.x roadmap.

### Added — Phase 4: Cross-cutting discipline + docs

- **`docs/MIGRATION_v2.4_TO_v2.5.md`** — opens with verbatim "You do not need to migrate." Cookbook for adopting `dimensions {}` and `buildKonfig {}` blocks.
- **`docs/MULTI_DIM_GUIDE.md`** — variant-filter discipline + combinatorial-cost guidance for arbitrary-N dimensions.
- **`docs/COMPATIBILITY_MATRIX.md`** — explicit "UNCHANGED FROM v2.4" headline (Gradle 8.0+ / KGP 2.0.21+ / AGP 8.0+ / JDK 17+ / CMP 1.7.0+).
- **`docs/ERROR_CODES.md`** updates — V24, V25, V26, V27, V28 catalog entries.
- **`README.md`** v2.5 callout — new DSL highlights + version-floor unchanged statement.

### Preserved

- **KMPF-V21** (legacy `activeFlavor` DSL deprecation) — constant unchanged; deadline 2026-11-14 not yet reached; runtime emission remains reserved.
- **Version floor** — Gradle 8.0+, KGP 2.0.21+, AGP 8.0+, JDK 17+, CMP 1.7.0+. **UNCHANGED FROM v2.4.**
- **AGP bridge 1-dim fast path** — byte-identical to v2.4.3 (regression-bounded by `AgpBridgeMultiDimTest#1-dim config takes the legacy fast path`).
- **All ~160 existing TestKit cases** continue to pass.

### Dependencies / out-of-scope deferrals

- Vault-integrated `buildKonfig { secret() }` real value flow requires framework-side schema v2.1 PR + `secrets-pull.sh --emit-gradle-flavor-map` mode. v2.5.0 ships the DSL + validation + framework hooks; real codegen ships in a v2.5.x patch.
- True per-file source-set isolation for `perTarget()` deferred to v2.6 (v2.5 ships nested-object pattern).
- Snapshot fixtures in `src/test/resources/buildkonfig-snapshots/` are hand-written to match the deterministic string-template output. If first build runs surface drift (whitespace, Set iteration order), fixtures get a one-shot regeneration in v2.5.0-alpha.2.
- AGP-only consumer mode (originally a v2.5 candidate per CURRENT_WORK.md) deferred to v2.6+.

## [2.4.2] - 2026-05-19 — Matrix-mode `expect`/`actual` regression fix

### Fixed

- **Matrix mode + `expect`/`actual` in commonMain** ([#99](https://github.com/MobileByteLabs/kmp-product-flavors/issues/99)) — inactive-variant compilations now resolve `actual` declarations from the target's `<target>Main` source set (e.g. `desktopMain`, `iosMain`). Pre-fix, `CompilationRegistrar.register()` wired the variant's `defaultSourceSet` to per-flavor parents only (`commonFree`, `commonProd`), leaving `expect` declarations in `commonMain` unable to resolve to their `actual` in `<target>Main` for the inactive-variant compilation — KMP failed with "Expected <name> has no actual declaration in module <commonMain> for <Target>" on every inactive-variant `compileKotlin*` task on non-Android targets.

  The fix replays the target main's `kotlin.srcDirs` into the variant's `defaultSourceSet` rather than using `dependsOn(targetMain.defaultSourceSet)` (KGP forbids `dependsOn` on default source sets). Mirrored in `TestCompilationRegistrar` for the test compilation.

  Real-world reproducer: `openMF/kmp-project-template` `core:database` module (`expect val platformModule: Module` in commonMain + actuals in `desktopMain` / `iosMain`). Every consumer using matrix mode + `expect`/`actual` in commonMain was affected — most realistic KMP libraries with DI factories, parcelize bridges, secure-settings, etc.

  New regression test: `MatrixModeExpectActualTest` covers both main + test compilation paths.

## [2.4.1] - 2026-05-18 — Scheduled cron patch (no substantive changes)

## [2.4.0] - 2026-05-17 — GA

> **GA cut.** Promoted from `2.4.0-rc.0` via proactive validation per [`docs/GA_READINESS_REPORT.md`](docs/GA_READINESS_REPORT.md) rather than reactive 1-week soak. Every `[2.4.0]` claim below cross-referenced with a test, workflow, or artifact — see the audit matrix in the report. Adopter signal live via `openMF/kmp-project-template:dev` pinned to `2.4.0-rc.0` (PR #152). Phase 4 outreach funnel ([Discussion #92](https://github.com/MobileByteLabs/kmp-product-flavors/discussions/92), `docs/v2.4-BETA-TESTING.md`, `v2.4-beta-stability-report.yml`) continues post-GA via the v2.4.x patch cadence.


> v2.3 + early-v2.4 cycle additions. **8 of 9 v2.3 phases + 4 of 5 v2.4 phases shipped 2026-05-15/16** in a single sweep per the "fix-all" session direction.

### Added — v2.3 phases

- **v2.3 Phase 6A cron safety-net** — `.github/workflows/auto-merge-bump-cron.yml` runs every 10 min; squash-merges open `chore/bump-version-*` PRs from `github-actions[bot]`. Closes the GitHub workflow-token-trigger-suppression limitation. Maintainer touch-points per release: 4 → 0.
- **v2.3 Phase 1 — Detekt per-target depth (opt-in)** — `kmpFlavors.detektPerVariantPerTarget: Property<Boolean>`. Registers `detekt{Variant}{Target}` per (variant × non-Android target) with per-target baselines.
- **v2.3 Phase 4 — Sonatype Snapshots channel** — nightly cron at 03:00 UTC publishes `{version}-SNAPSHOT` to Maven Central Portal's snapshot repo. See `docs/PUBLISHING.md` "Snapshot channel".
- **v2.3 Phase 7 — Per-variant Compose hot-reload Option A (opt-in)** — `kmpFlavors.composeHotReloadPerVariant`. Registers `composeHotReload{Variant}{Target}` per (inactive variant × JVM-family target).

### Added — v2.4 phases

- **v2.4 Phase 5 — Variant-conditional dependency excludes** — graduates v2.3 docs-only to full impl. New `VariantDependenciesScope.kt` + `KmpFlavorVariant.dependencies` field + `dependencies(Action)` DSL helper. Excludes scoped per-variant; each `KotlinCompilation`'s classpath gets the exclude rule applied.
- **v2.4 Phase 2 path-(b) — Cache namespacing impl** — graduates v2.3 stub to actual impl. Injects `kmpFlavorVariant` as `@Input` on every `compileKotlin*` task in matrix mode. Active-variant tasks namespace as "active". Prerequisite: `buildMatrix=true`. Default `false` until telemetry justifies the flip.
- **v2.4 Phase 3 — `switchVariantAndReload` task** — Option B best-effort workaround. `./gradlew switchVariantAndReload --to=<variant>` persists the new variant + prints the exact follow-up command. **Marked with `CMP-API-WAITING` markers** in 3 source locations + tracked by issue #75 — replace with daemon-restart-free impl when CMP exposes a public hot-reload reset API.
- **IDE plugin v0.2.0-alpha.1** (separate repo) — gutter icons + variant-aware Refactor → Rename + breakpoint scoping data layer. Published to Marketplace `eap` channel.
- **KMPF-V23 — `buildConfigField` name-collision validator** — surfaces the duplicate-`const val` regression at plugin-apply time instead of letting consumers hit Kotlin's "Conflicting declarations" at compile time. Reserved-name set is computed from the actual configuration (flavors + buildTypes + `VARIANT_NAME` + `BUILD_TYPE`). Discovered via `samples/multi-target-multi-variant/` — a flavor named `enterprise` + custom field `IS_ENTERPRISE` produced two `const val IS_ENTERPRISE` entries. See `docs/ERROR_CODES.md` for severity, message, and rename conventions.

### Fixed

- **Matrix mode + 6 non-Android targets** — the duplicate-BuildKonfig-codegen regression observed at `samples/multi-target-multi-variant/` creation time (PR #80) is **no longer reproducible** as of stability-plan investigation 2026-05-16. The 54-compilation matrix (3 flavors × 3 buildTypes × 6 non-Android targets: Desktop + JS + WasmJs + iOS X64 + iOS Arm64 + iOS Simulator Arm64) now builds clean via `./gradlew :samples:multi-target-multi-variant:assembleAllVariants` with `autoEnable=true` (default). The `autoEnable.set(false)` workaround has been removed from the sample. The `.github/workflows/sample-multi-target.yml` workflow now exercises `assembleAll{Desktop,Js,WasmJs}Variants` on Linux runners + `assembleAll{IosX64,IosArm64,IosSimulatorArm64}Variants` on macOS runners (27 inactive-variant compilations per slice) — locks in the green path so any future regression fails CI fast.

### Stable API surface (locked for 2.x cycle)

The following properties + DSL methods are committed to the v2.x SemVer contract. Breaking changes require a major-version bump.

- Core DSL: `flavors { register(…) }`, `buildTypes { register(…) }`, `flavorDimensions { register(…) }`, `variantFilter { … }`, `variants` (Q19-B public API).
- Top-level extension: `buildMatrix`, `publishMatrix`, `autoEnable`, `enableBuildTypes`, `generateBuildConfig`, `buildConfigPackage`, `buildConfigClassName`, `bridgeAgpProductFlavors`, `bridgeAgpBuildTypes`, `publishMatrixSbom`, `detektPerVariant`, `excludeGeneratedFromFormatters`, `dependencyGuardPerVariant`, `publishMatrixLegacyIosClassifiers`.
- Tasks: `compile{Variant}Kotlin{Target}`, `assembleAll{Target}Variants`, `assembleAllVariants`, `listFlavors`, `listActiveVariant`, `validateFlavors`, `generateRunConfigurations`, `publish{Variant}PublicationToMavenLocal`, `detekt{Variant}`.

### Experimental API surface (`@KmpFlavorsExperimental` annotation)

Source-annotated with `@KmpFlavorsExperimental(reason = "…")` — the reason string surfaces in IDE hover hints + KDoc. May change or be removed in v2.x point releases.

- `detektPerVariantPerTarget` — "Needs Phase 1 sample smoke (multi-target Detekt scope) before Stable promotion".
- `variantCacheNamespacing` — "Needs real cache-hit telemetry on 8+ variant modules to justify default-flip".
- `createIntermediateBuildTypeSourceSets` — "RFC §10 closer; KGP cross-tree dep warnings cosmetic but persistent".
- `npmPublishMatrix` — "Minimal real-world npm publish testing; needs adopter signal".
- `composeHotReloadPerVariant` — "Option A only; Option B graduation gated on CMP reset API (issue #75)".
- `promote(from, to, action)` — "Consumer demand signal unclear; DSL shape may evolve".
- `featureFlags { … }` — "SDK integration patterns may evolve as adopters wire it against real GrowthBook / Statsig / LaunchDarkly deployments".
- `KmpFlavorVariant.dependencies` — "Survey-gate-cleared via fix-all session, not consumer demand; DSL shape may evolve".

### Workaround API surface (`CMP-API-WAITING` markers)

Bridges a gap until an upstream API ships. Tagged in source with the comment marker `CMP-API-WAITING` + cross-linked to a tracking issue.

- `switchVariantAndReload --to=<variant>` task — workaround until CMP exposes the public hot-reload reset API (tracked at issue #75).

### Documentation

- **`docs/RELEASE.md`** — end-to-end release flow documentation.
- **`docs/COMPOSE_HOT_RELOAD.md`** — Option A + Option B-workaround consumer guide + "When CMP ships the public hot-reload reset API" migration section.
- **`docs/VARIANT_DEPENDENCY_EXCLUDES.md`** — graduated from v2.3 docs-only to shipped DSL reference.

### Dependencies

- **`mbl-actionhub` → `@v1.6.1`** — Phase 6D SemVer-pre-release bumper + 6E pre-release-aware GitHub Release flag.

### Tracking issues

- **#71** — Phase 3 (v2.3 era; now superseded by v2.4 Phase 5 ship). Closed.
- **#75** — CMP hot-reload reset API tracking (v2.4 Phase 3 graduation trigger). Open.

## [2.2.0] - 2026-05-15

> **v2.2 — fully-automatic + architecturally complete.** Closes RFC §10 (cross-variant intermediate source sets), every Phase 0 "consumer must manually opt-in" gap, and v2.1's 3 native-publishing deferrals (XCFramework / Package.swift / npm). Drop-in v2.1 → v2.2 upgrade for explicit-opt-in consumers; the master `kmpFlavors.autoEnable.set(false)` opt-out preserves v2.0/v2.1 semantics for shops that don't want the new auto-detection.

### Added

- **Phase 0 — Fully-automatic defaults** (11 of 12 sub-tracks; cross-repo bump-PR auto-merge cascade deferred).
  - `kmpFlavors.autoEnable: Property<Boolean>` master opt-out (default `true`).
  - **Auto-enable `buildMatrix`** when ≥2 non-Android targets + ≥2 flavors (Phase 0A).
  - **Auto-enable `publishMatrix`** when `maven-publish` applied + matrix mode on (Phase 0B).
  - **Auto-enable 3 adjacent-plugin helpers** (`dependencyGuardPerVariant` / `excludeGeneratedFromFormatters` / `detektPerVariant`) when their plugin is detected + matrix on (Phase 0C).
  - **Auto-flip `enableBuildTypes`** on first `buildTypes { register(…) }` (Phase 0D).
  - **CMP version detection** — KMPF-V14 WARNING when Compose Multiplatform < 1.7 (Phase 0E).
  - **Auto-canary** scheduled workflow against `openMF/kmp-project-template` weekly (Phase 0H).
  - **Deterministic codegen-host election** by lexicographic project path (Phase 0J).
  - **`kmpFlavorInit` sample-code generation** — drops `commonMain/Sample.kt` consuming BuildKonfig (Phase 0K).
  - **Compatibility-matrix validator** — KMPF-V15 (Apple Silicon Rosetta), KMPF-V16 (CMP × KGP), KMPF-V17 (KGP × Gradle) (Phases 0I + 0L).
- **Phase 1A — Cross-variant intermediate source sets** (RFC §10 closer). New `kmpFlavors.createIntermediateBuildTypeSourceSets: Property<Boolean>` opt-in creates `common{BuildType}` + `{target}{BuildType}` source sets. `KmpFlavorVariant.intermediateSourceSets: List<KotlinSourceSet>` exposes the per-variant set.
- **Phase 1B — Gradle 9 Project Isolation audit** — `ProjectIsolationCompatChecker` runs at apply() under Gradle 9.0+ AND `--project-isolation`; emits KMPF-V13 WARNING surfacing the codegen-claim cross-project-state violation. Nightly `project-isolation-check.yml` workflow audits the suite.
- **Phase 2A (Option B) — `listActiveVariant` task** + documented honest "Compose hot-reload still active-only" UX with `-PkmpFlavor=…` switch.
- **Phase 2B — Multi-KGP CI matrix workflow** — nightly cron against KGP 2.1 / 2.2 / 2.3 × pinned CMP versions (`.github/workflows/multi-kgp-matrix.yml` + `gradle/kgp-matrix.toml`).
- **Phase 3A — Build Scan per-variant tagging** — `BuildScanConfigurator` reflectively attaches `kmpFlavors.variant` + `kmpFlavors.target` Develocity custom values to per-variant compile tasks.
- **Phase 3B — Per-variant SBOM** — `PerVariantSbomConfigurator` attaches CycloneDX SBOM artifacts to per-variant MavenPublications. Opt-in via `kmpFlavors.publishMatrixSbom.set(true)`.
- **Phase 4A — Variant promotion DSL** — `kmpFlavors.promote(from, to) { applyTransform("renamePackage", … to …) }`. Registers `promote{From}To{To}` task per declared promotion with `-Pdry-run=true` preview support.
- **Phase 4B — Per-variant feature-flag hooks** — `kmpFlavors.featureFlags { growthbook { defaultPayload.set(file("flags/growthbook.json")) } }`. Generates per-variant `FeatureFlags.kt` consumed by GrowthBook / Statsig / LaunchDarkly SDKs at runtime. Per-variant override via `flags/<platform>.<variant>.json`.
- **Phase 5A — Per-variant XCFramework aggregation (iOS)** — `PerVariantIosXcframeworkConfigurator` registers Framework binaries linked to per-variant compilations + aggregates into XCFramework via reflective KGP-Apple API. Classifier-tagged MavenPublication: `coordinate:1.0.0:{variant}-xcframework`.
- **Phase 5B — Per-variant `Package.swift` (SPM)** — `KmpFlavorPlugin` registers one `generate{Variant}SpmManifest` task per variant in matrix mode. Output: `build/spm/{variant}/Package.swift`.
- **Phase 5C — Per-variant npm registry publishing (opt-in)** — `PerVariantNpmPublishConfigurator` generates per-variant `package.json` + Tar tarball (.tgz). Opt-in via `kmpFlavors.npmPublishMatrix.set(true)`. Configurable `kmpFlavors.npmPackagePrefix`.
- **`kmpFlavors.publishMatrixLegacyIosClassifiers: Property<Boolean>`** (default `true`) — keeps v2.1's Zip-shaped iOS MavenPublications as deprecation aliases during the migration window. Flip to `false` once consumers move to `:{variant}-xcframework` coordinates.

### Tests

- 11 new TestKit / ProjectBuilder cases across Phase 0 + Phase 1A. Phase 5 + Phase 4B paths are best validated via consumer adoption canaries + macOS-runner end-to-end (XCFramework + SPM); Develocity / CycloneDX configurators tested only at the no-op guard layer (full integration blocked by TestKit classloader isolation, same as CMP).
- ci-prepush 11/11 green at every phase boundary.

### Known limitations

- **Phase 0F (auto-merge bump PR cascade)** — deferred to a separate `mbl-actionhub-bump-version` cross-repo session.
- **Phase 2A Option A (true per-variant Compose hot-reload)** — Option B's CLI-switch UX ships here; full per-variant hot-reload is v2.3+ scope pending CMP hot-reload task-graph research.
- **Phase 6 — IDE plugin v0.1** — 🚢 published to [JetBrains Marketplace](https://plugins.jetbrains.com/plugin/31779-kmp-product-flavors) 2026-05-15 as part of the v2.3 cycle (was deferred from v2.2). Source: [`MobileByteLabs/kmp-product-flavors-ide-plugin`](https://github.com/MobileByteLabs/kmp-product-flavors-ide-plugin).
- **KGP "Invalid Source Set Dependency Across Trees" warnings** on Phase 1A's variant→commonBuildType edges remain cosmetic — proper fix via KGP's `applyHierarchyTemplate` API is v2.2 beta polish.

### Compatibility

- **No breaking changes** for v2.0 / v2.1 consumers who set `kmpFlavors.autoEnable.set(false)`.
- **For default consumers** (with `autoEnable=true`, the v2.2 default): matrix mode auto-fires on modules with ≥2 non-Android targets + ≥2 flavors; per-variant publications auto-register when `maven-publish` is applied; the 3 Phase 4 adjacent-plugin helpers fire when their plugin is detected. Set `autoEnable.set(false)` to opt out at once, OR override individual flags via explicit `set(false)`.
- Minimum KGP: 2.1+ (tested against 2.2.21 + 2.3.0; nightly multi-KGP matrix covers 2.1.0 / 2.2.21 / 2.3.0).
- Minimum Gradle: 8.5+ (tested 9.0; Project Isolation strict mode emits KMPF-V13 informational warning).
- Minimum JDK: 17+.

## [2.1.0] - 2026-05-14

> **v2.1 — AGP-parity-complete on the Kotlin side.** Closes the deliberate v2.0 deferrals: per-variant test compilations, per-variant resources (CMP + Android), validator hardening (V02/V03/V06/V07), IDE Run Configurations × target, Detekt-per-variant, dependency-guard / Spotless helpers, and native per-variant publishing (iOS klib + JS/WasmJs). No breaking changes for v2.0 consumers — `2.1.0` is a drop-in pin bump.

### Added

- **`diagnoseVariant` task** (RFC §3 Q22) — `./gradlew :module:diagnoseVariant --variant freeDev` prints source-set tree, target list, BuildConfig fields, and active-filter count for one variant. `--json` flag for CI consumption. Configuration-cache friendly.
- **`listVariantCompilations` task** (RFC §3 Q13) — Markdown table of the full variant × target compilation matrix with ACTIVE/inactive status per row.
- **`generateVariantRunConfigurations` task** (RFC §3 G22) — one `.run.xml` per (variant × target) under `.run/`. Active variant invokes `compileKotlin{Target}`; inactive variants invoke `compile{Variant}Kotlin{Target}`. Sibling to v2.0's `generateRunConfigurations`.
- **Per-variant test compilations** (RFC §3 Q10) — matrix mode now registers `compile{Variant}TestKotlin{Target}` per inactive variant × target via `TestCompilationRegistrar`. Per-variant test code can call variant main's `internal` declarations via `associateWith`; cross-variant test isolation holds.
- **Per-variant Compose resources** — `composeResources/` directories under per-flavor source sets (`src/commonFree/composeResources/...`) are auto-discovered by Compose Multiplatform v1.7+. Leaf source set wins on duplicate keys. Active variant sees `commonMain + commonActiveFlavor`; inactive variants see `commonMain + commonInactiveFlavor`. Cross-variant isolation preserved.
- **Per-variant Android resources via AGP bridge** — `src/{flavor}/res/values/strings.xml` overrides work unchanged from v1.x. The AGP bridge propagates flavors into AGP's `productFlavors` so AGP's native per-flavor `res/` discovery applies.
- **`KmpFlavorPluginValidator` codes V02/V03/V06/V07** — `FLAVOR_MISSING_DIMENSION` (ERROR), `DIMENSION_HAS_NO_FLAVORS` (ERROR; migrated from `FlavorVariantResolver`'s `IllegalStateException`), `UNKNOWN_ACTIVE_VARIANT` (WARNING — `-PkmpFlavor` is project-wide in multi-project builds), `INVALID_BUILD_CONFIG_FIELD_TYPE` (ERROR; supported set is `Boolean / Int / Long / Float / Double / String`). V04 gated against V03 to prevent double-fire on empty matrix.
- **`kmpFlavors.dependencyGuardPerVariant: Property<Boolean>`** (opt-in) — auto-registers one `dependencyGuard.configuration(...)` entry per (variant × target). Closes Q24's documented "consumer must add baselines manually" caveat.
- **`kmpFlavors.excludeGeneratedFromFormatters: Property<Boolean>`** (opt-in) — auto-excludes the per-variant codegen output path from Spotless + Detekt globs. Closes Q24's "watch source-set scope" caveats.
- **`kmpFlavors.detektPerVariant: Property<Boolean>`** (opt-in) — registers one `detekt{Variant}` task per variant with per-variant baselines at `config/detekt/{variant}/baseline.xml`. Equivalent UX to AGP's "Lint per variant" for non-Android targets — the most-requested adjacent-plugin gap on v2.0 alpha feedback.
- **Per-variant iOS publishing** (RFC §3 Q21-D extension) — `PerVariantIosPublishConfigurator` registers a classifier-tagged Zip + MavenPublication per (inactive variant × iOS target). Consumers resolve via `coordinate:1.0.0:paid-iosArm64`.
- **Per-variant JS / WasmJs publishing** — `PerVariantJsPublishConfigurator` registers classifier-tagged publications for `js(IR)` and `wasmJs()` targets. Consumers resolve via `coordinate:1.0.0:paid-js` or `coordinate:1.0.0:paid-wasmJs`.
- **`docs/PUBLISHING.md`** — consumer reference for per-variant publishing across JVM / iOS / JS / WasmJs. Documents v2.1 scope vs v2.2 deferrals (XCFramework aggregation, per-variant Package.swift, npm registry publishing).
- **`docs/MATRIX_MODE.md` updates** — new "Per-variant resources" section; Q24 adjacent-plugin compat table fully ✅ (no ⚠ rows remain); CLI cheat sheet entry for `generateVariantRunConfigurations`; `publishMatrix` row points at `PUBLISHING.md` for the full target catalog.

### Tests

- 35 new tests across Phases 1–5: `KmpFlavorPluginValidatorTest` (+8 cases for V02/V03/V06/V07), `DiagnoseVariantTaskTest` (5 TestKit), `ListVariantCompilationsTaskTest` (2 TestKit), `PerVariantTestCompilationTest` (3 TestKit), `PerVariantComposeResourcesTest` (3 ProjectBuilder + 1 `@Disabled` TestKit), `Phase4HelpersTest` (8 ProjectBuilder), `GenerateVariantRunConfigurationsTaskTest` (3 TestKit), `PerVariantNativePublishingTest` (4 TestKit), `FlavorVariantResolverTest` migrated (throws → returns-empty). Full suite green; `./ci-prepush.sh` 11/11 green at every phase boundary.

### Known limitations

- **Per-variant Compose hot-reload** — still active-variant only. Bumped to **v2.2 Phase 2A**.
- **Per-variant XCFramework aggregation** — deferred to v2.2. v2.1's iOS scope ships the publishing surface (klib Zip + MavenPublication); consumers wire `XCFramework()` aggregation manually if needed. See `docs/PUBLISHING.md` for the consumer-side workaround.
- **Per-variant Package.swift (SPM)** — deferred to v2.2 (depends on XCFramework above). The existing `GenerateSpmManifestTask` ships single-variant SPM unchanged.
- **npm registry publishing** — intentionally consumer-side per the v2.1 plan risk register. The plugin produces the classifier-tagged Maven publication; consumers wire their `~/.npmrc` and `kotlinNpmPublishToRegistry` separately.
- **CMP integration TestKit** — `@Disabled` due to `withPluginClasspath()` classloader isolation (`Could not find KotlinMultiplatformExtension`). End-to-end verification of per-variant Compose resources delegated to `samples/compose-multiplatform/` and consumer adoption canaries.

### Compatibility

- **No breaking changes for v2.0 consumers**: v2.1.0 with `buildMatrix.set(true)` left untouched is behaviourally identical to v2.0.0 except for the 4 new validator codes — V02/V03/V07 are ERRORs and will fail builds that were silently malformed in v2.0; V06 is a WARNING. Mitigation: V02/V03/V07 conditions were already broken in v2.0 (just less clearly diagnosed); fixing the underlying configuration restores compatibility.
- Minimum KGP: 2.1+ (tested against 2.2.21 and 2.3.0).
- Minimum Gradle: 8.5+ (tested against 9.5).
- Minimum JDK: 17+.
- For per-variant Compose resources: minimum Compose Multiplatform 1.7.0+. v2.1 ships with light-touch CMP-version detection; older versions still work but per-variant resource auto-discovery may be incomplete.

## [2.0.0-alpha.1] - 2026-05-14

> **Matrix mode** ships. Build every variant × every non-Android KMP target in one Gradle invocation, AGP-style. Opt-in (`kmpFlavors.buildMatrix=true`), with **zero per-module `build.gradle.kts` change** required from consumers (Zero-Touch Adoption tenet, RFC §1.1).
>
> v1.x active-variant-only behaviour is preserved when matrix mode is off (the default). No breaking changes for v1.x consumers — `2.0.0-alpha.1` should be a drop-in version bump if `buildMatrix` is left untouched.

### Added

- **`kmpFlavors.buildMatrix: Property<Boolean>`** — single-point opt-in for matrix mode (RFC §3 Q5-A, Q16-C). Hybrid resolution: extension > `gradle.properties: kmpFlavors.buildMatrix=true` > `false`.
- **Per-variant `compile{Variant}Kotlin{Target}` tasks** — KGP-auto-generated from `compilations.create("{variantName}")` for every inactive variant × non-Android target (JVM, iOS Native, JS IR, WasmJs all exercised end-to-end). Active variant continues to compile through the standard `compileKotlin{Target}` task (RFC §3 Q1-B, Q4).
- **Per-variant `KotlinSourceSet` hierarchy wiring** — variant compilations `dependsOn` the existing per-flavor source sets (commonFree, commonPaid, etc.) via the standard KMP source-set DAG, so `expect`/`actual` (Q11) works across variants and cross-variant isolation (Q12) holds.
- **Per-variant dependencies** — declaring `commonPaid { dependencies { implementation("...") } }` propagates to the paid variant's compileClasspath only; active and other-flavor compilations don't see it (RFC §3 Q17).
- **`generate{Variant}BuildConfig` tasks** — one per inactive variant. Output at `build/generated/kmpFlavors/{variantName}/kotlin/…`. Per-variant `IS_<FLAVOR>` / `IS_<BUILDTYPE>` constants + per-variant `buildConfigField` values (RFC §3 Q3-A).
- **`assembleAll{Target}Variants` per target + `assembleAllVariants` super-aggregate** — CI matrix-sharding and dev-convenience entry points. Both in the `kmpFlavors variants` task group (RFC §3 Q18-C, Q9).
- **`kmpFlavors.variants: NamedDomainObjectCollection<KmpFlavorVariant>`** — public variant API. `name / flavors / buildType / targets / compilations` per element. Consumers use standard `matching { … }.configureEach { … }` mechanics for per-variant customisation (RFC §3 Q19-B).
- **`variantFilter { setIgnore(true) }`** — AGP-style synonym for the existing `exclude()`. `VariantFilter` gained `buildType: String?` so the canonical example `if (flavors.any { it.name == "paid" } && buildType == "staging") setIgnore(true)` works (RFC §3 Q20-A).
- **`kmpFlavors.publishMatrix: Property<Boolean>`** — opt-in for per-variant Maven publishing. When `true` and `maven-publish` (or `com.vanniktech.maven.publish`) is applied, registers a `MavenPublication("variant{X}")` per inactive variant × JVM target with a classifier-tagged Jar. Standard `publishVariant{X}PublicationTo{Repo}` tasks derived by Gradle (RFC §3 Q21-D).
- **`KmpFlavorPluginValidator`** — fail-fast configuration validation with stable error codes `KMPF-V01` (flavor/buildType collision), `KMPF-V04` (variantFilter excluded all), `KMPF-V05` (matrix-on with zero targets, WARNING), `KMPF-V08` (matrix-on with zero flavors). Each finding carries code + severity + message + concrete fix. ERRORs throw `GradleException`; WARNINGs go to `logger.warn`. Full catalog: `docs/ERROR_CODES.md`. (RFC §3 Q23)
- **`samples/matrix-mode/`** — end-to-end reference sample exercising every consumer surface: 4-variant matrix (2 flavors × 2 buildTypes), per-flavor `expect`/`actual`, per-variant deps (kotlinx-coroutines-core on commonPaid), variant API, variantFilter excluding `paidRelease`, per-variant publishing, per-variant `BuildKonfig.kt`. Run `./gradlew :samples:matrix-mode:assembleAllVariants` to exercise.
- **`docs/MATRIX_MODE.md`** — consumer reference for matrix mode (TL;DR opt-in, what gets added, Q24 adjacent-plugin compatibility table, exclusion rules, KMPF-Vxx quick reference).
- **`docs/ERROR_CODES.md`** — KMPF-Vxx catalogue (V01/V04/V05/V08 shipped; V02/V03/V06/V07 marked pending).
- **`docs/RFC-v2.0-per-variant-compilation.md`** — design RFC (sealed 2026-05-13 via [#44](https://github.com/MobileByteLabs/kmp-product-flavors/pull/44); merged squash `ea486d1`). 26 design questions answered with provisional defaults; 24 dispositioned gaps in §3.5; live spike measurements inline for Q4/Q7/Q8.

### Tests

- 21 new tests landed across W1-W5: `MatrixModeResolverTest`, `CompilationRegistrarTest`, `KmpFlavorPluginValidatorTest`, `MatrixModeJvmRegistrationTest`, `EdgeCaseMatrixTest`, `MultiTargetMatrixRegistrationTest`, `VariantApiTest`, `AggregateVariantTasksTest`, `VariantFilterDslTest`, `BuildTimeBenchmarkTest`, `ExpectActualMatrixTest` (Q11), `CrossVariantIsolationTest` (Q12), `PerVariantDependencyClasspathTest` (Q17), `PerVariantBuildConfigTest` (Q3-A), `PerVariantPublishingTest` (Q21-D), `ConfigCacheCompatibilityTest` (Q7 ≥95% hit-rate SLO), `AdjacentPluginCompatTest` (Q24 — vanniktech case `@Disabled` due to TestKit classpath isolation; real verification in the `samples/matrix-mode/` sample). Total: 146 tests / 1 skipped / 0 failures.

### Known limitations

- **Per-variant publishing is JVM-only at alpha.1.** iOS/JS/WasmJs per-variant publishing has KMP-specific complications (per-target XCFramework bundling on iOS) and is deferred to a v2.0 post-GA follow-up if survey demand justifies the work.
- **Compose Multiplatform hot-reload is active-variant only** at alpha.1. Per-variant hot-reload is v2.1 scope (RFC §3 Q24).
- **vanniktech.maven-publish + TestKit**: TestKit classpath isolation can't satisfy vanniktech's `KotlinBasePlugin` import, so the smoke test is `@Disabled`. The compat is real — vanniktech delegates to `maven-publish`, and our `withId("maven-publish")` hook fires transitively. Verified in the `samples/matrix-mode/` sample.

### Compatibility

- **No breaking changes for v1.x consumers**: v2.0.0-alpha.1 with `buildMatrix` left untouched is behaviourally identical to v1.1.7.
- Minimum KGP: 2.1+ (alpha tested against 2.2.21).
- Minimum Gradle: 8.5+ (alpha tested against Gradle 9.5).
- Minimum JDK: 17+.
- Per RFC §3 Q15, v1.x continues to receive critical-fix releases for 6 months after v2.0 GA.

## [1.1.6] - 2026-05-13

Docs + tooling release. No plugin-source behaviour change vs. 1.1.5 — adopters can bump the pin without code changes.

### Added

- **`docs/ROLLBACK.md`** — rollback strategy if a downstream app hits a v1.1.5/v1.1.6 regression. Documents recovery target (v1.1.0), "what's NOT a regression" matrix (KLIB warnings, Compose 1.10 Preview deprecation noise), and a catastrophic-rollback composite-include fallback. Notes that v1.1.1–v1.1.4 were mavenLocal-only iteration markers.
- **`scripts/plugin-ci-prepush.sh`** — local CI-parity script mirroring `.github/workflows/pr-check.yml`. Runs `spotlessCheck`, flavor-plugin compile + test, `basic-flavors` matrix (free/paid × dev/staging), and the `kmp-project-template` sample BuildKonfig assertions. Surfaces every CI failure in 2-4 min locally instead of waiting on the runner queue.
- **Plugin test coverage for `codegenHost`** — `ConventionPluginIntegrationTest.codegenHost set false opts module out of codegen entirely` verifies the opt-out path.

### Fixed

- **Sample `kmp-project-template/cmp-shared` source code references `BuildKonfig` (not `FlavorConfig`)** — `samples/kmp-project-template/cmp-shared/src/commonMain/kotlin/cmp/shared/flavor/AppVariant.kt` was the last remaining file in the sample still importing `org.openmf.kmptemplate.FlavorConfig`. Renamed to `BuildKonfig` to match the v1.1.5 default class name. `pr-check.yml`'s "kmp-project-template sample build" job now passes.

### CI / Operations

- **`.github/workflows/publish-release.yml`** — added a `preflight` job that runs before `publish` and verifies, via `gh run list --workflow "PR Check" --commit $HEAD_SHA --status success`, that at least one successful PR Check run exists on the head SHA. Fails fast with a clear error if not, blocking publish of un-validated commits. Requires `actions: read` permission (added).

## [1.1.5] - 2026-05-12

Zero-config release. Downstream KMP consumers can adopt with **no workaround toggles** — the API is now the extension config + flavor/buildType DSL only.

### Added

- **`codegenHost: Property<Boolean>`** on `KmpFlavorExtension` — explicit deterministic codegen-host designation for multi-module builds. `null` (default) keeps the auto-claim behaviour; `set(true)` forces this module to win the claim regardless of configuration order; `set(false)` opts the module out of codegen even if it would have claimed.
- **Idempotent AGP bridge** — `AgpBridge.apply()` now detects when AGP `productFlavors` / `buildTypes` are already populated (e.g. via a consumer convention plugin\'s synchronous `pluginManager.withPlugin(...)` registration). If the existing set is a superset of the KMP flavor/buildType names, the bridge logs an info-level "no-op" and returns silently. Real conflicts still warn.
- **Default `buildConfigClassName` is now `"BuildKonfig"`** (was `"FlavorConfig"`). Aligns with Kotlin Multiplatform ecosystem naming.
- **Default `bridgeAgpProductFlavors.convention(true)` + `bridgeAgpBuildTypes.convention(true)`** — now safe because of the idempotency fix above. Consumers no longer need to set these to `false` to avoid duplicate-flavor warnings.

### Fixed

- **Multi-module `BuildKonfig` codegen** (resolves DEX merge duplicate-class error). Convention plugins that auto-apply `kmp.flavors` across every module previously caused each module to generate its own `<package>.BuildKonfig.kt`. New rootProject-extras claim ensures only one subproject generates the class; subsequent applications log info and skip via `KmpFlavorPlugin.shouldGenerateCodegen()`.
- **Lazy per-flavor source-set creation** in `SourceSetConfigurator`. Previously created `commonProd`, `iosDemoTest`, `androidProdTest`, etc. eagerly even when inactive and empty. KMP reported them as "Unused Kotlin Source Sets" — 19 warnings per module in a typical 2-dimension setup. Now `maybeCreateLazy()` only creates source sets when (a) the flavor is active, OR (b) the on-disk `src/<name>/{kotlin,resources}` directory contains files. Test source sets always require on-disk content even for the active flavor.
- **Web intermediate source-set wiring** in `PlatformDetector.wireIntermediateSourceSets()`. The previous `wireIfMissing` probe for `webMain → commonMain` and `js/wasmJs/wasmWasi → webMain` ran before Kotlin 2.1+\'s default hierarchy template installed its edges, producing spurious "Redundant dependsOn Kotlin Source Sets" warnings. The explicit web wiring is now removed — the hierarchy template owns those edges. The plugin only registers `src/webMain/{kotlin,resources}` directories.

### Build

- **Kotlin 2.0 metadata compatibility** for the plugin main source set (`languageVersion` + `apiVersion` capped to `KOTLIN_2_0`). The plugin can now be consumed by builds whose `kotlin-dsl` is on the embedded Kotlin 2.0.x compiler (Gradle <9.5). Source-level features don\'t depend on Kotlin 2.1+; the cap is metadata-only.

### Adoption API (after v1.1.5)

```kotlin
extensions.configure<KmpFlavorExtension> {
    buildConfigPackage.set("com.your.app")
    enableBuildTypes.set(true)
    flavorDimensions { register("contentType") { priority.set(0) } }
    flavors { ... }
    buildTypes { ... }
}
```

No `generateBuildConfig.set(false)`. No `createIntermediateSourceSets.set(false)`. No `bridgeAgp*.set(false)`. No `buildConfigClassName.set(...)`.

For deterministic codegen-host designation in multi-module projects, the designated host module (e.g. `:cmp-shared`) adds:

```kotlin
extensions.configure<KmpFlavorExtension> {
    codegenHost.set(true)
}
```

### Reference adoption

`openMF/kmp-project-template#141` (merged 2026-05-12) — full end-to-end adoption with the v1.1.5 API. Canonical example for downstream consumers (mifos-mobile, mifos-pay, mifos-x-field-officer-app, mifos-x-group-banking, mifos-x-open-banking, reels-downloader-new).

### Migration from earlier versions

Delete the following lines from your consumer\'s `KMPFlavorsConventionPlugin` if present:

```kotlin
generateBuildConfig.set(false)          // multi-module is now auto-handled
createIntermediateSourceSets.set(false) // web wiring delegated to Kotlin hierarchy template
bridgeAgpProductFlavors.set(false)      // bridge is idempotent now
bridgeAgpBuildTypes.set(false)
buildConfigClassName.set("FlavorConfig") // default is now "BuildKonfig"
```

And from `gradle.properties`:

```properties
kotlin.suppressGradlePluginWarnings=UnusedSourceSetsWarning  # plugin no longer creates unused source sets
```

## [1.1.2] - 2026-05-11

### Fixed

- **F1** `enableBuildTypes` was declared on `KmpFlavorExtension` but never read anywhere in the plugin — setting it had zero effect. Now wired through `FlavorVariantResolver` so the variant matrix expands by buildType axis (e.g. 8 flavors × 3 buildTypes = 24 variants) when `enableBuildTypes.set(true)` and at least one `buildTypes { register(...) }` block is declared.
- **F2/F3** `GenerateBuildConfigTask` now emits `BUILD_TYPE: String` plus `IS_<BUILDTYPE>: Boolean` constants per declared buildType, AND merges the active buildType's `buildConfigField(...)` entries into the generated `FlavorConfig.kt`. Previously these declarations were silently dropped.
- **F5** `PlatformDetector.wireIntermediateSourceSets()` no longer triggers the Kotlin compiler's "Redundant dependsOn Kotlin Source Sets" warning. Web intermediate edges (`webMain → commonMain`, `js/wasmJs/wasmWasi → webMain`) are now added only when not already present in the source set's transitive dependsOn chain (Kotlin 2.1+'s default hierarchy template already adds them).
- **F8** `SourceSetConfigurator` now wires the active flavor's source sets onto each platform's main compile path. Previously the plugin created `commonInternal/`, `desktopInternal/` etc. source sets and wired their internal dependsOn chain, but **never made them reachable from `compileKotlin<Target>` compilations** — so `expect`/`actual` flavor splits silently failed with "no actual declaration in module <commonMain>". v1.1.2 adds `wireIfMissing(platformMain, commonFlavor)` and `wireIfMissing(platformMain, platformFlavor)` for the active flavor, making actual declarations under `src/commonInternal/kotlin` and `src/desktopInternal/kotlin` reachable from desktop/JS/Wasm/iOS compilations.
- **F6** Eliminated two long-standing compiler warnings in `ValidateFlavorsTask.kt:113,128` ("Condition is always 'false'/'true'"). The redundant null checks on `MapProperty<String, String>` values were replaced with `String.isEmpty() / isNotEmpty()` checks consistent with the convention that empty string = "no dimension".

### Notes

This is a P0 hotfix. v1.1.0 and the unintentionally-republished v1.1.1's `buildTypes { … }` DSL was accepted but completely inert — variant naming, source-set wiring, codegen, and the AGP bridge handoff all ignored it. v1.1.1 makes the documented behaviour real.

Driving plan: `plan-layer/plans/PLAN-v1.1.0-validation-260511-100908.md`
Validation report: `plan-layer/plans/VALIDATION_REPORT-v1.1.0-260511.md`

### Migration

If you were on v1.1.0 and your `kmpFlavors { }` block had a `buildTypes { … }` declaration:

1. Add `enableBuildTypes.set(true)` to your `kmpFlavors { }` block (still defaults to `false` for backwards compatibility with consumers that ship a `buildTypes` block but didn't realise it was inert).
2. Your variant names will now include the buildType suffix (`freeDebug`, `paidRelease`). Update any `-PkmpFlavor=foo` invocations accordingly.
3. `FlavorConfig` will gain `BUILD_TYPE`, `IS_<BUILDTYPE>`, and any per-buildType `buildConfigField` entries.

## [1.1.0] - 2026-05-10

### Added

- **G6** AGP bridge implementation — `bridgeAgpProductFlavors` and `bridgeAgpBuildTypes` properties on `kmpFlavors {}`. When `true` and the consumer applies `com.android.application`, KMP flavor dimensions, flavors, and build types are propagated into AGP's `ApplicationExtension`, carrying `applicationIdSuffix` and `versionNameSuffix`. Reflection-based — no compile-time AGP dependency added to the plugin.
- Defensive behaviour: when AGP `productFlavors` or `buildTypes` are already populated by the consumer's hand-written DSL, the bridge logs a warning and skips propagation rather than silently overriding.
- `AgpBridgeTest` covering (a) no-op when both flags disabled, (b) silent skip when `com.android.application` not applied (v1.1.0 scope; library + KMP-library variants land in v1.2.0).
- **G7** SPM iOS framework distribution helper — new `kmpFlavors { spm { … } }` DSL block and `generateSpmManifest` Gradle task that writes a per-flavor `Package.swift` under `build/spm/<variant>/`. Two distribution modes: `LOCAL` (path-based) and `REMOTE` (URL + SHA-256 checksum with `{flavor}` / `{variant}` / `{version}` placeholder interpolation). Three checksum strategies: `AUTO` (sidecar then sha256-on-the-fly), `REQUIRE_FILE` (sidecar mandatory), `SKIP` (placeholder for samples / smoke tests). Linux-CI compatible — task generation runs in pure JVM with no Xcode toolchain dependency.
- New `docs/IOS_DISTRIBUTION.md` — SPM-first guidance, explicit "CocoaPods is deprecated and unsupported" note, distribution-mode + checksum-strategy reference, roadmap.
- `GenerateSpmManifestTaskTest` — three string-match cases covering LOCAL path emission, REMOTE placeholder interpolation, and REMOTE failure when `binaryUrlTemplate` is unset.
- **G8** Per-flavor compose-resources documentation — new section in `docs/PRODUCT_FLAVORS.md` showing how Compose Multiplatform `composeResources/` directories merge by flavor (e.g. `commonDemo/composeResources/drawable/logo.png` overrides `commonMain/composeResources/drawable/logo.png` for the `demo` variant).
- **G9** Per-flavor test source-set creation — `SourceSetConfigurator` now also creates `common<Flavor>Test` and `<platform><Flavor>Test` source sets (mirroring `*Main` structure) when `commonTest` exists. Plugin-side wiring rule: `<flavor>Test` `dependsOn(commonTest)` ONLY when active, so non-active flavor test sources never reach the test classpath of another variant.
- **G18** `InitFlavorSourceSetsTask` — new `createReadmePerSourceSet` property (default `true`). Each generated flavor source directory now gets a per-source-set `README.md` explaining what code belongs there, with a link back to `docs/PRODUCT_FLAVORS.md`. Replaces the previous "empty dir + .gitkeep" first-time-user experience.
- **G19** `KmpFlavorPluginIntegrationTest` — new case asserting the plugin gracefully no-ops on a Java-only project (no KMP plugin applied). Plugin emits a `WARN` log and returns silently rather than crashing.
- **G10** `matchingFallbacks` propagation through the AGP bridge — when `bridgeAgpProductFlavors.set(true)`, per-flavor `matchingFallbacks(...)` declarations now flow into AGP's `productFlavor.matchingFallbacks` automatically. No separate `android { productFlavors { … } }` block needed.
- **G11** New `docs/VARIANT_FILTERS.md` — practical recipes for `variantFilter { … }` (excluding impossible combinations, time-boxed pilots, per-buildType filters) and `matchingFallbacks(...)` (single, chained, AGP-bridged). Includes a decision tree mapping symptoms → fixes.
- **G12** `samples/README.md` — new Capability Matrix table mapping every plugin capability to which sample demonstrates it. Plus a Roadmap section listing planned v1.1.1 samples (`spm-distribution`, `full-apple-targets`) and v1.2.0 samples (`multi-module`, `library-with-flavors`, `android-resources-per-flavor`).

### Added

- **G1 / G2** Apple-family target detection extended: tvOS (`tvosX64`, `tvosArm64`, `tvosSimulatorArm64`) and watchOS (`watchosX64`, `watchosArm64`, `watchosSimulatorArm64`, `watchosDeviceArm64`). Each gets a flavor-aware source set wired under `nativeMain`.
- **G3** WasmWasi target detection (`wasmWasi`) — joins `js` + `wasmJs` under the `webMain` intermediate.
- **G4** Android Native target detection (`androidNativeArm64`, `androidNativeX64`, `androidNativeArm32`, `androidNativeX86`) — for server-side native binaries on Android NDK toolchains.
- `PlatformDetectorTest` — five new test cases covering the four new target families plus a regression test asserting `androidNativeArm64` is not confused with the `android` target.



### Changed

- Integration doc capability index promotes `bridgeAgpProductFlavors` / `bridgeAgpBuildTypes` from 🟡 Planned to active capabilities.

### Documentation

- README "Supported Platforms" table promotes tvOS / watchOS / wasmWasi / androidNative from 🟡 Planned to ✅ Detected. Compatibility Matrix updated with `1.1.0` row.

### Notes

Decision **D2** resolved: Kotlin's `applyDefaultHierarchyTemplate()` (already invoked at `PlatformDetector.kt:139`) owns `appleMain` — the plugin does **not** call `maybeCreate("appleMain")`, only adds flavor-axis intermediates on top. This keeps changes additive (no breaking source-set hierarchy change) → v1.1.0 minor remains correct, no v2.0.0 needed.

Driving plan: `plan-layer/plans/PLAN-gaps-fix-260510-191003.md` Phases B + C.

## [1.0.5] - 2026-05-10

### Fixed

- **G5** Doc-vs-source version drift: `docs/KMP_PROJECT_TEMPLATE_INTEGRATION.md` now references `1.0.5` (was `0.1.0`); `samples/convention-integration/` and `samples/kmp-project-template/` `gradle/libs.versions.toml` synced.
- **G13** Wrong plugin id in `KmpFlavorPlugin.kt` Kdoc — `io.github.anthropic.kmp-product-flavors` → `io.github.mobilebytelabs.kmp-product-flavors`.

### Documentation

- **G14** Documented `desktopTitleSuffix` / `webTitleSuffix` per-flavor properties in integration guide ("Configuration Extras").
- **G15** Documented `-PkmpFlavor=…` Gradle property override.
- **G16** Documented `afterEvaluate` ordering pitfall and plugin-application order in integration guide ("Pitfalls").
- **G20** Added Kotlin / AGP / Gradle / JDK / Compose Multiplatform compatibility matrix to README.
- README "Supported Platforms" table now reflects what `PlatformDetector.kt` actually recognises today (tvOS, watchOS, wasmWasi, androidNative are explicitly marked 🟡 Planned for v1.1.0).
- Cross-linked `PRODUCT_FLAVORS.md` ↔ `BUILD_VARIANTS.md` ↔ `KMP_PROJECT_TEMPLATE_INTEGRATION.md`.
- Capability index in integration doc marks `bridgeAgpProductFlavors` and `bridgeAgpBuildTypes` as 🟡 Planned (v1.1.0) until implemented.

### CI

- **G17** New workflow `.github/workflows/doc-consistency.yml` enforces (a) Kdoc plugin id matches `gradlePlugin` registration, (b) no stale `0.1.0` version refs in versioned doc/sample files.

### Notes

This is a doc-only release — no code or DSL behaviour changes. Driving plan: `plan-layer/plans/PLAN-gaps-fix-260510-191003.md` Phase A.

## [1.0.1] - 2026-02-25

### Fixed

- Fixed source set dependency warnings ("Invalid Dependency on Default Compilation Source Set")
- Platform flavor source sets now correctly depend only on commonFlavor (not platformMain)
- Intermediate flavor source sets no longer depend on compilation default source sets

### Changed

- Renamed `kmp-template-integration` sample to `kmp-project-template`
- Improved convention plugin integration in kmp-project-template sample

### Documentation

- Updated build-logic README with comprehensive KMP flavors documentation
- Added detailed integration guide in samples README
- Documented automatic kmp.flavors application in convention plugins

## [1.0.0] - 2026-02-25

### Changed

- Stable release - all features from alpha are now stable
- Updated Kotlin to 2.2.21
- Updated AGP to 8.12.3
- Updated Compose Multiplatform to 1.9.3

### Added

- **Variant filtering** - Exclude specific variant combinations
- **matchingFallbacks** - Dependency resolution fallback support
- **kmpFlavorInit task** - Initialize source directories
- **Platform-specific suffixes** - applicationIdSuffix, bundleIdSuffix, desktopWindowTitleSuffix, webTitleSuffix
- **Build types support** - debug/release configuration
- **Convention plugin integration** - Ready-to-use files for kmp-project-template
- **kmp-project-template sample** - Full sample showing convention plugin usage

## [1.0.0-alpha01] - 2026-02-25

### Added

- Initial release of KMP Product Flavors Gradle Plugin
- **Multi-dimensional flavor support**
  - Define flavor dimensions with priority ordering
  - Automatic cartesian product variant matrix generation
  - Default flavor selection per dimension
- **BuildConfig generation**
  - `VARIANT_NAME` constant with active variant name
  - `IS_<FLAVOR>` boolean flags for all defined flavors
  - Custom `buildConfigField()` support for String, Boolean, Int, Long, Float, Double
  - Configurable package name and class name
  - `@CacheableTask` for efficient incremental builds
- **Source set management**
  - Automatic creation of `common<Flavor>`, `<platform><Flavor>` source sets
  - Proper `dependsOn` wiring for active variant only
  - IDE-friendly: all flavor directories recognized, even inactive ones
- **Intermediate source sets**
  - Optional `webMain` shared between js and wasmJs
  - Optional `nativeMain` shared between iOS, macOS, Linux, Windows
- **Platform detection**
  - Android, iOS, macOS, Linux, Windows (MinGW), Desktop JVM, JS, WasmJS
  - Both `jvm()` and `jvm("desktop")` naming conventions supported
- **Per-flavor dependencies**
  - Add dependencies that only apply to specific flavors
  - `dependency("implementation", "group:artifact:version")`
- **Gradle tasks**
  - `generateFlavorBuildConfig` - Generates BuildConfig Kotlin object
  - `validateFlavors` - Validates configuration (dimensions, names, defaults)
  - `listFlavors` - Lists all variants in a formatted table
- **Configuration options**
  - `-PkmpFlavor=<variant>` Gradle property support
  - `gradle.properties` default flavor setting
  - DSL-based `activeFlavor.set()` configuration
- **Validation**
  - Duplicate flavor name detection
  - Invalid Kotlin identifier detection
  - Missing dimension assignment detection
  - Unknown dimension reference detection
  - Invalid active variant detection

### Technical Details

- Pure JVM Gradle plugin (no KMP in plugin module)
- Gradle lazy configuration with `Property<T>` and `Provider<T>`
- Serializable data classes for task input caching
- `afterEvaluate` pattern for consumer script evaluation

[Unreleased]: https://github.com/MobileByteLabs/kmp-product-flavors/compare/v1.0.1...HEAD
[1.0.1]: https://github.com/MobileByteLabs/kmp-product-flavors/releases/tag/v1.0.1
[1.0.0]: https://github.com/MobileByteLabs/kmp-product-flavors/releases/tag/v1.0.0
[1.0.0-alpha01]: https://github.com/MobileByteLabs/kmp-product-flavors/releases/tag/v1.0.0-alpha01
