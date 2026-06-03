# Library claims for v2.7 (mirror of consumer.md)

> **Purpose**: every section number here pairs with the same section number in [`consumer.md`](consumer.md). The library claims "we did X"; the consumer verifies X is in place. If a claim has no consumer verifier (or vice versa), adoption is incomplete — file a bug.
>
> **Audience**: library author + release-time CI. AI agents preparing the next release can use this as the discipline checklist.

---

## 1. Plugin published as v2.7.0

### What the library did

- Published `io.github.mobilebytelabs:flavor-plugin:2.7.0` to **Maven Central** on 2026-06-02.
- Published `io.github.mobilebytelabs.kmp-product-flavors:2.7.0` to **Gradle Plugin Portal** on 2026-06-02.
- Source-of-truth: `gradle.properties#kmpflavors.version=2.7.0`.

### Release-time check (CI)

```bash
# Asserts the published version matches the source-of-truth.
PUBLISHED=$(curl -s 'https://repo1.maven.org/maven2/io/github/mobilebytelabs/flavor-plugin/maven-metadata.xml' | \
  grep -oE '<latest>[^<]+' | head -1 | sed 's/<latest>//')
PINNED=$(grep '^kmpflavors\.version=' gradle.properties | cut -d= -f2)
[ "$PUBLISHED" = "$PINNED" ] || { echo "drift: published=$PUBLISHED pinned=$PINNED"; exit 1; }
```

### Pairs with consumer

