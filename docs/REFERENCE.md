# Reference — kmp-product-flavors DSL

> Complete `kmpFlavors { }` extension reference. Every property, every DSL method, with stability bucket + default + smoke example.

For the 5-min onboarding, see [`QUICKSTART.md`](QUICKSTART.md).

## Stability buckets

Each surface in this reference is tagged with one of three buckets:

- 🟢 **Stable** — locked for the remainder of the 2.x cycle. Removed only on a major-version bump.
- 🟡 **Experimental** — may change behaviour or be removed in v2.x point releases. Code-level marker: `@KmpFlavorsExperimental("…")` annotation lands in v2.4 (current cycle).
- 🟠 **Workaround** — bridges a gap until an upstream API lands. Tagged in source with `CMP-API-WAITING` markers. Replaced when the upstream API is published.

---

## Core flavor / build-type DSL

### 🟢 `flavors { register(name) { … } }`

Declare product flavors.

```kotlin
kmpFlavors {
    flavors {
        register("free") {
            isDefault.set(true)
            dimension.set("tier")             // optional — single-dim builds can omit
            applicationIdSuffix.set(".free")  // forwarded to AGP bridge on Android targets
            buildConfigField("Int", "MAX_ITEMS", "10")
        }
        register("paid")
    }
}
```

| Property | Type | Default | Notes |
|---|---|---|---|
| `isDefault` | `Property<Boolean>` | `false` | Marks which flavor compiles via the standard `compileKotlin*` tasks. Exactly one flavor per dimension should set this. |
| `dimension` | `Property<String>` | `null` | Required when multiple dimensions are registered via `flavorDimensions { … }`. |
| `applicationIdSuffix` | `Property<String>` | `""` | Forwarded to AGP `productFlavors { name { applicationIdSuffix = … } }` when `bridgeAgpProductFlavors=true`. |
| `bundleIdSuffix` | `Property<String>` | `""` | iOS bundle identifier suffix. |
| `buildConfigField(type, name, value)` | DSL function | — | Adds a `const val NAME: TYPE = VALUE` to the generated `BuildKonfig`. Supported types: `Boolean`, `Int`, `Long`, `Float`, `Double`, `String`. |
| `dependencies { … }` | DSL block | — | Per-flavor dependency registration. Adds deps to that flavor's compilation classpath. |

---

### 🟢 `buildTypes { register(name) { … } }`

Declare build types (the build-type axis of the flavor × buildType matrix).

```kotlin
kmpFlavors {
    enableBuildTypes.set(true)    // required when registering buildTypes
    buildTypes {
        register("debug") { isDefault.set(true) }
        register("staging")
        register("release")
    }
}
```

| Property | Type | Default | Notes |
|---|---|---|---|
| `isDefault` | `Property<Boolean>` | `false` | One build type per build should set this to mark the active build type. |

---

### 🟢 `flavorDimensions { register(name) { … } }`

For multi-axis variant matrices.

```kotlin
kmpFlavors {
    flavorDimensions {
        register("tier") { priority.set(0) }
        register("environment") { priority.set(1) }
    }
    flavors {
        register("free") { dimension.set("tier") }
        register("paid") { dimension.set("tier") }
        register("dev")  { dimension.set("environment") }
        register("prod") { dimension.set("environment") }
    }
}
```

Resulting variants: `freeDev`, `freeProd`, `paidDev`, `paidProd`. Higher `priority` dimension wins on flavor-name conflicts.

---

### 🟢 `variantFilter { … }`

AGP-shaped filter to remove specific variant combinations.

```kotlin
kmpFlavors {
    variantFilter {
        // Strip `paidRelease` from the matrix entirely
        if (flavors.any { it.name == "paid" } && buildType == "release") {
            setIgnore(true)
        }
    }
}
```

Filtered variants disappear from `listFlavors` output + compilation registration.

---

## Top-level extension properties

### 🟢 `buildMatrix`

```kotlin
abstract val buildMatrix: Property<Boolean>
```

Default: `false`. Setting `true` enables matrix mode — registers `compile{Variant}Kotlin{Target}` tasks for every inactive variant on every non-Android target, in addition to the active variant's standard `compileKotlin*` task.

See [`MATRIX_MODE.md`](MATRIX_MODE.md) for the full matrix-mode surface.

---

### 🟢 `publishMatrix`

```kotlin
abstract val publishMatrix: Property<Boolean>
```

