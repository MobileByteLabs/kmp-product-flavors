# Adopting kmp-product-flavors v2.7 (consumer side)

> **Format**: AI-executable verify gates. Paste this doc into Claude/Cursor/Copilot and the agent will run every `## ✅ Verify` block in order. If all pass, you are at **100% adoption** of v2.7 — no missing pieces.
>
> **Mirror**: every section here has a paired claim in [`library.md`](library.md). If a library claim has no verifier here, that's a bug — file it.
>
> **Greenfield consumers**: read top-to-bottom; this doc walks you from zero to fully-integrated.
> **Existing v2.6 consumers**: read [`MIGRATION_v2.6_TO_v2.7.md`](../../MIGRATION_v2.6_TO_v2.7.md) first (it's 92 lines and opens with "You do not need to migrate"), then run the verify gates here to confirm 100% adoption.

## How to use this doc

```bash
# 1. From the root of your KMP consumer project
cd /path/to/your/consumer

# 2. Walk through Sections 1-10 in order
# 3. For each, run the ✅ Verify block
# 4. If "Expected output" matches → continue
# 5. If verify fails → apply "If verify fails" remediation → re-run

# At the end of Section 10, you have 100% adoption.
```

---

## 1. Plugin pinned to v2.7.0

### What you should have

```kotlin
// settings.gradle.kts
pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

// build.gradle.kts (root or per-module — same shape)
plugins {
    id("io.github.mobilebytelabs.kmp-product-flavors") version "2.7.0"
}
```

### ✅ Verify

```bash
grep -rn 'io\.github\.mobilebytelabs\.kmp-product-flavors' \
  --include='*.gradle.kts' --include='*.gradle' --include='*.toml' .
```

**Expected output**: at least one line containing `version "2.7.0"` or `version = "2.7.0"`. Multiple lines OK; all must match the same version string.

**If verify fails**:
- No matches → plugin not applied. Add the `plugins { id(...) version "2.7.0" }` block above.
- Multiple versions → pick one; pin the entire build to one plugin version (use a `gradle/libs.versions.toml` `[plugins]` table).
- Version < 2.7.0 → upgrade. Floor is unchanged at AGP 8.2+, so the bump is safe if your toolchain meets Section 2.

---

## 2. Toolchain compatibility

### What you should have

| Tool | Minimum (floor) | Recommended (built-against) |
|---|---|---|
| JDK | 17 | 17 or 21 |
| Gradle | 8.0 | 8.5+ |
| Kotlin Gradle Plugin (KGP) | 2.0.21 | 2.3.x |
| AGP (if Android consumer) | 8.2 | 8.10+ or 9.2.1 |
| Compose Multiplatform (if CMP consumer) | 1.7.0 | 1.7+ |

### ✅ Verify

```bash
# JDK
java -version 2>&1 | head -1

# Gradle
./gradlew --version 2>&1 | grep '^Gradle'

# KGP + AGP versions (read from libs.versions.toml or build.gradle.kts)
grep -E '^(kotlin|agp|kotlinGradlePlugin|androidGradlePlugin)\s*=' gradle/libs.versions.toml 2>/dev/null || \
  grep -E 'kotlin\("\d+\.|com\.android\.tools\.build:gradle:' \
    -r --include='*.gradle.kts' --include='*.gradle' .
```

**Expected output**:
- `java -version` reports `17.x.x` or higher
- Gradle 8.0+
- Kotlin 2.0.21+
- AGP 8.2+ (if Android consumer)

**If verify fails**:
- JDK < 17 → install via SDKMAN: `sdk install java 17.0.13-zulu` and set in `gradle.properties`: `org.gradle.java.home=$(/usr/libexec/java_home -v 17)` (macOS).
- Gradle < 8.0 → run `./gradlew wrapper --gradle-version 8.10` and commit `gradle/wrapper/gradle-wrapper.properties`.
- KGP < 2.0.21 → bump `kotlin = "..."` in `libs.versions.toml`.
- AGP < 8.2 → see [`AGP_9_MIGRATION_NOTES.md`](../../AGP_9_MIGRATION_NOTES.md) if you also want AGP 9. The plugin works on AGP 8.2+ transparently.

---

## 3. `kmpFlavors {}` DSL block present

### What you should have

```kotlin
// build.gradle.kts of the module(s) where you want flavors
kmpFlavors {
    buildConfigPackage.set("com.example.app")
    // ... at least one flavor declared, see Section 4
}
```

### ✅ Verify

```bash
grep -rn '^kmpFlavors\s*{' \
  --include='*.gradle.kts' --include='*.gradle' .
```

**Expected output**: at least one match. The block can live in any module — root, app, shared, feature.

**If verify fails**:
- No matches → you applied the plugin but never configured it. Add the block above to the module that should drive the flavor matrix (typically a shared module like `cmp-shared`).

---

## 4. Flavors registered (or `dimensions { }` ergonomic block)

### What you should have

One of these two styles inside `kmpFlavors { ... }`:

**Legacy flat DSL** (v2.4+):
```kotlin
kmpFlavors {
    flavorDimensions { register("tier") }
    flavors {
        register("free") { dimension.set("tier"); isDefault.set(true) }
        register("paid") { dimension.set("tier") }
    }
}
```

**v2.5+ ergonomic sugar**:
```kotlin
kmpFlavors {
    dimensions {
        dimension("tier") {
            flavor("free") { isDefault.set(true) }
            flavor("paid")
        }
    }
}
```

> **Note**: mixing both styles in the same `kmpFlavors {}` block fires KMPF-V24 ERROR at configuration time. Pick one.

### ✅ Verify

```bash
./gradlew :listFlavors --no-daemon --no-configuration-cache 2>&1 | \
  tail -30
```

**Expected output**: a table listing every registered flavor with `← ACTIVE` next to the default. At least one flavor row must be present.

**If verify fails**:
- "No variants configured" → either DSL block missing or no `register(...)` / `flavor(...)` calls inside.
- KMPF-V24 error → you mixed `dimensions {}` with legacy `flavorDimensions {} + flavors {}` in the same block. Pick one.

---

## 5. Required config: `buildConfigPackage`

### What you should have

```kotlin
kmpFlavors {
    generateBuildConfig.set(true)  // default; explicit is fine
    buildConfigPackage.set("com.example.app")  // REQUIRED when generateBuildConfig=true
    buildConfigClassName.set("BuildKonfig")  // default
}
```

### ✅ Verify

```bash
grep -rn 'buildConfigPackage\.set' \
  --include='*.gradle.kts' --include='*.gradle' .
```

**Expected output**: at least one match with a non-empty string value. Multiple matches OK (one per module that registers the plugin).

**If verify fails**:
- No matches AND `generateBuildConfig.set(true)` → plugin will halt at configuration with a hint. Add `buildConfigPackage.set("your.package.name")`.
- Multiple modules apply the plugin under the same `buildConfigPackage` → ONE module must be the codegen host. Set `codegenHost.set(true)` on that one; explicitly `set(false)` on the others.

---

## 6. Default variant resolves

### What you should have

When you run the build with no `-PkmpFlavor=` override, the plugin auto-resolves the default variant from each dimension's `isDefault` flag (or first flavor if no defaults set).

### ✅ Verify

```bash
./gradlew :listActiveVariant --no-daemon --no-configuration-cache 2>&1 | \
  grep -E 'Active|All'
```

**Expected output**: `Active : <variantName>` line + `All : <list of every registered variant>` line. The `Active` value must appear in the `All` list.

**If verify fails**:
- `Active :` is blank → no default flavor is set on any dimension. Mark one flavor per dimension with `isDefault.set(true)`.
- `Active : <unknownName>` not in `All` → you passed `-PkmpFlavor=<typo>`. Drop the override or fix the typo. KMPF-V06 will surface this warning at config time.

---

## 7. `BuildKonfig.kt` codegen produces output at the expected path

### What you should have

After running `./gradlew :generateFlavorBuildConfig`, the plugin writes:

```
{module}/build/generated/kmpFlavors/commonMain/kotlin/{packageDir}/BuildKonfig.kt
```

Where `{packageDir}` is your `buildConfigPackage` with dots replaced by `/`.

### ✅ Verify

```bash
# Replace :cmp-shared with the actual codegen-host module name
./gradlew :cmp-shared:generateFlavorBuildConfig --rerun-tasks \
  --no-daemon --no-configuration-cache 2>&1 | tail -5

# Then locate the file. Replace com.example.app with your buildConfigPackage.
PKG_DIR=$(echo "com.example.app" | tr '.' '/')
find . -path "*/build/generated/kmpFlavors/commonMain/kotlin/${PKG_DIR}/BuildKonfig.kt"
```

**Expected output**:
- Gradle build is SUCCESSFUL
- `find` returns at least one matching path

**If verify fails**:
- Gradle build failure → read the stack trace; common cause is `buildConfigPackage` unset (Section 5) or a KMPF-V** validator firing (Section 8).
- `find` returns nothing → the codegen-host module isn't the one you ran `:generateFlavorBuildConfig` against. Either change the module name in the command, or check which module logs `Generated …BuildKonfig.kt` in the build output.

---

## 8. Validator codes all pass (KMPF-V01 through V30)

### What you should have

At configuration time, the plugin runs the `KmpFlavorPluginValidator` against your configuration. ERRORS halt the build; WARNINGS surface but proceed.

### ✅ Verify

```bash
./gradlew :validateFlavors --no-daemon --no-configuration-cache 2>&1 | \
  grep -E 'KMPF-V|Validation passed|FAIL'
```

**Expected output**: `[KMP Flavors] Validation passed!` line. NO `KMPF-V0[1-9]` or `KMPF-V[12][0-9]` ERROR lines. WARNINGS (e.g. `KMPF-V05`, `KMPF-V06`, `KMPF-V15`, `KMPF-V16`, `KMPF-V17`, `KMPF-V19`, `KMPF-V21`) are advisory and do NOT fail adoption.

**If verify fails**:
- See the validator code catalog at [`docs/ERROR_CODES.md`](../../ERROR_CODES.md) (if present) or the inline catalog in [`KmpFlavorPluginValidator.kt`](../../../build-logic/flavor-plugin/src/main/kotlin/com/mobilebytelabs/kmpflavors/internal/KmpFlavorPluginValidator.kt). Each finding includes a `Fix:` line.

Notable codes for v2.7:
- KMPF-V01 — flavor name collides with a build type name
- KMPF-V02 — flavor declared without `dimension.set(...)` when dimensions are registered
- KMPF-V03 — dimension has no flavors
- KMPF-V07 — `buildConfigField` declared with an unsupported type
- KMPF-V08 — `buildMatrix=true` but no flavors registered
- KMPF-V23 — custom `buildConfigField` name collides with auto-derived `VARIANT_NAME` / `BUILD_TYPE` / `IS_*`
- KMPF-V24 — mixed `dimensions {}` + legacy `flavorDimensions {} / flavors {}` in same block
- KMPF-V25 — duplicate dimension name
- KMPF-V26 — `buildKonfig { secret(...) }` with schema < v2.1 manifest (degrades to placeholders)
- KMPF-V27 — `customField<T>` with unsupported type
- KMPF-V28 — `perTarget(name)` references a target not in `kotlin.targets`
- KMPF-V29 — `network { baseUrl("X" to ...) }` references unknown flavor
- KMPF-V30 — variant's active flavor has no matching `baseUrl`

---

## 9. AGP 9.x compatibility (only if you're on AGP 9)

### What you should have (AGP 9-only)

If your consumer build bumped AGP from 8.x to 9.x, four breaking changes affect your build (NOT the plugin — see [`AGP_9_MIGRATION_NOTES.md`](../../AGP_9_MIGRATION_NOTES.md) for the full cookbook):

1. `CommonExtension<*,*,*,*,*,*>` type params dropped → use concrete `ApplicationExtension`/`LibraryExtension`.
2. `dataBinding` block deprecated → remove from `buildFeatures {}`.
3. `com.android.library + kotlin("multiplatform")` co-application → use `com.android.kotlin.multiplatform.library`.
4. `dependencyGuard` reads variants at config time → wrap in `afterEvaluate`.

The plugin itself transparently works on AGP 8.2+ and AGP 9.x via the reflective `AgpBridge.kt`. No source changes required.

### ✅ Verify (only run if you're on AGP 9)

```bash
# Confirm you're on AGP 9
grep -E 'agp\s*=\s*"9\.' gradle/libs.versions.toml 2>/dev/null

# If yes, scan for the four landmines:
echo "=== CommonExtension landmine ==="
grep -rn 'CommonExtension<' --include='*.kt' --include='*.gradle.kts' .

echo "=== dataBinding landmine ==="
grep -rn 'dataBinding\s*=\s*true\|dataBinding\s*{' \
  --include='*.gradle.kts' --include='*.gradle' .

echo "=== library co-application landmine ==="
grep -rn 'com\.android\.library' --include='*.gradle.kts' --include='*.gradle' . | \
  while read line; do
    file=$(echo "$line" | cut -d: -f1)
    if grep -l 'kotlin("multiplatform")' "$file" > /dev/null 2>&1; then
      echo "$line  ← also applies kotlin(\"multiplatform\")"
    fi
  done

echo "=== dependencyGuard landmine ==="
grep -rn 'dependencyGuard\s*{' --include='*.gradle.kts' --include='*.gradle' . | \
  grep -v afterEvaluate
```

**Expected output**: every landmine section reports empty. (The CommonExtension grep should ONLY match `CommonExtension<…>` patterns — bare `CommonExtension` references are fine.)

**If verify fails**:
- Any landmine grep returns matches → see the matching section of [`AGP_9_MIGRATION_NOTES.md`](../../AGP_9_MIGRATION_NOTES.md) for the fix recipe.

**Note**: if you're on AGP 8.x, skip this section entirely. The plugin works transparently on 8.2+.

---

## 10. End-to-end smoke test

### What you should have

The full adoption is verified by a single command that exercises:
- plugin applied
- DSL valid
- BuildKonfig codegen produces non-empty output
- validators pass
- the default variant's compile task succeeds

### ✅ Verify

```bash
# Run the validator + listFlavors + a compile on the default variant
./gradlew :validateFlavors :listFlavors :generateFlavorBuildConfig \
  --no-daemon --no-configuration-cache 2>&1 | tail -20
```

**Expected output**:
- `BUILD SUCCESSFUL`
- `[KMP Flavors] Validation passed!` line
- A table with at least one flavor row
- `Generated /…/BuildKonfig.kt` line

**If verify fails**: each subtask reports its own error. Loop back to the corresponding section above (1-9) using the failure code.

---

## 100% adoption checklist

If every `✅ Verify` from Sections 1-10 (excluding Section 9 if you're on AGP 8.x) returned the expected output, you have:

- [x] Plugin pinned to v2.7.0
- [x] Toolchain meets all floors
- [x] `kmpFlavors {}` DSL present
- [x] At least one flavor / dimension registered
- [x] `buildConfigPackage` set
- [x] Default variant resolves
- [x] `BuildKonfig.kt` codegen produces output at the expected path
- [x] All validators pass (no KMPF-V** ERRORs)
- [x] AGP 9 landmines avoided (if applicable)
- [x] End-to-end smoke test green

**Congratulations — you are at 100% adoption of v2.7.0. No missing pieces.**

If you want to opt into additional v2.7 capabilities (matrix mode, per-variant publishing, BuildKonfig secrets/enums/network DSL, DI integration, analytics tags, SPM manifest, Compose hot-reload), see:

- [`MATRIX_MODE.md`](../../MATRIX_MODE.md) — `buildMatrix.set(true)` and per-variant compilations
- [`PUBLISHING.md`](../../PUBLISHING.md) — per-variant Maven / iOS / JS / npm publishing
- [`SECRETS_INTEGRATION.md`](../../SECRETS_INTEGRATION.md) — `buildKonfig { secret() }` vault integration
- [`DI_INTEGRATION.md`](../../DI_INTEGRATION.md) — `di { koin {} }` per-variant module codegen
- [`NETWORK_CONFIG.md`](../../NETWORK_CONFIG.md) — `buildKonfig { network {} }` BASE_URL + TIMEOUT codegen
- [`PRODUCT_FLAVORS.md`](../../PRODUCT_FLAVORS.md) — full DSL reference
- [`COMPATIBILITY_MATRIX.md`](../../COMPATIBILITY_MATRIX.md) — supported toolchain pairs
- [`MIGRATION_v2.6_TO_v2.7.md`](../../MIGRATION_v2.6_TO_v2.7.md) — the "you do not need to migrate" doc for v2.6 consumers
- [`AGP_9_MIGRATION_NOTES.md`](../../AGP_9_MIGRATION_NOTES.md) — AGP 9 consumer cookbook
- [`library.md`](library.md) — the mirror of this doc on the library side

These are optional: the adoption gate above is complete for the v2.7 minimum surface.