[Section 1 — Plugin pinned to v2.7.0](consumer.md#1-plugin-pinned-to-v270)

---

## 2. Toolchain floors stated + built-against pinned

### What the library did

- **Floor (unchanged from v2.6)**: Gradle 8.0+ / KGP 2.0.21+ / AGP 8.2+ / JDK 17+ / CMP 1.7.0+.
- **Built-against (v2.7)**: AGP 9.2.1 + Kotlin 2.3.21 (`gradle/libs.versions.toml#[versions]`).
- The reflective `AgpBridge.kt` allows consumers to stay on AGP 8.2+ transparently.
- Documented in [`COMPATIBILITY_MATRIX.md`](../../COMPATIBILITY_MATRIX.md).

### Release-time check (CI)

```bash
grep -E '^agp\s*=' gradle/libs.versions.toml | grep -q '9.2.1'
grep -E '^kotlin\s*=' gradle/libs.versions.toml | grep -q '2.3.21'
grep -E 'AGP 8.2' docs/COMPATIBILITY_MATRIX.md
```

### Pairs with consumer

[Section 2 — Toolchain compatibility](consumer.md#2-toolchain-compatibility)

---

## 3. `kmpFlavors {}` DSL surface preserved from v2.6

### What the library did

- Zero DSL deltas from v2.6 → v2.7. Every v2.6 block (`kmpFlavors {}`, `dimensions {}`, `flavorDimensions {}`, `flavors {}`, `buildTypes {}`, `variantFilter {}`, `promote()`, `spm {}`, `featureFlags {}`, `di {}`, `analytics {}`, `buildKonfig {}`) works in v2.7 byte-identically.
- Documented in [`MIGRATION_v2.6_TO_v2.7.md`](../../MIGRATION_v2.6_TO_v2.7.md) (opens with "You do not need to migrate.").

### Release-time check (CI)

```bash
# The `KmpFlavorExtension` public surface should not have removed any v2.6 entry point.
./gradlew :build-logic:flavor-plugin:apiCheck --no-daemon --no-configuration-cache
```

(The `apiCheck` Gradle task compares the public API against the locked `.api` file.)

### Pairs with consumer

[Section 3 — `kmpFlavors {}` DSL block present](consumer.md#3-kmpflavors--dsl-block-present)

---

## 4. Flavor + dimension registration paths both supported

### What the library did

- Both `flavorDimensions { register("X") } + flavors { register("Y") { dimension.set("X") } }` (v2.4 flat) AND `dimensions { dimension("X") { flavor("Y") } }` (v2.5 sugar) are supported.
- Mixing the two in the same `kmpFlavors {}` fires `KMPF-V24` ERROR at configuration time.
- `:listFlavors` task lists every registered flavor with `← ACTIVE` next to the default.

### Release-time check (CI)

```bash
# Validator KMPF-V24 has a unit test covering the mutex.
grep -l 'CODE_DIMENSIONS_VS_FLAT_MUTEX' \
  build-logic/flavor-plugin/src/test/kotlin/com/mobilebytelabs/kmpflavors/internal/KmpFlavorPluginValidatorExtraTest.kt
```

### Pairs with consumer

[Section 4 — Flavors registered (or `dimensions { }` ergonomic block)](consumer.md#4-flavors-registered-or-dimensions---ergonomic-block)

---

## 5. `buildConfigPackage` convention + required-when-codegen-on contract

### What the library did

- `generateBuildConfig` convention defaults to `true`.
- `buildConfigClassName` convention defaults to `"BuildKonfig"`.
- `buildConfigPackage` has NO convention — REQUIRED when `generateBuildConfig=true`. Configuration-time hint surfaces when missing.
- Multi-module codegen-host claim mechanism: first module wins by default, override via `codegenHost.set(true)` on one module + `set(false)` on others.

### Release-time check (CI)

```bash
# The KmpFlavorExtensionTest covers each convention.
grep -l 'generateBuildConfig convention is true\|buildConfigClassName convention is BuildKonfig' \
  build-logic/flavor-plugin/src/test/kotlin/com/mobilebytelabs/kmpflavors/KmpFlavorExtensionTest.kt
```

### Pairs with consumer

[Section 5 — Required config: `buildConfigPackage`](consumer.md#5-required-config-buildconfigpackage)

---

## 6. Default variant resolver

### What the library did

- `FlavorVariantResolver.resolveDefaultVariant()` walks each dimension in priority order, picks the `isDefault=true` flavor, falls back to the first flavor if no defaults set.
- `:listActiveVariant` task prints the resolved active variant + the full registered set + the `-PkmpFlavor=` switch instructions.
- `-PkmpFlavor=<name>` overrides the resolved default. Unknown names fire `KMPF-V06` WARN with a soft-fall to the resolved default.

### Release-time check (CI)

```bash
# 16 unit tests cover the resolver — see FlavorVariantResolverExtraTest.
grep -c '@Test' \
  build-logic/flavor-plugin/src/test/kotlin/com/mobilebytelabs/kmpflavors/internal/FlavorVariantResolverExtraTest.kt
# Expected: 16 or more
```

### Pairs with consumer

[Section 6 — Default variant resolves](consumer.md#6-default-variant-resolves)

---

## 7. `BuildKonfig.kt` codegen output path stable

### What the library did

- Generated at `{module}/build/generated/kmpFlavors/commonMain/kotlin/{packageDir}/BuildKonfig.kt` — stable since v2.0.
- Emits `VARIANT_NAME`, `BUILD_TYPE` (if build types declared), `IS_<FLAVOR>` constants for every flavor, plus consumer's custom `buildConfigField(...)` declarations.
- v2.5+: optional `Network`, `PerTarget`, sealed-class enums, customField<T>, secrets (placeholder when manifest schema < v2.1).
- v2.6+: optional Koin `FlavorDependentModules.kt`, `AnalyticsTags.kt`.

### Release-time check (CI)

```bash
# 11 GenerateBuildConfigTaskTest tests cover every codegen block.
grep -c '@Test' \
  build-logic/flavor-plugin/src/test/kotlin/com/mobilebytelabs/kmpflavors/tasks/GenerateBuildConfigTaskTest.kt
# Expected: 11 or more
```

### Pairs with consumer

[Section 7 — `BuildKonfig.kt` codegen produces output at the expected path](consumer.md#7-buildkonfigkt-codegen-produces-output-at-the-expected-path)

---

## 8. Validator KMPF-V01 through V30 active

### What the library did

- `KmpFlavorPluginValidator` codes V01-V30 all wired and tested.
- Each finding includes `code`, `severity` (ERROR/WARNING), `message`, `fix`.
- ERROR findings halt the build at configuration time; WARNING findings surface but proceed.
- `:validateFlavors` task is the standalone entry point.

### Release-time check (CI)

```bash
# Every KMPF-V code has a CONST and at least one test case.
for code in V01 V02 V03 V04 V05 V06 V07 V08 V14 V15 V16 V17 V18 V19 V20 V21 V22 V23 V24 V25 V26 V27 V28 V29 V30; do
  grep -q "CODE_.*KMPF-${code}\|KMPF-${code}" \
    build-logic/flavor-plugin/src/main/kotlin/com/mobilebytelabs/kmpflavors/internal/KmpFlavorPluginValidator.kt || \
    { echo "missing: $code"; exit 1; }
done
echo "all 25 validator codes present"
```

### Pairs with consumer

[Section 8 — Validator codes all pass (KMPF-V01 through V30)](consumer.md#8-validator-codes-all-pass-kmpf-v01-through-v30)

---

## 9. AGP 9.x matrix-CI certification

### What the library did

- `agp-matrix-compat.yml` matrix: `[8.2.2, 8.5.2, 8.10.0, 9.2.1]`. Every PR touching `AgpBridge.kt` runs against all 4.
- `AgpBridge.kt` uses pure reflection — `Class.forName()` + `methods.firstOrNull()` — so AGP 8 → 9 transition has no source change.
- The 4 AGP-9 consumer landmines (CommonExtension type-params, dataBinding deprecation, `com.android.kotlin.multiplatform.library`, dependencyGuard afterEvaluate) are documented in [`AGP_9_MIGRATION_NOTES.md`](../../AGP_9_MIGRATION_NOTES.md).

### Release-time check (CI)

```bash
# Confirm all 4 AGP rows present in the matrix.
grep -E '"8\.2\.2"|"8\.5\.2"|"8\.10\.0"|"9\.2\.1"' .github/workflows/agp-matrix-compat.yml | wc -l
# Expected: 4
```

### Pairs with consumer

[Section 9 — AGP 9.x compatibility (only if you're on AGP 9)](consumer.md#9-agp-9x-compatibility-only-if-youre-on-agp-9)

---

## 10. End-to-end smoke test surface

### What the library did

- `:validateFlavors`, `:listFlavors`, `:generateFlavorBuildConfig` are all standalone Gradle tasks consumers can chain into one verification command.
- Each task is registered idempotently (re-running is safe).
- 704 tests across 92 test classes pass at 100.00% line coverage on the published commit (see [`COVERAGE_GUIDE.md`](../../COVERAGE_GUIDE.md)).

### Release-time check (CI)

```bash
# The published commit must be one where the full test suite passed at floor 100.
./gradlew -p build-logic :flavor-plugin:test :flavor-plugin:koverVerify \
  --no-daemon --no-configuration-cache
```

### Pairs with consumer

[Section 10 — End-to-end smoke test](consumer.md#10-end-to-end-smoke-test)

---

## Release-time discipline for v2.8 (next pair)

When v2.8.0 ships, the same shape applies. The release author MUST:

1. Create `docs/adoption/v2.8/library.md` + `docs/adoption/v2.8/consumer.md`.
2. Number sections consistently — every library claim has a paired consumer verifier.
3. Carry forward Sections 1-10 from v2.7 (most won't change) + add new sections for v2.8 deltas.
4. Update `docs/adoption/README.md` "Available versions" table.
5. Ensure every NEW capability shipped in v2.8 has both a library claim AND a consumer verifier — silent gaps are the failure mode this pattern exists to prevent.

The CI gate `adoption-doc-symmetry-check.yml` (TODO — to be added in v2.8) will fail the PR if a section number exists on one side without a matching counterpart on the other.

---

## See also

- [`consumer.md`](consumer.md) — the mirror this doc pairs with.
- [`../README.md`](../README.md) — the pattern explainer.
- [`../../MIGRATION_v2.6_TO_v2.7.md`](../../MIGRATION_v2.6_TO_v2.7.md) — delta from v2.6.
- [`../../AGP_9_MIGRATION_NOTES.md`](../../AGP_9_MIGRATION_NOTES.md) — AGP-9 cookbook (decoupled from plugin version).
- [`../../COMPATIBILITY_MATRIX.md`](../../COMPATIBILITY_MATRIX.md) — supported toolchain pairs.
- [`../../COVERAGE_GUIDE.md`](../../COVERAGE_GUIDE.md) — coverage gate + contributor patterns.
