# Migrating from v1.x to v2.0

> v2.0 is a fully-additive release. The Zero-Touch Adoption tenet (RFC §1.1) means **every consumer KMP module's `build.gradle.kts` is byte-identical between v1.x and v2.0**. Matrix mode is opt-in at a single point. v1.x semantics are preserved unchanged when matrix mode is off (the default).

This guide walks through the absolute minimum diff a consumer needs to land on v2.0.

---

## TL;DR — the smallest possible upgrade

1. Bump the plugin pin.
2. (Optional) Opt into matrix mode by adding **one line** to `gradle.properties` or the convention plugin.

That's it. No per-module changes.

---

## Step 1 — bump the plugin version

In your convention plugin or root `build.gradle.kts`:

```diff
 plugins {
-    id("io.github.mobilebytelabs.kmp-product-flavors") version "1.1.7"
+    id("io.github.mobilebytelabs.kmp-product-flavors") version "2.0.0"
 }
```

Or in `libs.versions.toml`:

```diff
 [versions]
-kmpProductFlavors = "1.1.7"
+kmpProductFlavors = "2.0.0"
```

**Test the upgrade without enabling matrix mode first**. v2.0 with `buildMatrix=false` is behaviourally identical to v1.x — your build should pass with no other changes.

---

## Step 2 — (optional) opt into matrix mode

Matrix mode adds compilation tasks for **inactive variants** alongside the v1.x active variant. Two opt-in shapes; pick whichever fits your convention plugin / CI ergonomics:

### Shape A — gradle.properties (single project-wide line)

```diff
 # gradle.properties
+kmpFlavors.buildMatrix=true
```

### Shape B — convention plugin (per-project override)

```diff
 // build-logic/convention/.../KMPFlavorsConventionPlugin.kt
 extensions.configure<KmpFlavorExtension> {
     buildConfigPackage.set("org.example.app")
+    buildMatrix.set(true)
 }
```

**Order constraint**: if any consumer file does `kotlin { sourceSets { val commonPaid by getting { dependencies { … } } } }` (per-variant deps, Q17), the `kmpFlavors { flavors { register(…) } }` block must run BEFORE the `kotlin { sourceSets { … } }` block — the plugin creates per-flavor source sets eagerly as flavors are registered.

---

## What you get when matrix mode is on

| Surface | Effect |
|---|---|
| `compile{Variant}Kotlin{Target}` tasks | One per inactive variant × non-Android target |
| `generate{Variant}BuildConfig` tasks | Per-variant `BuildKonfig.kt` in `build/generated/kmpFlavors/{variant}/kotlin/…` |
| `assembleAll{Target}Variants` + `assembleAllVariants` | Aggregates for CI sharding / dev convenience |
| `kmpFlavors.variants` | `NamedDomainObjectCollection<KmpFlavorVariant>` for `matching { … }.configureEach { … }` customisation |
| `variantFilter { … setIgnore(true) }` | AGP-style filter; `buildType == "staging"` works |

See [`MATRIX_MODE.md`](MATRIX_MODE.md) for full reference.

---

## Common upgrade scenarios

### Scenario 1 — single module, no flavors

Nothing changes. v2.0 is a drop-in version bump.

### Scenario 2 — multi-module project with a convention plugin

Edit the convention plugin once. Every module that applies the convention picks up matrix mode automatically. **Zero per-module file changes** — verified by `samples/kmp-project-template`'s byte-identical-diff test.

### Scenario 3 — consumer was using `variantFilter { exclude() }` in v1.x

Works unchanged in v2.0. If you want AGP-style ergonomics, replace `exclude()` with `setIgnore(true)`:

```diff
 variantFilter {
-    if (variantName == "paidStaging") exclude()
+    if (flavors.any { it.name == "paid" } && buildType == "staging") setIgnore(true)
 }
```

Both forms remain supported in v2.0 — `setIgnore(true)` is just a synonym (RFC §3 Q20-A).

### Scenario 4 — consumer wants per-variant Maven publications

```diff
 plugins {
+    `maven-publish`
     id("io.github.mobilebytelabs.kmp-product-flavors")
 }
 kmpFlavors {
     buildMatrix.set(true)
+    publishMatrix.set(true)
     // ...
 }
```

`publishMatrix=true` registers a `MavenPublication` per inactive variant × JVM target with the variant name as the Maven classifier. Standard `publishVariant{X}PublicationToMavenLocal` tasks are derived by Gradle's `maven-publish`.

(W4.1 scope: JVM-only. iOS/JS/WasmJs per-variant publishing has KMP-specific complications and lands in a v2.0 post-GA follow-up if survey demand justifies it.)

---

## Things v2.0 deliberately does NOT change

- Active-variant semantics (whichever variant wins by `isDefault` / `-PkmpFlavor=…`) — unchanged.
- AGP bridge (`bridgeAgpProductFlavors`) — unchanged.
- SPM manifest generator — unchanged.
- `listFlavors` / `validateFlavors` / `kmpFlavorInit` tasks — unchanged.
- `generateRunConfigurations` — unchanged.

---

## v1.x deprecation timeline

v1.x will continue to receive critical-fix releases for **6 months** after v2.0 GA. After that v1.x is EOL. The v1.1.6 `docs/ROLLBACK.md` is the rollback anchor for any v2.x → v1.x emergency.

---

## Automated migration assistant

```bash
./gradlew kmpFlavorsMigrateToV2 --dry-run
```

Prints a structured per-project migration report. Add `--json` for machine-readable output. The task is no-op safe — it never modifies your project; it only reports.
