# Migration: v2.4 → v2.5

**You do not need to migrate.**

All v2.4 DSL surfaces are fully supported in v2.5 and beyond. The version-floor
is unchanged (Gradle 8.0+ / KGP 2.0.21+ / AGP 8.0+ / JDK 17+ / CMP 1.7.0+ — see
[`COMPATIBILITY_MATRIX.md`](COMPATIBILITY_MATRIX.md)). This document is a
**cookbook** for consumers who want to adopt the optional `dimensions {}` block
or the new `buildKonfig {}` DSL features — not a required migration path.

---

## When to adopt the new DSL

The v2.5 `dimensions {}` block is **optional ergonomic sugar** over the v2.4 flat
`flavorDimensions { } + flavors { }` pair. Consider adopting if:

- Your config has ≥2 flavor dimensions today — the tree-shaped DSL is more
  readable at scale (especially with arbitrary-N dimensions, see
  [`MULTI_DIM_GUIDE.md`](MULTI_DIM_GUIDE.md))
- You want grouped flavor declarations co-located with their dimension
- You're starting a new project from scratch and want the cleanest pattern

**Keep the flat DSL if:**

- Your config has 1 dimension — the flat DSL is more concise
- Your config is stable + working — there's no value in churn
- You're following a sample / convention plugin that uses the flat style
- You have downstream tooling (linters, codegen) that parses your build.gradle.kts
  and expects the v2.4 shape

---

## Before / After — `dimensions {}` block

### Before (v2.4 flat DSL — fully supported in v2.5+)

```kotlin
kmpFlavors {
    flavorDimensions {
        register("tier")
        register("env")
    }
    flavors {
        register("free") {
            dimension.set("tier")
            buildConfigField("Boolean", "IS_PREMIUM", "false")
        }
        register("paid") {
            dimension.set("tier")
            buildConfigField("Boolean", "IS_PREMIUM", "true")
        }
        register("dev")  { dimension.set("env") }
        register("prod") { dimension.set("env") }
    }
}
```

### After (v2.5 `dimensions {}` sugar — purely additive)

```kotlin
kmpFlavors {
    dimensions {
        dimension("tier") {
            flavor("free") {
                buildConfigField("Boolean", "IS_PREMIUM", "false")
            }
            flavor("paid") {
                buildConfigField("Boolean", "IS_PREMIUM", "true")
            }
        }
        dimension("env") {
            flavor("dev")
            flavor("prod")
        }
    }
}
```

Both produce **identical** resolved variants (`freeDev`, `freePrd`, `paidDev`,
`paidPrd`), same downstream codegen, same AGP cross-product behavior, same
matrix mode compilations. The DSL is purely a configuration-time ergonomic
choice — runtime behavior is byte-identical.

---

## Pitfall — DO NOT MIX

Mixing `dimensions { }` with the legacy `flavorDimensions { } / flavors { }`
blocks in the same `kmpFlavors {}` block fires `KMPF-V24` ERROR at apply time:

```kotlin
// ❌ This fires KMPF-V24 — pick one style per project.
kmpFlavors {
    flavorDimensions { register("tier") }   // ← legacy flat DSL
    dimensions {                             // ← v2.5 sugar
        dimension("env") { flavor("dev") }
    }
}
```

The validator surfaces a clear error message pointing back to this document.

---

## Adopting `buildKonfig {}` (Phase 3 features)

The v2.5 `buildKonfig {}` top-level block adds four codegen capabilities. Each is
purely additive — existing flavor-level `buildConfigField()` declarations are
unaffected.

### Dimension enum — auto-generated sealed class

```kotlin
kmpFlavors {
    dimensions {
        dimension("tier") { flavor("free"); flavor("paid") }
    }
    buildKonfig {
        enum("tier")
    }
}
```

Generates a `sealed class Tier { Free; Paid }` in the BuildKonfig output plus a
typed `val tier: Tier` holding the active variant's flavor instance. Consumer
code pattern-matches:

```kotlin
when (BuildKonfig.tier) {
    BuildKonfig.Tier.Free -> ...
    BuildKonfig.Tier.Paid -> ...
}
```

### Custom-type fields — sealed-class + List<T>

```kotlin
kmpFlavors {
    buildKonfig {
        customField("config", "com.example.MyConfig", "com.example.MyConfig.Default")
        customField("scopes", "List<String>", "listOf(\"read\", \"write\")")
    }
}
```

Generates `val config: com.example.MyConfig = com.example.MyConfig.Default` +
`val scopes: List<String> = listOf("read", "write")`. The consumer's project must
define the referenced type — codegen only emits the value assignment.

Unsupported types (nested generics, open classes, Map) fire `KMPF-V27` at
configuration time.

### Per-target conditional codegen

