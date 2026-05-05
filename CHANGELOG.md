# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

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