Default: `false`. Requires `buildMatrix=true`. Registers classifier-tagged `MavenPublication`s per (inactive variant × target) so consumers can resolve `library:1.0.0:paidStaging`.

See [`PUBLISHING.md`](PUBLISHING.md).

---

### 🟢 `autoEnable`

```kotlin
abstract val autoEnable: Property<Boolean>
```

Default: `true`. When `true`, the plugin auto-detects opportunities to flip `buildMatrix`, `publishMatrix`, `detektPerVariant`, `enableBuildTypes`, etc. when their adjacent plugins or configurations are detected.

Set to `false` to preserve strict v1.x active-variant-only semantics. The v1.x → v2.x migration documented in [`MATRIX_MODE.md`](MATRIX_MODE.md) typically requires this opt-out.

---

### 🟢 `enableBuildTypes`

```kotlin
abstract val enableBuildTypes: Property<Boolean>
```

Default: `false` (auto-flips to `true` when `buildTypes { register(…) }` is called on `autoEnable=true` builds). When `true`, the variant matrix is `flavor × buildType`; when `false`, just `flavor`.

---

### 🟢 `generateBuildConfig`

```kotlin
abstract val generateBuildConfig: Property<Boolean>
```

Default: `false`. When `true`, generates a `BuildKonfig` Kotlin object per variant carrying `buildConfigField` values + auto-derived `IS_<FLAVOR>` / `IS_<BUILDTYPE>` / `VARIANT_NAME` / `BUILD_TYPE` constants.

---

### 🟢 `buildConfigPackage` / `buildConfigClassName`

```kotlin
abstract val buildConfigPackage: Property<String>
abstract val buildConfigClassName: Property<String>
```

Defaults: `"com.example.app"` / `"BuildKonfig"`. Both used only when `generateBuildConfig=true`.

---

### 🟢 `bridgeAgpProductFlavors` / `bridgeAgpBuildTypes`

```kotlin
abstract val bridgeAgpProductFlavors: Property<Boolean>
abstract val bridgeAgpBuildTypes: Property<Boolean>
```

Defaults: `true` / `true`. Forward `kmpFlavors.flavors` + `kmpFlavors.buildTypes` into AGP's `android { productFlavors { … }; buildTypes { … } }` when an Android target is detected.

---

### 🟢 `publishMatrixSbom`

```kotlin
abstract val publishMatrixSbom: Property<Boolean>
```

Default: `false`. When `true` + `publishMatrix=true` + `org.cyclonedx.bom` plugin applied, attaches a CycloneDX SBOM to each per-variant `MavenPublication`.

---

### 🟢 `detektPerVariant`

```kotlin
abstract val detektPerVariant: Property<Boolean>
```

Default: `false` (auto-flips to `true` when `io.gitlab.arturbosch.detekt` is detected on `autoEnable=true` builds). Registers `detekt{Variant}` tasks per variant with per-variant baselines at `config/detekt/{variant}/baseline.xml`.

---

### 🟢 `excludeGeneratedFromFormatters`

```kotlin
abstract val excludeGeneratedFromFormatters: Property<Boolean>
```

Default: `false` (auto-flips when Spotless / Detekt detected). Excludes `build/generated/kmpFlavors/**` from Spotless + Detekt scans.

---

### 🟢 `dependencyGuardPerVariant`

```kotlin
abstract val dependencyGuardPerVariant: Property<Boolean>
```

Default: `false` (auto-flips when `com.dropbox.dependency-guard` detected). Auto-registers one `dependencyGuard.configuration(...)` baseline per (variant × target).

---

### 🟢 `publishMatrixLegacyIosClassifiers`

```kotlin
abstract val publishMatrixLegacyIosClassifiers: Property<Boolean>
```

Default: `true`. When `true`, ships v2.1's Zip-shaped iOS MavenPublications alongside v2.2's XCFramework publications for the migration window. Flip to `false` once consumers move to `:{variant}-xcframework` coordinates.

---

### 🟢 `codegenHost`

```kotlin
abstract val codegenHost: Property<Boolean>
```

Default: unset (first-come-first-served auto-claim).

Explicit override for multi-module codegen. When more than one module applies the plugin under the same `buildConfigPackage` + `buildConfigClassName`, the plugin elects exactly one module as the codegen host — otherwise downstream builds hit duplicate-class errors at DEX merge.

