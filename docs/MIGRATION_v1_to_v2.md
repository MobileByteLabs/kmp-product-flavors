# Migration: v1.x → v2.x

> **Critical timeline note**: The v1.x compat shim is scheduled for removal on **2026-11-14** per RFC §3 Q15 (6-month deprecation window from v2.0 GA on 2026-05-14). After that date, `kmpFlavors.activeFlavor`-style v1.x DSL throws `KMPF-V21` ERROR. Migrate before the cutoff.

## TL;DR

```kotlin
// v1.x — DEPRECATED, removed 2026-11-14
kmpFlavors {
    activeFlavor.set("free")
}

// v2.x — current
kmpFlavors {
    flavors {
        register("free") { isDefault.set(true) }
        register("paid")
    }
}
```

Drop-in for projects that don't use matrix mode. Add `autoEnable.set(false)` if your existing code reads `BuildKonfig` from `commonMain` (most v1.x consumers do).

---

## Major changes between v1.x and v2.x

### 1. Flavor declaration shape

**v1.x**: single active flavor via `kmpFlavors.activeFlavor.set("name")`.

**v2.x**: explicit flavor + build-type registration. The active variant is whichever flavor has `isDefault.set(true)`, optionally overridden via `-PkmpFlavor=<name>` at the CLI.

```kotlin
// v2.x
kmpFlavors {
    flavors {
        register("free") {
            isDefault.set(true)
            buildConfigField("Int", "MAX_ITEMS", "10")
        }
        register("paid") {
            buildConfigField("Int", "MAX_ITEMS", "1000")
        }
    }
    // Optional — adds a build-type axis on top of flavors
    buildTypes {
        register("debug") { isDefault.set(true) }
        register("release")
    }
}
```

### 2. Matrix mode (new in v2.0)

v2.0 introduced **matrix mode** — register `compile{Variant}Kotlin{Target}` tasks for every inactive variant on every non-Android target.

v1.x: only the active variant compiled. Cross-variant validation required CI matrix runs.

v2.x with `buildMatrix.set(true)`: every variant compiles in one Gradle invocation. See [`MATRIX_MODE.md`](MATRIX_MODE.md).

### 3. Auto-detection (new in v2.2)

v2.2 introduced `autoEnable` — when `true` (default), the plugin auto-flips `buildMatrix`, `publishMatrix`, `detektPerVariant`, etc. when their adjacent plugins or shape conditions are detected.

**Migration gotcha**: if your v1.x code reads `BuildKonfig` from `commonMain` (typical for consumers that wrote `import com.example.app.BuildKonfig`), the v2.2 auto-enable trips matrix mode on multi-target projects + moves `BuildKonfig` into per-flavor source sets. `commonMain` can no longer see it.

**Fix**: set `autoEnable.set(false)` to preserve v1.x active-variant-only semantics:

```kotlin
kmpFlavors {
    // PRESERVE V1.X SEMANTICS — keeps BuildKonfig in commonMain
    autoEnable.set(false)

    flavors {
        register("free") { isDefault.set(true) }
        register("paid")
    }
}
```

Then `BuildKonfig` stays in `commonMain` + your existing imports keep working.

### 4. Per-flavor source set conventions

v1.x: ad-hoc per-flavor source directories.

v2.x: standardised on `src/common{Flavor}/kotlin/`:

```
src/
├── commonMain/kotlin/          # all variants
├── commonFree/kotlin/          # free* variants only
└── commonPaid/kotlin/          # paid* variants only
```

`commonFree` ↔ `commonPaid` cross-references are forbidden — KGP enforces source-set isolation per compilation.

### 5. AGP bridge

v2.0+ forwards `kmpFlavors.flavors` + `kmpFlavors.buildTypes` into AGP's `productFlavors { … }` + `buildTypes { … }` blocks automatically (gated by `bridgeAgpProductFlavors` / `bridgeAgpBuildTypes`, both default `true`).

v1.x consumers using AGP product flavors manually can drop their manual `productFlavors { register("free") { … } }` blocks and let the v2.x bridge handle it.

---

## Step-by-step migration

### Step 1 — Bump the plugin pin

```kotlin
// build.gradle.kts (or libs.versions.toml)
plugins {
    id("io.github.mobilebytelabs.kmp-product-flavors") version "2.4.0-alpha.0"
}
```

### Step 2 — Convert `activeFlavor` to flavor registration

Replace:

```kotlin
// v1.x
kmpFlavors {
    activeFlavor.set("free")
}
```

With:

```kotlin
// v2.x
kmpFlavors {
    autoEnable.set(false)   // preserves v1.x semantics; remove later
    flavors {
        register("free") { isDefault.set(true) }
        register("paid")
        // …list all flavors you previously toggled `activeFlavor.set(...)` through
    }
}
```

