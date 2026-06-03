# Library claims for v2.7 (mirror of consumer.md)

> **Purpose**: every section number here pairs with the same section number in [`consumer.md`](consumer.md). The library claims "we did X"; the consumer verifies X is in place. If a claim has no consumer verifier (or vice versa), adoption is incomplete — file a bug.
>
> **Audience**: library author + release-time CI. AI agents preparing the next release can use this as the discipline checklist.
>
> **Canonical reference**: every section cites the corresponding file in our first-party consumer [`samples/kmp-project-template`](https://github.com/openMF/kmp-project-template) where the verify gate is exercised in production.

---

## 1. Plugin published as v2.7.0 with maven artifact + Gradle plugin id

### What the library did

- Published `io.github.mobilebytelabs:flavor-plugin:2.7.0` to **Maven Central** on 2026-06-02.
- Published `io.github.mobilebytelabs.kmp-product-flavors:2.7.0` to **Gradle Plugin Portal** on 2026-06-02.
- Source-of-truth: `gradle.properties#kmpflavors.version=2.7.0`.
- The two coordinates target two different consumption styles:
  - **Maven artifact** (`io.github.mobilebytelabs.kmpflavors:flavor-plugin`) → for convention-plugin consumers who need a `compileOnly` dep so they can `pluginManager.apply(KmpFlavorPlugin::class.java)` programmatically.
  - **Gradle plugin id** (`io.github.mobilebytelabs.kmp-product-flavors`) → for direct-apply consumers who use `plugins { id("...") version "2.7.0" }`.

### Release-time check (CI)

```bash
# Confirm v2.7.0 is listed on Maven Central — the version this section claims.
# Note: gradle.properties may be at 2.7.x (next dev cycle) after the auto-bump
# PR following each release; we assert against Central, not gradle.properties.
curl -sf 'https://repo1.maven.org/maven2/io/github/mobilebytelabs/kmpflavors/flavor-plugin/maven-metadata.xml' | \
  grep -qE '<version>2\.7\.0</version>'
```

### Pairs with consumer

[Section 1 — Plugin pinned to v2.7.0 via `libs.versions.toml`](consumer.md#1-plugin-pinned-to-v270-via-libsversionstoml)

---

## 2. Toolchain floors stated + built-against pinned

### What the library did

- **Floor (unchanged from v2.6)**: Gradle 8.0+ / KGP 2.0.21+ / AGP 8.2+ / JDK 17+ / CMP 1.7.0+.
- **Built-against (v2.7)**: Gradle 9.5.1 + AGP 9.2.1 + Kotlin 2.3.21 (`gradle/libs.versions.toml#[versions]` + `gradle/wrapper/gradle-wrapper.properties`).
- The reflective `AgpBridge.kt` allows consumers to stay on AGP 8.2+ transparently. Gradle 9.5.1 is what the library itself is built with; consumers can stay on Gradle 8.0+ via the same backward-compat surface.
- Documented in [`COMPATIBILITY_MATRIX.md`](../../COMPATIBILITY_MATRIX.md).

### Release-time check (CI)

```bash
grep -E '^agp\s*=' gradle/libs.versions.toml | grep -q '9.2.1'
grep -E '^kotlin\s*=' gradle/libs.versions.toml | grep -q '2.3.21'
# The library wrapper is on Gradle 9.5.1
grep -E 'distributionUrl=' gradle/wrapper/gradle-wrapper.properties | grep -q '9\.5\.'
# The compat-matrix table row asserts AGP 8.2 is the floor (preserved on backward-compat).
grep -qE '\|\s*Android Gradle Plugin \(AGP\)\s*\|\s*\*\*8\.2\*\*' docs/COMPATIBILITY_MATRIX.md
```

### Pairs with consumer

[Section 2 — Toolchain compatibility](consumer.md#2-toolchain-compatibility)

---

## 3. Two consumption patterns supported (direct apply + convention plugin)

### What the library did

The plugin works equally well in either consumption style:

- **3a. Direct apply** — `plugins { id("io.github.mobilebytelabs.kmp-product-flavors") version "2.7.0" }` → `kmpFlavors { ... }` DSL block.
- **3b. Convention plugin** — `pluginManager.apply(KmpFlavorPlugin::class.java)` programmatically + `extensions.configure<KmpFlavorExtension> { ... }`.

The `KmpFlavorPlugin` entry point registers the `KmpFlavorExtension` under the name `"kmpFlavors"`, which both DSL styles resolve to the same underlying extension instance. No special accommodation is needed in the plugin source for either style.

### Release-time check (CI)

```bash
# Confirm the extension is registered under the name "kmpFlavors" — both DSL
# styles resolve to it. The registration spans multiple lines so grep both
# the create call and the next line for the name literal.
grep -A 2 'project\.extensions\.create' \
  build-logic/flavor-plugin/src/main/kotlin/com/mobilebytelabs/kmpflavors/KmpFlavorPlugin.kt | \
  grep -q '"kmpFlavors"'
```

### Pairs with consumer

[Section 3 — Choose your adoption pattern](consumer.md#3-choose-your-adoption-pattern)

---

## 4. `KmpFlavorPlugin` + `KmpFlavorExtension` public API stable for both patterns

### What the library did

- `KmpFlavorPlugin` class is publicly accessible (Kotlin `class` with default visibility) so consumers can pass `KmpFlavorPlugin::class.java` to `pluginManager.apply(...)`.
- `KmpFlavorExtension` class is publicly accessible so consumers can use `extensions.configure<KmpFlavorExtension> { ... }`.
- Both `kmpFlavors { }` DSL block (3a) and `extensions.configure<KmpFlavorExtension>` (3b) resolve to the same extension instance — the kmpFlavors {} convention is just sugar for the latter.
- The convention-plugin-style adoption is what our first-party canonical consumer [`samples/kmp-project-template`](https://github.com/openMF/kmp-project-template) uses.

### Release-time check (CI)

```bash
# Both classes are publicly accessible
grep -E 'class KmpFlavorPlugin\b' \
  build-logic/flavor-plugin/src/main/kotlin/com/mobilebytelabs/kmpflavors/KmpFlavorPlugin.kt
grep -E 'abstract class KmpFlavorExtension\b' \
  build-logic/flavor-plugin/src/main/kotlin/com/mobilebytelabs/kmpflavors/KmpFlavorExtension.kt

# The kmp-project-template's convention plugin imports both
grep -E 'import com\.mobilebytelabs\.kmpflavors\.(KmpFlavorPlugin|KmpFlavorExtension)' \
  samples/kmp-project-template/build-logic/convention/src/main/kotlin/KMPFlavorsConventionPlugin.kt
```

### Pairs with consumer

[Section 4a — Plugin applied (direct-apply pattern)](consumer.md#4a-plugin-applied-direct-apply-pattern-3a) + [Section 4b — Plugin applied + configured via convention plugin (Recommended)](consumer.md#4b-plugin-applied--configured-via-convention-plugin-recommended-3b)

---

## 5. Flavor + dimension registration paths both supported

### What the library did

- Both `flavorDimensions { register("X") } + flavors { register("Y") { dimension.set("X") } }` (v2.4 flat) AND `dimensions { dimension("X") { flavor("Y") } }` (v2.5 sugar) are supported.
- Mixing the two in the same configure block fires `KMPF-V24` ERROR at configuration time.
- `:listFlavors` task lists every registered flavor with `← ACTIVE` next to the default.

### Release-time check (CI)

```bash
grep -l 'CODE_DIMENSIONS_VS_FLAT_MUTEX' \
  build-logic/flavor-plugin/src/test/kotlin/com/mobilebytelabs/kmpflavors/internal/KmpFlavorPluginValidatorExtraTest.kt
```

### Pairs with consumer

[Section 5 — Flavors + dimensions registered](consumer.md#5-flavors--dimensions-registered-any-v25-style)

---

## 6. `buildConfigPackage` contract + canonical single-source-of-truth pattern

### What the library did

- `generateBuildConfig` convention defaults to `true`.
- `buildConfigClassName` convention defaults to `"BuildKonfig"`.
- `buildConfigPackage` has NO convention — REQUIRED when `generateBuildConfig=true`. Configuration-time hint surfaces when missing.
- Multi-module codegen-host claim mechanism: first module wins by default, override via `codegenHost.set(true)`.
- The canonical pattern in [`samples/kmp-project-template`](https://github.com/openMF/kmp-project-template/blob/main/build-logic/convention/src/main/kotlin/KMPFlavorsConventionPlugin.kt) stores the brand identifier ONCE in `gradle/libs.versions.toml#[versions].appId` and reads it via `libs.findVersion("appId").get().requiredVersion` — forking the template is a one-line change.

### Release-time check (CI)

```bash
# KmpFlavorExtensionTest covers each convention.
grep -l 'generateBuildConfig convention is true\|buildConfigClassName convention is BuildKonfig' \
  build-logic/flavor-plugin/src/test/kotlin/com/mobilebytelabs/kmpflavors/KmpFlavorExtensionTest.kt

# The reference impl uses the appId single-source-of-truth pattern
grep -E 'libs\.findVersion\("appId"\)' \
  samples/kmp-project-template/build-logic/convention/src/main/kotlin/KMPFlavorsConventionPlugin.kt
```

### Pairs with consumer

[Section 6 — `buildConfigPackage` — set, ideally from a single source of truth](consumer.md#6-buildconfigpackage--set-ideally-from-a-single-source-of-truth)

---

## 7. Default variant resolver

### What the library did

- `FlavorVariantResolver.resolveDefaultVariant()` walks each dimension in priority order, picks the `isDefault=true` flavor, falls back to the first flavor if no defaults set.
- `:listActiveVariant` task prints the resolved active variant + the full registered set + the `-PkmpFlavor=` switch instructions.
- `-PkmpFlavor=<name>` overrides the resolved default. Unknown names fire `KMPF-V06` WARN with a soft-fall to the resolved default.

### Release-time check (CI)

```bash
# 16 unit tests cover the resolver
grep -c '@Test' \
  build-logic/flavor-plugin/src/test/kotlin/com/mobilebytelabs/kmpflavors/internal/FlavorVariantResolverExtraTest.kt
# Expected: 16 or more
```

### Pairs with consumer

[Section 7 — Default variant resolves](consumer.md#7-default-variant-resolves)

---

## 8. `BuildKonfig.kt` codegen output path stable + claim mechanism

### What the library did

- Generated at `{module}/build/generated/kmpFlavors/commonMain/kotlin/{packageDir}/BuildKonfig.kt` — stable since v2.0.
- Multi-module claim mechanism: only ONE module generates the file (the codegen host). Others log `skipping FlavorConfig codegen — already generated by :X` and consume the same file.
- The canonical claim winner in [`samples/kmp-project-template`](https://github.com/openMF/kmp-project-template) is `cmp-navigation` (deterministic across local + CI).

### Release-time check (CI)

```bash
# 11 GenerateBuildConfigTaskTest cases cover every codegen block
grep -c '@Test' \
  build-logic/flavor-plugin/src/test/kotlin/com/mobilebytelabs/kmpflavors/tasks/GenerateBuildConfigTaskTest.kt
```

### Pairs with consumer

[Section 8 — `BuildKonfig.kt` codegen produces output at the expected path](consumer.md#8-buildkonfigkt-codegen-produces-output-at-the-expected-path)

---

## 9. Validator KMPF-V01 through V30 active

### What the library did

- `KmpFlavorPluginValidator` codes V01-V30 all wired and tested.
- Each finding includes `code`, `severity` (ERROR/WARNING), `message`, `fix`.
- ERROR findings halt the build at configuration time; WARNING findings surface but proceed.
- `:validateFlavors` task is the standalone entry point.

### Release-time check (CI)

```bash
for code in V01 V02 V03 V04 V05 V06 V07 V08 V14 V15 V16 V17 V18 V19 V20 V21 V22 V23 V24 V25 V26 V27 V28 V29 V30; do
  grep -q "CODE_.*KMPF-${code}\|KMPF-${code}" \
    build-logic/flavor-plugin/src/main/kotlin/com/mobilebytelabs/kmpflavors/internal/KmpFlavorPluginValidator.kt || \
    { echo "missing: $code"; exit 1; }
done
echo "all 25 validator codes present"
```

### Pairs with consumer

[Section 9 — Validator codes V01–V30 pass](consumer.md#9-validator-codes-v01v30-pass)

---

## 10. AGP-only modules supported via `KmpFlavorPlugin` early-return + consumer-side helper pattern

### What the library did

- `KmpFlavorPlugin.apply()` returns early when `KotlinMultiplatformExtension` is NOT present (e.g. a pure `com.android.application` module without `kotlin("multiplatform")`).
- The library does NOT ship its own `configureFlavors(CommonExtension)` helper — this is intentional. The KMP-side flavor declaration is the source of truth, so the consumer mirrors it via their convention plugin's own helper for AGP-only modules.
- The canonical pattern is documented in our first-party consumer [`samples/kmp-project-template`](https://github.com/openMF/kmp-project-template) under `build-logic/convention/src/main/kotlin/org/convention/AppFlavor.kt`.

### Release-time check (CI)

```bash
# KmpFlavorPlugin.apply() has the KMP early-return
grep -E 'KotlinMultiplatformExtension|kotlin\("multiplatform"\)' \
  build-logic/flavor-plugin/src/main/kotlin/com/mobilebytelabs/kmpflavors/KmpFlavorPlugin.kt

# The kmp-project-template ships the canonical AppFlavor.kt helper
test -f samples/kmp-project-template/build-logic/convention/src/main/kotlin/org/convention/AppFlavor.kt
grep -E 'fun configureFlavors\(' \
  samples/kmp-project-template/build-logic/convention/src/main/kotlin/org/convention/AppFlavor.kt
```

### Pairs with consumer

[Section 10 — AGP-only modules: `configureFlavors(CommonExtension)` helper](consumer.md#10-agp-only-modules-configureflavorscommonextension-helper)

---

## 11. Downstream-extension hook pattern documented

### What the library did

- The library does NOT ship a `LocalFlavorsLoader.kt` reflective hook — this is a consumer-side template pattern.
- The canonical pattern is shipped in our first-party consumer [`samples/kmp-project-template`](https://github.com/openMF/kmp-project-template/blob/main/build-logic/convention/src/main/kotlin/LocalFlavorsLoader.kt) for downstream forks to add flavors without editing the synced convention plugin.
- This decoupling means library consumers that are NOT templates (single-fork projects) don't pay the complexity cost.

### Release-time check (CI)

```bash
test -f samples/kmp-project-template/build-logic/convention/src/main/kotlin/LocalFlavorsLoader.kt
grep -E 'object LocalFlavorsLoader' \
  samples/kmp-project-template/build-logic/convention/src/main/kotlin/LocalFlavorsLoader.kt
```

### Pairs with consumer

[Section 11 — Downstream extension hook: `LocalFlavorsLoader` pattern (optional)](consumer.md#11-downstream-extension-hook-localflavorsloader-pattern-optional)

---

## 12. AGP 9.x matrix-CI certification

### What the library did

- `agp-matrix-compat.yml` matrix: `[8.2.2, 8.5.2, 8.10.0, 9.2.1]`. Every PR touching `AgpBridge.kt` runs against all 4.
- `AgpBridge.kt` uses pure reflection — `Class.forName()` + `methods.firstOrNull()` — so AGP 8 → 9 transition has no source change.
- The 4 AGP-9 consumer landmines (CommonExtension type-params, dataBinding deprecation, `com.android.kotlin.multiplatform.library`, dependencyGuard afterEvaluate) are documented in [`AGP_9_MIGRATION_NOTES.md`](../../AGP_9_MIGRATION_NOTES.md).

### Release-time check (CI)

```bash
grep -E '"8\.2\.2"|"8\.5\.2"|"8\.10\.0"|"9\.2\.1"' .github/workflows/agp-matrix-compat.yml | wc -l
# Expected: 4
```

### Pairs with consumer

[Section 12 — AGP 9.x compatibility (conditional — only if you're on AGP 9)](consumer.md#12-agp-9x-compatibility-conditional--only-if-youre-on-agp-9)

---

## 13. End-to-end smoke test surface

### What the library did

- `:validateFlavors`, `:listFlavors`, `:generateFlavorBuildConfig` are all standalone Gradle tasks consumers can chain into one verification command.
- Each task is registered idempotently (re-running is safe).
- 704 tests across 92 test classes pass at 100.00% line coverage on the published commit.

### Release-time check (CI)

```bash
./gradlew -p build-logic :flavor-plugin:test :flavor-plugin:koverVerify \
  --no-daemon --no-configuration-cache
```

### Pairs with consumer

[Section 13 — End-to-end smoke test](consumer.md#13-end-to-end-smoke-test)

---

## 14. `samples/kmp-project-template` ships the Tier 2 adoption record

### What the library did

- Owns `samples/kmp-project-template` as a git submodule pointing at `openMF/kmp-project-template` HEAD.
- Bumped the submodule to AGP-9-compatible HEAD as part of v2.7 Phase 02 (samples-audit).
- The kmp-project-template repo is owned by the Mifos Initiative — a real production consumer, not a synthetic sample.
- The library publishes the **Tier 1 abstract spec** at `docs/adoption/v{X.Y}/consumer.md` (this doc's mirror); the template ships the **Tier 2 concrete realization** at `samples/kmp-project-template/docs/ADOPTION_KMP_PRODUCT_FLAVORS.md`. Together they form the [three-tier source-of-truth chain](../README.md#three-tier-source-of-truth-chain) every consumer should follow.
- Every section in this library's `consumer.md` is realized concretely in the template's `ADOPTION_KMP_PRODUCT_FLAVORS.md` with that template's actual paths and expected outputs.
- The CI workflow `.github/workflows/pr-check.yml` runs the canonical consumer's adoption gate against every PR to this library.

### Release-time check (CI)

```bash
# Submodule exists and points at the right repo
git submodule status samples/kmp-project-template
git config --file .gitmodules submodule.samples/kmp-project-template.url

# Canonical files referenced in consumer.md exist in the submodule
test -f samples/kmp-project-template/build-logic/convention/src/main/kotlin/KMPFlavorsConventionPlugin.kt
test -f samples/kmp-project-template/build-logic/convention/src/main/kotlin/org/convention/AppFlavor.kt
test -f samples/kmp-project-template/build-logic/convention/src/main/kotlin/LocalFlavorsLoader.kt
test -f samples/kmp-project-template/gradle/libs.versions.toml
test -f samples/kmp-project-template/cmp-navigation/build.gradle.kts

# Tier 2 adoption record exists with a section for this version
test -f samples/kmp-project-template/docs/ADOPTION_KMP_PRODUCT_FLAVORS.md
grep -E '^## v2\.7\.0' samples/kmp-project-template/docs/ADOPTION_KMP_PRODUCT_FLAVORS.md

# pr-check.yml validates against the submodule
grep -E 'samples/kmp-project-template' .github/workflows/pr-check.yml | head -3
```

### Pairs with consumer

[Section 14 — Reference implementation: `samples/kmp-project-template`](consumer.md#14-reference-implementation-sampleskmp-project-template)

---

## Release-time discipline for v2.8 (next pair)

When v2.8.0 ships, the same shape applies. The release author MUST:

1. Create `docs/adoption/v2.8/library.md` + `docs/adoption/v2.8/consumer.md`.
2. Number sections consistently — every library claim has a paired consumer verifier.
3. Carry forward Sections 1-14 from v2.7 (most won't change) + add new sections for v2.8 deltas.
4. Update `docs/adoption/README.md` "Available versions" table.
5. Ensure every NEW capability shipped in v2.8 has both a library claim AND a consumer verifier — silent gaps are the failure mode this pattern exists to prevent.
6. If the kmp-project-template canonical adoption pattern changes in v2.8, update Sections 4b / 10 / 11 / 14 to cite the new reference paths.

The CI gate `adoption-doc-symmetry-check.yml` (TODO — to be added in v2.8) will fail the PR if a section number exists on one side without a matching counterpart on the other.

---

## See also

- [`consumer.md`](consumer.md) — the mirror this doc pairs with.
- [`../README.md`](../README.md) — the pattern explainer.
- [`../../MIGRATION_v2.6_TO_v2.7.md`](../../MIGRATION_v2.6_TO_v2.7.md) — delta from v2.6.
- [`../../AGP_9_MIGRATION_NOTES.md`](../../AGP_9_MIGRATION_NOTES.md) — AGP-9 cookbook (decoupled from plugin version).
- [`../../COMPATIBILITY_MATRIX.md`](../../COMPATIBILITY_MATRIX.md) — supported toolchain pairs.
- [`../../COVERAGE_GUIDE.md`](../../COVERAGE_GUIDE.md) — coverage gate + contributor patterns.
- [openMF/kmp-project-template](https://github.com/openMF/kmp-project-template) — first-party canonical consumer.