- `set(true)`: this module always wins the host claim regardless of configuration order.
- `set(false)`: this module never generates the class (consumes the host's output transitively).
- Unset: first-come-first-served. Non-deterministic across builds — prefer explicit `set(true)` in a designated host module (e.g. `cmp-shared`).

---

### 🟢 `createIntermediateSourceSets`

```kotlin
abstract val createIntermediateSourceSets: Property<Boolean>
```

Default: `true`.

Creates platform-family intermediate source sets that share code between related targets:

- `webMain` — shared between `js` and `wasmJs`.
- `nativeMain` — shared between iOS, macOS, Linux, Windows (every Kotlin/Native target).

Flip to `false` to opt into bare per-target source sets (rare — most consumers benefit from these intermediate sets).

Distinct from `createIntermediateBuildTypeSourceSets` (which creates `common{BuildType}` source sets across flavors of the same build type).

---

### 🟠 `activeFlavor` (v1.x compat shim, removed 2026-11-14)

```kotlin
abstract val activeFlavor: Property<String>
```

**Status**: Workaround / CMP-API-WAITING. **Deprecation cutoff**: 2026-11-14 per RFC §3 Q15. After that date the v1.x compat shim is removed and assigning `activeFlavor.set(...)` triggers `KMPF-V21` ERROR + `GradleException`.

The v1.x DSL — set the active variant by name. Replaced in v2.x by `register("name") { isDefault.set(true) }` inside `flavors { … }`. See [`MIGRATION_v1_to_v2.md`](MIGRATION_v1_to_v2.md) for the migration path.

For active-variant override at the CLI, use `-PkmpFlavor=<variant>` instead — that path is Stable and survives the cutoff.

---

## Experimental surfaces

### 🟡 `detektPerVariantPerTarget`

```kotlin
abstract val detektPerVariantPerTarget: Property<Boolean>
```

**Since**: v2.3. **Why experimental**: needs adoption signal on real 2+ target projects before promotion to Stable.

Default: `false`. Requires `detektPerVariant=true`. Extends per-variant Detekt to per-(variant × non-Android target) — registers `detekt{Variant}{Target}` (e.g. `detektFreeDevDesktop`, `detektFreeDevIosArm64`). Per-target baseline path: `config/detekt/{variant}/{target}/baseline.xml`.

---

### 🟡 `variantCacheNamespacing`

```kotlin
abstract val variantCacheNamespacing: Property<Boolean>
```

**Since**: v2.4. **Why experimental**: real cache-hit telemetry on 8+ variant modules pending; default-flip to `true` happens once that data justifies it.

Default: `false`. Requires `buildMatrix=true`. Injects `kmpFlavorVariant` as `@Input` on every `compileKotlin*` task in matrix mode, partitioning the Gradle cache key space per variant so cache evictions don't cascade across sibling variants.

Surfaces `KMPF-V20` (INFO) when `variantCacheNamespacing=true` but `buildMatrix=false` — matrix mode is a prerequisite. See [`ERROR_CODES.md`](ERROR_CODES.md).

---

### 🟡 `composeHotReloadPerVariant`

```kotlin
abstract val composeHotReloadPerVariant: Property<Boolean>
```

**Since**: v2.3. **Why experimental**: Option A only; Option B graduation gated on CMP exposing a public hot-reload reset API (tracked at [issue #75](https://github.com/MobileByteLabs/kmp-product-flavors/issues/75)).

Default: `false`. Requires `org.jetbrains.compose` applied. Registers `composeHotReload{Variant}{Target}` per (inactive variant × JVM-family target). Switching the active variant still requires a Gradle daemon restart on CMP 1.7-1.9 — see [`COMPOSE_HOT_RELOAD.md`](COMPOSE_HOT_RELOAD.md) for the workflow.

---

### 🟡 `npmPublishMatrix` / `npmPackagePrefix`

```kotlin
abstract val npmPublishMatrix: Property<Boolean>
abstract val npmPackagePrefix: Property<String>
```

**Since**: v2.2. **Why experimental**: minimal real-world npm publish testing.

Default: `false` / `rootProject.name`. When `true` + `publishMatrix=true` + `js(IR)` or `wasmJs()` target declared, generates per-variant `package.json` + `.tgz` tarball at `build/kmpFlavors/npm/{prefix}-{variant}/`. Consumer's `~/.npmrc` handles registry credentials; the plugin doesn't manage them.

---

### 🟡 `featureFlags { … }`

```kotlin
val featureFlags: FeatureFlagsConfig
```

**Since**: v2.2. **Why experimental**: SDK integration patterns may evolve as adopters wire it against real GrowthBook / Statsig / LaunchDarkly deployments.

Generates per-variant `FeatureFlags.kt` Kotlin object alongside `BuildKonfig`. Each platform (`growthbook`, `statsig`, `launchDarkly`) accepts a `defaultPayload` JSON file; the generator embeds the values as `Map<String, String>` consumed by the platform SDK at runtime.

```kotlin
kmpFlavors {
    featureFlags {
        growthbook { defaultPayload.set(file("flags/growthbook.json")) }
    }
}
```

Per-variant overrides: sibling files named `flags/<platform>.<variant>.json` (e.g. `flags/growthbook.paid.json`) override the base map for that variant only.

---

### 🟡 `promote(from, to, action)`

```kotlin
fun promote(from: String, to: String, action: Action<VariantPromotionScope>)
```

**Since**: v2.2. **Why experimental**: consumer demand signal unclear.

Automates source-set graduation between buildTypes (e.g. `freeDev` → `freeStaging`). Registers `promote{From}To{To}` task that applies declared transforms (`renamePackage`, etc.) when invoked. `-Pdry-run=true` previews the diff without writing.

---

### 🟡 `variants.matching { … }.configureEach { dependencies { exclude(group, module) } }`

```kotlin
abstract class VariantDependenciesScope {
    fun exclude(group: String, module: String)
}
```

**Since**: v2.4. **Why experimental**: survey-gate-cleared via fix-all session, not consumer demand. Long-term API shape may evolve.

Per-variant dependency exclusion. Each variant's `KotlinCompilation` compile + runtime classpath gets the exclude rule applied. Pass empty string to wildcard a side (Gradle's standard exclude semantics). Both empty triggers `KMPF-V22` warning.

See [`VARIANT_DEPENDENCY_EXCLUDES.md`](VARIANT_DEPENDENCY_EXCLUDES.md).

---

### 🟡 `createIntermediateBuildTypeSourceSets`

```kotlin
abstract val createIntermediateBuildTypeSourceSets: Property<Boolean>
```

**Since**: v2.2 Phase 1A. **Why experimental**: KGP cross-tree dependency warnings cosmetic but persistent; proper fix via KGP's `applyHierarchyTemplate` API pending.

Default: `false`. When `true` + `enableBuildTypes=true` + matrix mode, creates `common{BuildType}` (e.g. `commonStaging`) source sets shared between sibling-buildType variants. Closes RFC §10.

---

## Workaround surfaces (CMP-API-WAITING)

### 🟠 `switchVariantAndReload` task

```bash
./gradlew switchVariantAndReload --to=<variantName>
```

**Since**: v2.4. **Why workaround**: genuine Option B (daemon-restart-free variant switching) requires CMP exposing a public hot-reload reset API; not yet shipped by JetBrains.

Persists the new variant to `build/kmpFlavor.lock` + prints the follow-up `composeApp:run` command. Collapses the 3-step manual sequence (stop daemon → edit `gradle.properties` → restart) into one command. Task name + `--to=` option stay stable across both implementations, so consumer scripts written against the workaround don't need to change when v2.5+ ships the real Option B.

Cross-linked from [issue #75](https://github.com/MobileByteLabs/kmp-product-flavors/issues/75) + tagged with `CMP-API-WAITING` source markers.

---

## Variants public API

### 🟢 `kmpFlavors.variants`

```kotlin
val variants: NamedDomainObjectContainer<KmpFlavorVariant>
```

Lazy-populated container of resolved variants. Use standard Gradle `NamedDomainObjectCollection` mechanics:

```kotlin
kmpFlavors.variants.matching { it.flavors.contains("paid") }.configureEach {
    // per-variant configuration block, fires for paidDebug, paidStaging, paidRelease, etc.
    val variantName = name
    val variantFlavors = flavors          // List<String>
    val variantBuildType = buildType      // String?
    val variantTargets = targets          // Set<KotlinTarget> (lazy)
    val variantCompilations = compilations // Map<KotlinTarget, KotlinCompilation<*>> (lazy)
    val variantIntermediateSourceSets = intermediateSourceSets  // List<KotlinSourceSet>
}
```

The `targets` + `compilations` fields are populated AFTER `CompilationRegistrar.register()` runs — use `configureEach { }` (not eager `forEach`) to consume them safely.

---

## Tasks

| Task | Purpose | Stability |
|---|---|---|
| `compile{Variant}Kotlin{Target}` | Variant compilation (per inactive variant × target) in matrix mode. | 🟢 |
| `compileKotlin{Target}` | Active variant compilation. Unchanged from KGP defaults. | 🟢 |
| `assembleAll{Target}Variants` | Aggregates active + inactive variants on one target. | 🟢 |
| `assembleAllVariants` | Aggregates every variant on every target. | 🟢 |
| `listFlavors` | Print the resolved matrix (with `(filtered out)` annotations). | 🟢 |
| `listActiveVariant` | Print the active variant + `-PkmpFlavor` switch instructions. | 🟢 |
| `validateFlavors` | Run the structured validator + emit any `KMPF-V…` findings. | 🟢 |
| `generateRunConfigurations` | Emit IntelliJ Run Configurations per (variant × target). | 🟢 |
| `publish{Variant}PublicationToMavenLocal` | Per-variant publish (matrix mode + publishMatrix). | 🟢 |
| `detekt{Variant}` | Per-variant Detekt scope (when `detektPerVariant=true`). | 🟢 |
| `detekt{Variant}{Target}` | Per-(variant × target) Detekt (when `detektPerVariantPerTarget=true`). | 🟡 |
| `composeHotReload{Variant}{Target}` | Per-variant CMP hot-reload (when `composeHotReloadPerVariant=true`). | 🟡 |
| `switchVariantAndReload --to=<v>` | Variant-switch helper task (CMP-API-WAITING workaround). | 🟠 |
| `promote{From}To{To}` | Variant promotion task (per `kmpFlavors.promote(...)` registration). | 🟡 |

---

## Validator codes

Quick reference; full catalogue + fix steps in [`ERROR_CODES.md`](ERROR_CODES.md).

| Code | Severity | Meaning |
|---|---|---|
| `KMPF-V01` | ERROR | Flavor name collides with build type name |
| `KMPF-V02` | ERROR | Flavor declared without `dimension.set(…)` when dimensions registered |
| `KMPF-V03` | ERROR | Dimension has no flavors assigned |
| `KMPF-V04` | ERROR | Variant filter excluded every variant |
| `KMPF-V05` | WARNING | Matrix mode enabled but zero non-Android KMP targets |
| `KMPF-V06` | WARNING | `-PkmpFlavor` references an unknown variant |
| `KMPF-V07` | ERROR | Invalid `buildConfigField` type |
| `KMPF-V08` | ERROR | Matrix mode enabled but no flavors registered |
| `KMPF-V13` | WARNING | Gradle 9 Project Isolation cross-project state in codegen-host election |
| `KMPF-V14` | WARNING | Compose Multiplatform version below floor (`<1.7.0`) |
| `KMPF-V15` | WARNING | Apple Silicon + iosX64 may need Rosetta |
| `KMPF-V16` | WARNING | CMP × KGP version skew |
| `KMPF-V17` | WARNING | KGP × Gradle version skew |
| `KMPF-V18` | INFO (→ WARNING in v2.4.x) | Variant exclude target dep missing |
| `KMPF-V19` | ERROR (publish-time) | Sonatype Snapshots namespace not enabled |
| `KMPF-V20` | INFO | `variantCacheNamespacing=true` but `buildMatrix=false` |
| `KMPF-V21` | ERROR | Legacy `activeFlavor` DSL post-2026-11-14 (reserved for v2.5+) |
| `KMPF-V22` | WARNING | Variant `exclude(group="", module="")` (both empty) |
| `KMPF-V23` | ERROR | Custom `buildConfigField` name collides with auto-derived constant (`IS_<FLAVOR>` / `IS_<BUILDTYPE>` / `VARIANT_NAME` / `BUILD_TYPE`) |

---

## See also

- [`QUICKSTART.md`](QUICKSTART.md) — 5-min onboarding.
- [`MATRIX_MODE.md`](MATRIX_MODE.md) — matrix-mode deep dive.
- [`PUBLISHING.md`](PUBLISHING.md) — per-variant publishing (Maven Central, npm, Sonatype Snapshots).
- [`VARIANT_DEPENDENCY_EXCLUDES.md`](VARIANT_DEPENDENCY_EXCLUDES.md) — variant-scoped dependency excludes.
- [`COMPOSE_HOT_RELOAD.md`](COMPOSE_HOT_RELOAD.md) — Compose Multiplatform hot-reload.
- [`ERROR_CODES.md`](ERROR_CODES.md) — full KMPF-Vxx catalogue.
- [`RELEASE.md`](RELEASE.md) — release flow + cascade reference.
- [`MIGRATION_v1_to_v2.md`](MIGRATION_v1_to_v2.md) — v1.x → v2.x migration.
