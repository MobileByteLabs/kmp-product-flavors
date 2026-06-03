# Adopting kmp-product-flavors v2.7 (consumer side)

> **Format**: AI-executable verify gates. Paste this doc into Claude/Cursor/Copilot and the agent will run every `## ✅ Verify` block in order. If all pass, you are at **100% adoption** of v2.7 — no missing pieces.
>
> **Mirror**: every section here has a paired claim in [`library.md`](library.md). If a library claim has no verifier here, that's a bug — file it.
>
> **Reference implementation**: [`samples/kmp-project-template`](https://github.com/openMF/kmp-project-template) is our **first-party canonical consumer** owned by the Mifos Initiative. Every section below cites the exact file in that template that demonstrates the verify. If you're starting from zero, copy that template's `build-logic/convention/` structure as your starting point.
>
> **New consumers**: read top-to-bottom; this doc walks you from zero to fully-integrated.
> **Existing v2.6 consumers**: read [`MIGRATION_v2.6_TO_v2.7.md`](../../MIGRATION_v2.6_TO_v2.7.md) first (it opens with "You do not need to migrate"), then run the verify gates here to confirm 100% adoption.

## How to use this doc

```bash
# 1. From the root of your KMP consumer project
cd /path/to/your/consumer

# 2. Walk through Sections 1-14 in order
# 3. For each, run the ✅ Verify block
# 4. If "Expected output" matches → continue
# 5. If verify fails → apply "If verify fails" remediation → re-run

# At the end of Section 14, you have 100% adoption.
```

---

## 1. Plugin pinned to v2.7.0 via `libs.versions.toml`

### What you should have

```toml
# gradle/libs.versions.toml
[versions]
kmpProductFlavors = "2.7.0"

[plugins]
# Gradle plugin id — used by modules that apply directly via plugins{} (Section 3a)
kmp-product-flavors = { id = "io.github.mobilebytelabs.kmp-product-flavors", version.ref = "kmpProductFlavors" }

[libraries]
# Maven artifact — used by build-logic/convention/build.gradle.kts to take a
# compileOnly dependency so KMPFlavorsConventionPlugin can pluginManager.apply()
# the upstream plugin programmatically (Section 3b).
kmp-product-flavors-plugin = { group = "io.github.mobilebytelabs.kmpflavors", name = "flavor-plugin", version.ref = "kmpProductFlavors" }
```

**Why both entries?** Two different consumption styles need two different references:
- The `[plugins]` alias is for the **direct-apply pattern** (Section 3a).
- The `[libraries]` entry is for the **convention-plugin pattern** (Section 3b) — your `build-logic/convention/` module needs the maven artifact on its `compileOnly` classpath to compile against `KmpFlavorPlugin::class.java`.

### Reference in kmp-project-template

- [`gradle/libs.versions.toml`](https://github.com/openMF/kmp-project-template/blob/main/gradle/libs.versions.toml) — both entries side-by-side

### ✅ Verify

```bash
grep -E 'kmpProductFlavors\s*=' gradle/libs.versions.toml
grep -E 'kmp-product-flavors\s*=\s*\{\s*id\s*=' gradle/libs.versions.toml
grep -E 'kmp-product-flavors-plugin\s*=\s*\{\s*group\s*=' gradle/libs.versions.toml
```

**Expected output**:
- A `kmpProductFlavors = "2.7.0"` (or higher patch) version entry
- A `[plugins].kmp-product-flavors` alias entry
- A `[libraries].kmp-product-flavors-plugin` entry pointing at `io.github.mobilebytelabs.kmpflavors:flavor-plugin`

**If verify fails**:
- No `kmpProductFlavors` version → add the version entry above
- Only one consumption entry → add the missing one (plugin alias OR library entry, depending on the adoption pattern you'll use in Section 3)
- Version < 2.7.0 → bump. Floor at AGP 8.2+ is unchanged, so the bump is safe if your toolchain meets Section 2

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
java -version 2>&1 | head -1
./gradlew --version 2>&1 | grep '^Gradle'
grep -E '^(kotlin|agp|kotlinGradlePlugin|androidGradlePlugin)\s*=' gradle/libs.versions.toml 2>/dev/null
```

**Expected output**:
- `java -version` reports `17.x.x` or higher
- Gradle 8.0+
- Kotlin 2.0.21+
- AGP 8.2+ (if Android consumer)

**If verify fails**:
- JDK < 17 → `sdk install java 17.0.13-zulu`
- Gradle < 8.0 → `./gradlew wrapper --gradle-version 8.10`
- KGP < 2.0.21 → bump `kotlin = "..."` in `libs.versions.toml`
- AGP < 8.2 → see [`AGP_9_MIGRATION_NOTES.md`](../../AGP_9_MIGRATION_NOTES.md) if you also want AGP 9. The plugin works on AGP 8.2+ transparently.

---

## 3. Choose your adoption pattern

The plugin supports two consumption styles. Pick one based on your project shape:

| Pattern | When to use | Where the DSL config lives |
|---|---|---|
| **3a. Direct apply** | Single-module project, or every-module-has-its-own-config | `plugins { alias(libs.plugins.kmp.product.flavors) }` + `kmpFlavors { ... }` in each module's `build.gradle.kts` |
| **3b. Convention plugin** (Recommended for multi-module) | Multi-module project that should share ONE flavor contract across N modules | `build-logic/convention/src/main/kotlin/KMPFlavorsConventionPlugin.kt` applies + configures the upstream plugin once; consumer modules apply the convention plugin |

**Production consumers (including the first-party reference [`samples/kmp-project-template`](https://github.com/openMF/kmp-project-template)) use 3b.** It's strictly more powerful — you can still expose direct-apply if you want.

### ✅ Verify

```bash
# Check whether you have a build-logic/convention/ module
ls -d build-logic/convention/ 2>/dev/null && echo "→ convention-plugin pattern (3b)"
# Else
grep -rln '^kmpFlavors\s*{' --include='*.gradle.kts' . 2>/dev/null | grep -v 'build-logic' | head -3
```

**Expected output**: exactly one of these:
- `build-logic/convention/` exists → you're on pattern 3b → continue to Section 4b
- `kmpFlavors {` appears in one or more per-module `build.gradle.kts` files → you're on pattern 3a → continue to Section 4a

**If neither matches**: the plugin isn't wired yet. Skip to Section 4 and pick a pattern.

---

## 4a. Plugin applied (direct-apply pattern, 3a)

### What you should have

```kotlin
// build.gradle.kts of the module(s) where you want flavors
plugins {
    alias(libs.plugins.kmp.product.flavors)
}

kmpFlavors {
    buildConfigPackage.set("com.example.app")
    flavorDimensions { register("tier") }
    flavors {
        register("free") { dimension.set("tier"); isDefault.set(true) }
        register("paid") { dimension.set("tier") }
    }
}
```

### ✅ Verify (skip if you're on 3b)

```bash
grep -rln 'alias(libs\.plugins\.kmp\.product\.flavors)' --include='*.gradle.kts' .
grep -rln '^kmpFlavors\s*{' --include='*.gradle.kts' .
```

**Expected output**: at least one match for each grep.

**If verify fails**:
- No alias match → add the `plugins { alias(libs.plugins.kmp.product.flavors) }` block to the module that should drive flavors.
- No `kmpFlavors {` match → you applied the plugin but never configured it. Add the block above.

---

## 4b. Plugin applied + configured via convention plugin (Recommended, 3b)

### What you should have

Three files work together:

**File 1**: `build-logic/convention/build.gradle.kts` — declares the convention plugin + takes a compileOnly dep on the upstream maven artifact:

```kotlin
plugins {
    `kotlin-dsl`
}

dependencies {
    // Compile-time only; the runtime apply happens via pluginManager.apply()
    compileOnly(libs.kmp.product.flavors.plugin)
}

gradlePlugin {
    plugins {
        register("kmpFlavors") {
            id = "org.convention.kmp.flavors"
            implementationClass = "KMPFlavorsConventionPlugin"
        }
    }
}
```

**File 2**: `build-logic/convention/src/main/kotlin/KMPFlavorsConventionPlugin.kt` — applies the upstream plugin programmatically + configures it:

```kotlin
import com.android.build.api.dsl.CommonExtension
import com.mobilebytelabs.kmpflavors.KmpFlavorExtension
import com.mobilebytelabs.kmpflavors.KmpFlavorPlugin
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

class KMPFlavorsConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        // 1. Apply the upstream plugin
        pluginManager.apply(KmpFlavorPlugin::class.java)

        // 2. Configure via extensions.configure<KmpFlavorExtension>
        extensions.configure<KmpFlavorExtension> {
            buildConfigPackage.set(libs.findVersion("appId").get().requiredVersion)
            enableBuildTypes.set(true)

            flavorDimensions {
                register("contentType") { priority.set(0) }
            }
            flavors {
                register("demo") {
                    dimension.set("contentType"); isDefault.set(true)
                    buildConfigField("Boolean", "IS_DEMO_BUILD", "true")
                }
                register("prod") {
                    dimension.set("contentType")
                    buildConfigField("Boolean", "IS_DEMO_BUILD", "false")
                }
            }
            buildTypes {
                register("debug") { isDefault.set(true); isDebuggable.set(true) }
                register("release") { isMinifyEnabled.set(true) }
            }

            // Optional: extension hook for downstream forks — see Section 11
            // LocalFlavorsLoader.applyIfPresent(this, target)
        }

        // 3. AGP-side helper for pure Android modules — see Section 10
        // listOf("com.android.application", "com.android.library").forEach { agpId ->
        //     pluginManager.withPlugin(agpId) {
        //         extensions.findByType(CommonExtension::class.java)?.let { configureFlavors(it) }
        //     }
        // }
    }
}
```

**File 3**: `gradle/libs.versions.toml` adds an alias for the LOCAL convention plugin so modules can apply it via `alias(...)`:

```toml
[plugins]
kmp-flavors-convention = { id = "org.convention.kmp.flavors" }
```

Then each consumer module applies it:

```kotlin
// e.g. cmp-shared/build.gradle.kts
plugins {
    alias(libs.plugins.kmp.flavors.convention)
}
```

### Reference in kmp-project-template

- [`build-logic/convention/build.gradle.kts`](https://github.com/openMF/kmp-project-template/blob/main/build-logic/convention/build.gradle.kts) — convention plugin registration
- [`build-logic/convention/src/main/kotlin/KMPFlavorsConventionPlugin.kt`](https://github.com/openMF/kmp-project-template/blob/main/build-logic/convention/src/main/kotlin/KMPFlavorsConventionPlugin.kt) — full impl

### ✅ Verify (skip if you're on 3a)

```bash
# Convention plugin registration exists
grep -E 'register\("kmpFlavors"\)' build-logic/convention/build.gradle.kts
grep -E 'id\s*=\s*"org\.convention\.kmp\.flavors"' build-logic/convention/build.gradle.kts

# Convention plugin impl applies + configures the upstream plugin
grep -E 'pluginManager\.apply\(KmpFlavorPlugin::class\.java\)' \
  build-logic/convention/src/main/kotlin/KMPFlavorsConventionPlugin.kt
grep -E 'extensions\.configure<KmpFlavorExtension>' \
  build-logic/convention/src/main/kotlin/KMPFlavorsConventionPlugin.kt

# libs.versions.toml exposes the LOCAL convention alias
grep -E 'kmp-flavors-convention\s*=\s*\{\s*id\s*=\s*"org\.convention\.kmp\.flavors"' \
  gradle/libs.versions.toml

# At least one module applies the convention via alias()
grep -rln 'alias(libs\.plugins\.kmp\.flavors\.convention)\|apply\("org\.convention\.kmp\.flavors"\)' \
  --include='*.gradle.kts' --include='*.kt' . | head -5
```

**Expected output**: every grep returns at least one match.

**If verify fails**:
- Missing `register("kmpFlavors")` → declare the convention plugin in `build-logic/convention/build.gradle.kts`
- Missing `pluginManager.apply(KmpFlavorPlugin::class.java)` → add the programmatic apply
- Missing `extensions.configure<KmpFlavorExtension>` → add the configuration block
- Missing libs.versions.toml alias → add `kmp-flavors-convention = { id = "org.convention.kmp.flavors" }`
- No module applies the convention plugin → apply it from your CMP feature / KMP library convention plugins (chained), or directly via `alias(libs.plugins.kmp.flavors.convention)`

---

## 5. Flavors + dimensions registered (any v2.5+ style)

### What you should have

One of these two equivalent styles inside your `extensions.configure<KmpFlavorExtension> { ... }` (3b) or `kmpFlavors { ... }` (3a) block:

**Flat DSL** (v2.4+, used by kmp-project-template):
```kotlin
flavorDimensions { register("tier") }
flavors {
    register("free") { dimension.set("tier"); isDefault.set(true) }
    register("paid") { dimension.set("tier") }
}
```

**Ergonomic sugar** (v2.5+, equivalent):
```kotlin
dimensions {
    dimension("tier") {
        flavor("free") { isDefault.set(true) }
        flavor("paid")
    }
}
```

> **KMPF-V24**: mixing both styles in the same configure block fires a configuration-time ERROR. Pick one.

### ✅ Verify

```bash
./gradlew :listFlavors --no-daemon --no-configuration-cache 2>&1 | tail -30
```

**Expected output**: a table listing every registered flavor with `← ACTIVE` next to the default. At least one flavor row.

**If verify fails**:
- "No variants configured" → flavor block missing or empty inside the configure block.
- KMPF-V24 error → you mixed `dimensions {}` with legacy `flavorDimensions {} + flavors {}`. Pick one.

---

## 6. `buildConfigPackage` — set, ideally from a single source of truth

### What you should have (minimum)

```kotlin
generateBuildConfig.set(true)               // default — explicit is fine
buildConfigPackage.set("com.example.app")   // REQUIRED when generateBuildConfig=true
buildConfigClassName.set("BuildKonfig")     // default
```

### What kmp-project-template does (canonical)

The brand identifier is stored ONCE in `gradle/libs.versions.toml` under `[versions].appId`, and every convention plugin reads it from there. Forking the template = changing one line.

```toml
# gradle/libs.versions.toml
[versions]
appId = "org.mifos.kmp.template"
```

```kotlin
// KMPFlavorsConventionPlugin.kt
buildConfigPackage.set(libs.findVersion("appId").get().requiredVersion)
```

### Reference in kmp-project-template

- [`build-logic/convention/src/main/kotlin/KMPFlavorsConventionPlugin.kt`](https://github.com/openMF/kmp-project-template/blob/main/build-logic/convention/src/main/kotlin/KMPFlavorsConventionPlugin.kt)

### ✅ Verify

```bash
grep -rln 'buildConfigPackage\.set(' \
  --include='*.gradle.kts' --include='*.kt' .
grep -E '^appId\s*=' gradle/libs.versions.toml 2>/dev/null
```

**Expected output**: at least one match for `buildConfigPackage.set(...)`. If you're following the kmp-project-template pattern, `appId = "..."` also exists in `libs.versions.toml`.

**If verify fails**:
- No `buildConfigPackage.set(...)` AND `generateBuildConfig` is true (default or explicit) → plugin halts at configuration. Add the call.
- Multiple modules under the same `buildConfigPackage` → ONE module must be the codegen host. Set `codegenHost.set(true)` on that one; explicitly `set(false)` on the others (see [`MATRIX_MODE.md`](../../MATRIX_MODE.md) for the claim mechanism).

---

## 7. Default variant resolves

### What you should have

When you build with no `-PkmpFlavor=` override, the plugin auto-resolves the default variant from each dimension's `isDefault` flag.

### ✅ Verify

```bash
./gradlew :listActiveVariant --no-daemon --no-configuration-cache 2>&1 | grep -E 'Active|All'
```

**Expected output**: `Active : <variantName>` line + `All : <list>` line. The `Active` value appears in the `All` list.

**If verify fails**:
- `Active :` blank → no default flavor set. Mark one flavor per dimension with `isDefault.set(true)`.
- `Active : <unknownName>` not in `All` → you passed `-PkmpFlavor=<typo>`. Drop the override or fix the typo. KMPF-V06 warns at config time.

---

## 8. `BuildKonfig.kt` codegen produces output at the expected path

### What you should have

```
{codegen-host-module}/build/generated/kmpFlavors/commonMain/kotlin/{packageDir}/BuildKonfig.kt
```

`{packageDir}` is your `buildConfigPackage` with dots replaced by `/`.

### What kmp-project-template does (canonical)

The codegen host is `cmp-navigation` (deterministic across local + CI runs). Other modules log `skipping FlavorConfig codegen — already generated by :cmp-navigation` and consume the same file. This claim mechanism is what `.github/workflows/pr-check.yml` validates against.

### Reference in kmp-project-template + this repo

- [`.github/workflows/pr-check.yml` (Validate FlavorConfig codegen step)](../../../.github/workflows/pr-check.yml) — the CI step in THIS repo that does this exact verify against the kmp-project-template submodule

### ✅ Verify

```bash
# Replace :cmp-navigation with YOUR codegen-host module name
./gradlew :cmp-navigation:generateFlavorBuildConfig --rerun-tasks \
  --no-daemon --no-configuration-cache 2>&1 | tail -5

PKG_DIR=$(grep '^appId\s*=' gradle/libs.versions.toml | cut -d'"' -f2 | tr '.' '/')
find . -path "*/build/generated/kmpFlavors/commonMain/kotlin/${PKG_DIR}/BuildKonfig.kt"
```

**Expected output**:
- Gradle BUILD SUCCESSFUL
- `find` returns at least one matching path
- The file contains `VARIANT_NAME` + `IS_<FLAVOR>` constants

**If verify fails**:
- Build failure → read the stack trace; common cause is `buildConfigPackage` unset (Section 6) or KMPF-V** ERROR (Section 9).
- `find` empty → wrong module name. Search for `Generated …BuildKonfig.kt` in the build output to find the actual codegen-host module.

---

## 9. Validator codes V01–V30 pass

### What you should have

`KmpFlavorPluginValidator` runs at configuration time. ERRORs halt the build; WARNINGs surface but proceed.

### ✅ Verify

```bash
./gradlew :validateFlavors --no-daemon --no-configuration-cache 2>&1 | \
  grep -E 'KMPF-V|Validation passed|FAIL'
```

**Expected output**: `[KMP Flavors] Validation passed!` line. NO ERROR-severity KMPF-V** codes. WARNINGs (V05, V06, V15, V16, V17, V19, V21) are advisory.

**If verify fails**: each finding includes a `Fix:` line. See [`ERROR_CODES.md`](../../ERROR_CODES.md) for the full catalog.

---

## 10. AGP-only modules: `configureFlavors(CommonExtension)` helper

### When this applies

If you have modules that apply `com.android.application` or `com.android.library` WITHOUT `kotlin("multiplatform")` — e.g. a pure Android app module — then `KmpFlavorPlugin` returns early (it requires `KotlinMultiplatformExtension`). The AGP-side flavor registration won't happen via the plugin's normal `androidComponents.finalizeDsl` path.

### What you should have

Add an `org.convention.configureFlavors(CommonExtension)` helper that mirrors your KMP-side flavor declaration, then call it from your convention plugin via `pluginManager.withPlugin("com.android.application")` and `withPlugin("com.android.library")`.

```kotlin
// build-logic/convention/src/main/kotlin/org/convention/AppFlavor.kt
package org.convention

import com.android.build.api.dsl.ApplicationExtension
import com.android.build.api.dsl.ApplicationProductFlavor
import com.android.build.api.dsl.CommonExtension

@Suppress("EnumEntryName")
enum class FlavorDimension { contentType }

@Suppress("EnumEntryName")
enum class AppFlavor(val dimension: FlavorDimension, val applicationIdSuffix: String? = null) {
    demo(FlavorDimension.contentType, ".demo"),
    prod(FlavorDimension.contentType),
}

/**
 * Idempotent — skips any flavor or dimension already present.
 */
fun configureFlavors(commonExtension: CommonExtension<*, *, *, *, *, *>) {
    commonExtension.apply {
        if (FlavorDimension.contentType.name !in flavorDimensions) {
            flavorDimensions += FlavorDimension.contentType.name
        }
        productFlavors {
            AppFlavor.values().forEach { flv ->
                if (findByName(flv.name) == null) {
                    create(flv.name) {
                        dimension = flv.dimension.name
                        if (this@apply is ApplicationExtension && this is ApplicationProductFlavor) {
                            flv.applicationIdSuffix?.let { applicationIdSuffix = it }
                        }
                    }
                }
            }
        }
    }
}
```

Then wire it from your convention plugin:

```kotlin
// In KMPFlavorsConventionPlugin.apply()
listOf("com.android.application", "com.android.library").forEach { agpId ->
    pluginManager.withPlugin(agpId) {
        extensions.findByType(CommonExtension::class.java)?.let { configureFlavors(it) }
    }
}
```

### Reference in kmp-project-template

- [`build-logic/convention/src/main/kotlin/org/convention/AppFlavor.kt`](https://github.com/openMF/kmp-project-template/blob/main/build-logic/convention/src/main/kotlin/org/convention/AppFlavor.kt)
- [`build-logic/convention/src/main/kotlin/KMPFlavorsConventionPlugin.kt`](https://github.com/openMF/kmp-project-template/blob/main/build-logic/convention/src/main/kotlin/KMPFlavorsConventionPlugin.kt) (bottom — withPlugin wiring)

### ✅ Verify

```bash
# Only matters if you HAVE pure-Android modules (com.android.application without kotlin("multiplatform"))
HAS_PURE_ANDROID=$(grep -rln 'com\.android\.application' --include='*.gradle.kts' . | \
  xargs -I {} sh -c 'grep -L "kotlin(\"multiplatform\")" {} 2>/dev/null' | head -1)

if [ -n "$HAS_PURE_ANDROID" ]; then
  grep -rln 'fun configureFlavors\s*(' \
    --include='*.kt' build-logic/ 2>/dev/null
  grep -E 'withPlugin\("com\.android\.(application|library)"\)' \
    build-logic/convention/src/main/kotlin/KMPFlavorsConventionPlugin.kt 2>/dev/null
fi
```

**Expected output**:
- If you HAVE pure-Android modules → both greps return at least one match.
- If you have NO pure-Android modules → both greps empty. Skip this section.

**If verify fails (and you have pure-Android modules)**:
- No `configureFlavors` helper → AGP product flavors won't be registered on those modules. Add the helper above.
- No `withPlugin("com.android.application")` wiring → the helper is defined but never called. Wire it in your convention plugin.

---

## 11. Downstream extension hook: `LocalFlavorsLoader` pattern (optional)

### When this applies

If your template / convention plugin is consumed by N downstream forks, and each fork wants to ADD flavors without editing the shared convention plugin (which gets overwritten on every template sync), use the `LocalFlavorsLoader` reflective hook pattern.

### What you should have

```kotlin
// build-logic/convention/src/main/kotlin/LocalFlavorsLoader.kt
import com.mobilebytelabs.kmpflavors.KmpFlavorExtension
import org.gradle.api.Project

object LocalFlavorsLoader {
    private const val LOCAL_FLAVORS_FQN = "local.LocalFlavors"
    private const val LOCAL_FLAVORS_METHOD = "apply"

    fun applyIfPresent(ext: KmpFlavorExtension, project: Project) {
        runCatching {
            val cls = Class.forName(LOCAL_FLAVORS_FQN)
            val method = cls.getDeclaredMethod(
                LOCAL_FLAVORS_METHOD,
                KmpFlavorExtension::class.java,
                Project::class.java,
            )
            method.invoke(null, ext, project)
        }.onFailure { e ->
            if (e is ClassNotFoundException) {
                project.logger.info("[KMPFlavors] No local.LocalFlavors override — using base only.")
            } else {
                project.logger.warn("[KMPFlavors] Failed to apply local.LocalFlavors: ${e.message}")
            }
        }
    }
}
```

Call it from the convention plugin as the **last** statement inside `extensions.configure<KmpFlavorExtension> { ... }` so the local override sees the fully-populated extension. Forks create:

```kotlin
// build-logic/convention/src/main/kotlin/local/LocalFlavors.kt (NOT synced; survives template updates)
package local

import com.mobilebytelabs.kmpflavors.KmpFlavorExtension
import org.gradle.api.Project

object LocalFlavors {
    @JvmStatic
    fun apply(ext: KmpFlavorExtension, project: Project) {
        ext.flavors {
            register("enterprise") { /* fork-specific flavor */ }
        }
    }
}
```

### Reference in kmp-project-template

- [`build-logic/convention/src/main/kotlin/LocalFlavorsLoader.kt`](https://github.com/openMF/kmp-project-template/blob/main/build-logic/convention/src/main/kotlin/LocalFlavorsLoader.kt)
- `build-logic/convention/src/main/kotlin/local/` directory — excluded from `sync-dirs.sh` so forks' local files survive every template sync

### ✅ Verify (only if your template is forked downstream)

```bash
grep -l 'LocalFlavorsLoader\|local\.LocalFlavors' \
  build-logic/convention/src/main/kotlin/KMPFlavorsConventionPlugin.kt 2>/dev/null
ls -d build-logic/convention/src/main/kotlin/local 2>/dev/null
```

**Expected output** (only if you ARE the template):
- KMPFlavorsConventionPlugin.kt references the loader
- `local/` directory exists (even if empty — `.gitkeep` is fine)

**If verify fails (and your template is forked)**:
- No loader call → forks have no extension point. Add the loader + the `local/` directory.
- Loader defined but never called → wire it as the LAST statement inside `extensions.configure<KmpFlavorExtension> { ... }`.

If your project is NOT a template for downstream forks, skip this section entirely.

---

## 12. AGP 9.x compatibility (conditional — only if you're on AGP 9)

### What you should have (AGP 9-only)

The plugin itself is AGP 9 compatible — no source changes required. Four AGP 9 breaking changes affect your CONSUMER build (not the plugin):

1. `CommonExtension<*,*,*,*,*,*>` type params dropped → use concrete `ApplicationExtension`/`LibraryExtension`.
2. `dataBinding` block deprecated → remove from `buildFeatures {}`.
3. `com.android.library + kotlin("multiplatform")` co-application → use `com.android.kotlin.multiplatform.library`.
4. `dependencyGuard` reads variants at config time → wrap in `afterEvaluate`.

Full cookbook: [`AGP_9_MIGRATION_NOTES.md`](../../AGP_9_MIGRATION_NOTES.md).

### ✅ Verify (only run if you're on AGP 9)

```bash
grep -E 'agp\s*=\s*"9\.' gradle/libs.versions.toml 2>/dev/null

# Four landmines:
grep -rn 'CommonExtension<' --include='*.kt' --include='*.gradle.kts' .
grep -rn 'dataBinding\s*=\s*true\|dataBinding\s*{' --include='*.gradle.kts' .
grep -rn 'com\.android\.library' --include='*.gradle.kts' . | \
  while read line; do f=$(echo "$line" | cut -d: -f1); grep -l 'kotlin("multiplatform")' "$f" 2>/dev/null && echo "  ← co-application landmine"; done
grep -rn 'dependencyGuard\s*{' --include='*.gradle.kts' . | grep -v afterEvaluate
```

**Expected output**: each landmine grep returns empty (the `CommonExtension<...>` grep should match ONLY parameterized usage; bare `CommonExtension` is fine).

**If verify fails**: see the matching section of [`AGP_9_MIGRATION_NOTES.md`](../../AGP_9_MIGRATION_NOTES.md) for the recipe.

Skip this section if you're on AGP 8.x.

---

## 13. End-to-end smoke test

### What you should have

A single command that exercises plugin + DSL + codegen + validators + a compile.

### ✅ Verify

```bash
./gradlew :validateFlavors :listFlavors :generateFlavorBuildConfig \
  --no-daemon --no-configuration-cache 2>&1 | tail -20
```

**Expected output**:
- `BUILD SUCCESSFUL`
- `[KMP Flavors] Validation passed!`
- A table with at least one flavor row
- `Generated /.../BuildKonfig.kt` line

**If verify fails**: each subtask reports its own error. Loop back to the corresponding section.

---

## 14. Reference implementation: `samples/kmp-project-template`

### What you should know

`samples/kmp-project-template` is our **first-party canonical consumer**, owned by the Mifos Initiative. It's the reference implementation for every adoption pattern in this doc:

| Pattern | Location in kmp-project-template |
|---|---|
| 1.  Plugin pinned via `libs.versions.toml` (both entries) | [`gradle/libs.versions.toml`](https://github.com/openMF/kmp-project-template/blob/main/gradle/libs.versions.toml) |
| 3b. Convention-plugin adoption | [`build-logic/convention/`](https://github.com/openMF/kmp-project-template/tree/main/build-logic/convention) |
| 4b. Plugin applied + configured | [`KMPFlavorsConventionPlugin.kt`](https://github.com/openMF/kmp-project-template/blob/main/build-logic/convention/src/main/kotlin/KMPFlavorsConventionPlugin.kt) |
| 6.  Single-source-of-truth `buildConfigPackage` via `[versions].appId` | `libs.findVersion("appId").get().requiredVersion` inside KMPFlavorsConventionPlugin |
| 8.  Codegen-host claim mechanism | `cmp-navigation` is the deterministic winner |
| 10. AGP-only-module helper | [`org/convention/AppFlavor.kt`](https://github.com/openMF/kmp-project-template/blob/main/build-logic/convention/src/main/kotlin/org/convention/AppFlavor.kt) |
| 11. Downstream extension hook | [`LocalFlavorsLoader.kt`](https://github.com/openMF/kmp-project-template/blob/main/build-logic/convention/src/main/kotlin/LocalFlavorsLoader.kt) + the `local/` directory excluded from `sync-dirs.sh` |
| Chained convention application | [`KMPLibraryConventionPlugin.kt`](https://github.com/openMF/kmp-project-template/blob/main/build-logic/convention/src/main/kotlin/KMPLibraryConventionPlugin.kt) applies `org.convention.kmp.flavors` transitively |

If you're starting from zero, **copy this template's `build-logic/convention/` structure** as your starting point.

### ✅ Verify

```bash
# Confirm you've got the same shape as the reference
test -f build-logic/convention/src/main/kotlin/KMPFlavorsConventionPlugin.kt && echo "✓ convention plugin"
test -f build-logic/convention/src/main/kotlin/org/convention/AppFlavor.kt && echo "✓ AGP helper (optional)"
test -f build-logic/convention/src/main/kotlin/LocalFlavorsLoader.kt && echo "✓ downstream extension hook (optional)"
```

**Expected output**: at minimum, the convention plugin file exists. The other two are optional based on Sections 10 + 11 conditions.

---

## 100% adoption checklist

If every applicable `✅ Verify` from Sections 1–13 returned the expected output, you have:

- [x] Plugin pinned to v2.7.0 in `libs.versions.toml` (both `[plugins]` alias + `[libraries]` artifact entry — Section 1)
- [x] Toolchain meets all floors (Section 2)
- [x] Adoption pattern chosen — 3a direct or 3b convention plugin (Section 3)
- [x] Plugin applied + configured via your chosen pattern (Section 4a or 4b)
- [x] At least one flavor + dimension registered (Section 5)
- [x] `buildConfigPackage` set (preferably from `[versions].appId` — Section 6)
- [x] Default variant resolves (Section 7)
- [x] `BuildKonfig.kt` codegen produces output at the expected path (Section 8)
- [x] All validators pass — no KMPF-V** ERRORs (Section 9)
- [x] AGP-only modules have the `configureFlavors` helper wired (if applicable — Section 10)
- [x] Downstream extension hook in place (if you're a template — Section 11)
- [x] AGP 9 landmines avoided (if on AGP 9 — Section 12)
- [x] End-to-end smoke test green (Section 13)

**Congratulations — you are at 100% adoption of v2.7.0. No missing pieces.**

## Optional v2.7 capabilities

The adoption gate above covers the **minimum**. v2.7 also supports:

- [`MATRIX_MODE.md`](../../MATRIX_MODE.md) — `buildMatrix.set(true)` + per-variant compilations
- [`PUBLISHING.md`](../../PUBLISHING.md) — per-variant Maven / iOS / JS / npm publishing
- [`SECRETS_INTEGRATION.md`](../../SECRETS_INTEGRATION.md) — `buildKonfig { secret() }` vault integration
- [`DI_INTEGRATION.md`](../../DI_INTEGRATION.md) — `di { koin {} }` per-variant module codegen
- [`NETWORK_CONFIG.md`](../../NETWORK_CONFIG.md) — `buildKonfig { network {} }` BASE_URL + TIMEOUT codegen
- [`PRODUCT_FLAVORS.md`](../../PRODUCT_FLAVORS.md) — full DSL reference
- [`COMPATIBILITY_MATRIX.md`](../../COMPATIBILITY_MATRIX.md) — supported toolchain pairs

These are opt-in; the adoption gate above is complete for the v2.7 minimum surface.

## See also

- [`library.md`](library.md) — the mirror of this doc on the library side
- [`../README.md`](../README.md) — the adoption-doc pattern explainer
- [`../../MIGRATION_v2.6_TO_v2.7.md`](../../MIGRATION_v2.6_TO_v2.7.md) — incremental migration from v2.6
- [`../../AGP_9_MIGRATION_NOTES.md`](../../AGP_9_MIGRATION_NOTES.md) — AGP-9 consumer cookbook
- [openMF/kmp-project-template](https://github.com/openMF/kmp-project-template) — first-party canonical consumer