```kotlin
kmpFlavors {
    buildKonfig {
        perTarget("iosMain") {
            field("BUNDLE_ID_SUFFIX", "String", "\".dev\"")
        }
    }
}
```

v2.5 emits these as a nested `object PerTarget.IosMain { ... }` inside the main
BuildKonfig object. Consumer code accesses via `BuildKonfig.PerTarget.IosMain.X`.
True per-file source-set isolation deferred to v2.6 — see
[`SECRETS_INTEGRATION.md`](SECRETS_INTEGRATION.md) § "perTarget semantics".

### Vault-integrated secrets

```kotlin
kmpFlavors {
    buildKonfig {
        secret("api-key")
    }
}
```

**v2.5 ships the DSL surface; real value flow ships in a v2.5.x patch** once the
framework-side `secrets-manifest.yaml` schema v2.1 + `secrets-pull.sh
--emit-gradle-flavor-map` mode land. Consumers can adopt the DSL today; the
plugin emits a placeholder value (`<unresolved:see-docs-SECRETS_INTEGRATION>`)
until the v2.5.x patch — **SV15 compliance** (no hardcoded secret values in
generated `.kt` files).

Full consumer contract: [`SECRETS_INTEGRATION.md`](SECRETS_INTEGRATION.md).

---

## New validator codes in v2.5

| Code | Severity | Trigger |
|---|---|---|
| `KMPF-V24` | ERROR | Both `dimensions {}` AND `flavorDimensions {}/flavors {}` used in same `kmpFlavors{}` |
| `KMPF-V25` | ERROR | Two dimensions share the same name OR AGP-side conflict on re-apply |
| `KMPF-V26` | ERROR/WARN | Vault-integrated secret resolution failed OR `secrets-manifest.yaml` schema < v2.1 |
| `KMPF-V27` | ERROR | `customField` declared with a type the codegen can't emit |
| `KMPF-V28` | ERROR | `perTarget(name)` references a target not in `kotlin.targets` |

Full catalog with fix messages: [`ERROR_CODES.md`](ERROR_CODES.md).

---

## Sample-side migration reference

Both DSL styles have canonical samples in this repository:

- **v2.4 flat DSL:** [`samples/matrix-mode/build.gradle.kts`](../samples/matrix-mode/build.gradle.kts) +
  [`samples/multi-target-multi-variant/build.gradle.kts`](../samples/multi-target-multi-variant/build.gradle.kts)
- **v2.5 `dimensions {}` sugar:** [`samples/multi-dim-3d/build.gradle.kts`](../samples/multi-dim-3d/build.gradle.kts)
- **v2.5 `buildKonfig {}` full DSL:** [`samples/buildkonfig-rich/build.gradle.kts`](../samples/buildkonfig-rich/build.gradle.kts)

Pick whichever style matches your project's existing patterns. The flat DSL is
not deprecated and will not be removed — it's part of the supported public
surface for the 2.x release line.

---

## What to skip when migrating

If your project uses any of the following v2.x DSL features, NO change is needed
in v2.5:

- `flavorDimensions { register(...) }` + `flavors { register(...) { dimension.set(...) } }` — fully supported
- `buildTypes { register(...) }` — unchanged
- `variantFilter { }` — unchanged behavior; works identically with both DSL styles
- `kmpFlavors.variants.matching { }.configureEach { }` — unchanged
- Matrix mode (`buildMatrix.set(true)`) — unchanged
- Per-variant Compose resources — unchanged behavior; new target families
  (watchOS, tvOS, etc.) automatically benefit (see [`SUPPORTED_TARGETS.md`](SUPPORTED_TARGETS.md))
- AGP bridge (`bridgeAgpProductFlavors.set(true|false)`) — 1-dim configs traverse
  a byte-identical fast path; ≥2-dim configs use the new cross-product path
  (transparent to consumers)
- All existing validator codes V01-V23 — unchanged behavior

---

## Cross-references

- **`COMPATIBILITY_MATRIX.md`** — v2.5 version floor (UNCHANGED FROM v2.4)
- **`MULTI_DIM_GUIDE.md`** — variant-filter discipline for arbitrary-N dimensions
- **`SECRETS_INTEGRATION.md`** — `buildKonfig { secret() }` consumer contract
- **`SUPPORTED_TARGETS.md`** — v2.5 9-target sample/CI coverage matrix
- **`ERROR_CODES.md`** — full validator code catalog including V24-V28
- **`CHANGELOG.md`** § `[2.5.0]` — release notes
- **Older migrations:** [`MIGRATION_v1_to_v2.md`](MIGRATION_v1_to_v2.md),
  [`MIGRATION_v2.0_to_v2.4.md`](MIGRATION_v2.0_to_v2.4.md)
