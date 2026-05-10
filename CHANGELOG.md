# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

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