### Step 3 — Test against the active variant

Verify the build is unchanged:

```bash
./gradlew clean compileKotlinDesktop
./gradlew :module:listFlavors        # confirm active variant resolves correctly
```

### Step 4 — Migrate to per-flavor source sets (if applicable)

If your v1.x code branched on `activeFlavor` via runtime checks (`if (BuildKonfig.IS_FREE)`), v2.x lets you split the code into per-flavor source sets — better isolation, no runtime branching.

Move free-only code to `src/commonFree/kotlin/…` + paid-only code to `src/commonPaid/kotlin/…`. The build resolves the right source set per active variant.

### Step 5 — Opt into matrix mode (optional)

Once the active-variant build works, opt into matrix mode to compile every variant in one invocation:

```kotlin
kmpFlavors {
    // Remove autoEnable.set(false) — let matrix mode auto-engage
    // (or keep it false if you don't want matrix mode yet)
    buildMatrix.set(true)
    // … rest unchanged
}
```

Verify:

```bash
./gradlew assembleAllVariants
./gradlew tasks --group="kmpFlavors variants"
```

### Step 6 — Adopt v2.x features incrementally

Each feature opt-in is independent. Adopt as needed:

- **Per-variant publishing**: `publishMatrix.set(true)` — see [`PUBLISHING.md`](PUBLISHING.md).
- **Per-variant Detekt**: `detektPerVariant.set(true)` — see [`MATRIX_MODE.md`](MATRIX_MODE.md).
- **Per-variant Compose hot-reload**: `composeHotReloadPerVariant.set(true)` — see [`COMPOSE_HOT_RELOAD.md`](COMPOSE_HOT_RELOAD.md).
- **Variant-conditional dep excludes**: `variants.matching { … }.configureEach { dependencies { exclude(...) } }` — see [`VARIANT_DEPENDENCY_EXCLUDES.md`](VARIANT_DEPENDENCY_EXCLUDES.md).

### Step 7 — Drop the `autoEnable.set(false)` workaround

When your codebase no longer reads `BuildKonfig` from `commonMain` (moved to per-flavor source sets), drop the `autoEnable.set(false)` line. The plugin's auto-detection kicks in + lights up matrix mode + adjacent-plugin helpers automatically.

---

## Compatibility windows

| Plugin version | v1.x DSL accepted | Warnings emitted |
|---|---|---|
| `v2.0.0` – `v2.4.x` | ✅ (compat shim) | None (silent shim) |
| `v2.5.0+` (post-2026-11-14) | ❌ | `KMPF-V21` ERROR + GradleException |

The compat shim is removed in `v2.5.0` or whichever release first ships **after 2026-11-14**. Pin to `v2.4.x` if you can't migrate before that date — but plan the migration before `v2.5.0` lands.

---

## Common pitfalls

### "Unresolved reference 'BuildKonfig'" in commonMain

**Cause**: `autoEnable=true` (default) + multi-target shape trips matrix mode → `BuildKonfig` moves into per-flavor source sets → `commonMain` can't see it.

**Fix**: `kmpFlavors.autoEnable.set(false)` (Step 2 above).

### "Conflicting declarations: const val IS_X" in generated BuildKonfig

**Cause**: a flavor name matches a custom `buildConfigField` name. Example: flavor `enterprise` + custom field `buildConfigField("Boolean", "IS_ENTERPRISE", "true")` — the codegen produces TWO `const val IS_ENTERPRISE` entries.

**Fix**: rename the custom field to avoid the collision: `buildConfigField("Boolean", "PREMIUM_TIER", "true")`. Tracked as KMPF-V23 in a future v2.4.x.

### AGP `productFlavors { … }` block duplicated by the bridge

**Cause**: v2.0+ AGP bridge auto-registers `productFlavors { register("free") { … } }` from `kmpFlavors.flavors { register("free") { … } }`. If you ALSO have a manual `productFlavors { … }` block in `android { }`, you get duplicate flavors.

**Fix**: either drop the manual AGP block (let the bridge handle it), OR disable the bridge: `kmpFlavors.bridgeAgpProductFlavors.set(false)`.

---

## See also

- [`REFERENCE.md`](REFERENCE.md) — complete v2.x DSL reference.
- [`MATRIX_MODE.md`](MATRIX_MODE.md) — matrix-mode deep dive.
- [`MIGRATION_v2.0_to_v2.4.md`](MIGRATION_v2.0_to_v2.4.md) — point-release diffs within the 2.x cycle.
- [`ERROR_CODES.md`](ERROR_CODES.md) — `KMPF-V21` (legacy DSL post-cutoff) + all other codes.
