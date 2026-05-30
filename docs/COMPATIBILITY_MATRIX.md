# Compatibility Matrix

> **Since v2.5** — the canonical compatibility floor for every supported tool the
> plugin depends on at consumer-side.

---

## UNCHANGED FROM v2.4

**v2.5 introduces no version-floor bumps. Drop-in upgrade for v2.4.x consumers.**

| Tool | Minimum (v2.5) | Built against | Source |
|---|---|---|---|
| Gradle | **8.0** | 9.5 | `gradle/wrapper/gradle-wrapper.properties` |
| Kotlin / KGP | **2.0.21** | 2.3.0 | `gradle/libs.versions.toml` → `kotlin` |
| Android Gradle Plugin (AGP) | **8.0** | 8.12.3 | reflective; tested against 8.0–8.12 |
| Compose Multiplatform | **1.7.0** | 1.10.3 | `ComposeResourcesConfigurator.kt#MIN_CMP_VERSION` |
| JDK toolchain | **17** | 17 | `build-logic/flavor-plugin/build.gradle.kts` → `jvmToolchain(17)` |
| BuildKonfig (codingfeline/BuildKonfig) | (pinned in `libs.versions.toml`) | (pinned in `libs.versions.toml`) | `gradle/libs.versions.toml` |
| kotlinx-coroutines | implicit via CMP | 1.10.2 | `gradle/libs.versions.toml` → `coroutines` |
| kotlinx-serialization | implicit via consumer projects | latest | (consumer-managed) |

---

## Why the floor stayed put

v2.5 ships three substantial capability themes (multi-dim DSL ergonomics + 9-target
sample expansion + BuildKonfig codegen expansion) — but the underlying KMP/Gradle/AGP
infrastructure required for each was already available at the v2.4 floor:

### KMP target stability

All 9 KMP targets added to v2.5's sample matrix have been stable at the v2.4 KGP
floor (2.0.21) or earlier:

- **wasmJs** — stable since KGP 2.0 (v2.4 floor was already 2.0.21, so this was
  already a v2.4 deliverable; v2.5 just adds sample coverage)
- **watchOS** (X64, Arm64, SimulatorArm64, DeviceArm64) — stable since KGP 1.4
- **tvOS** (X64, Arm64, SimulatorArm64) — stable since KGP 1.4
- **linuxX64** — stable since KGP 1.0
- **mingwX64** — stable since KGP 1.0

### AGP API surface

The AGP bridge uses `com.android.build.api.variant.AndroidComponentsExtension` +
`finalizeDsl(Action<CommonExtension>)`, both available since AGP 7.1 (released Q4
2021). v2.4 floor of 8.0 leaves a 1+ major version buffer. v2.5's cross-product
flavor handling (Phase 1) uses the same reflective API surface — no new AGP
features required.

### Compose Multiplatform

Per-variant `composeResources/` auto-discovery on custom source sets (commonFree,
commonPaid, etc.) requires CMP ≥ 1.7.0. This was the v2.4 floor and remains
unchanged in v2.5 — the 9 new targets all flow through the same auto-discovery path.

### BuildKonfig

The v2.5 `buildKonfig {}` DSL expansion (Phase 3) uses string-template codegen, not
kotlinpoet — no new dependency on the build-logic module. The underlying BuildKonfig
library (codingfeline/BuildKonfig) is unchanged.

### Configuration cache

v2.5 task additions (`FrameworkSchemaCheckTask`, expanded `GenerateBuildConfigTask`
inputs) all use serializable types (CustomFieldDeclaration, PerTargetFieldDeclaration,
DimensionEnumSpec, plain String lists). No raw `project` references in `@Input`
getters. Configuration-cache compatibility preserved — verified by
`ConfigCacheCompatibilityTest#v2-5 AC 26`.

---

## Operating-system support

v2.5's `sample-target-coverage.yml` CI exercises the matrix across three runner
families. Consumers can build any combination — these are the canonical CI-verified
combinations:

| Runner | KMP targets exercised in CI |
|---|---|
| `macos-latest` | iOS×3 + watchOS×3 + tvOS×3 + macOS (transitively) |
| `ubuntu-latest` | Desktop JVM + JS + WasmJs + linuxX64 |
| `windows-latest` | mingwX64 |

Apple targets require macOS for compilation (Kotlin/Native toolchain restriction).
mingwX64 cross-compiles from Linux but is tested on Windows for end-to-end
validation.

---

## Cross-references

- **Floor source of truth:** `gradle/libs.versions.toml`
- **Detection:** `build-logic/flavor-plugin/src/main/kotlin/com/mobilebytelabs/kmpflavors/internal/PlatformDetector.kt`
- **AGP bridge:** `build-logic/flavor-plugin/src/main/kotlin/com/mobilebytelabs/kmpflavors/internal/AgpBridge.kt`
- **Version compatibility validator:** `KmpFlavorPluginValidator.validatePlatformAndVersionCompatibility`
  emits KMPF-V14/V15/V16/V17 for known-bad combinations of CMP+KGP, KGP+Gradle,
  Apple Silicon + iosX64. Documented in [`ERROR_CODES.md`](ERROR_CODES.md).
- **Per-target sample coverage:** [`SUPPORTED_TARGETS.md`](SUPPORTED_TARGETS.md)
- **Migration cookbook:** [`MIGRATION_v2.4_TO_v2.5.md`](MIGRATION_v2.4_TO_v2.5.md)
  (opens with "You do not need to migrate.")
