# Multi-Dimension Flavor Guide

> **Since v2.5** — variant-filter discipline + combinatorial-cost guidance for
> arbitrary-N flavor dimensions.

The v2.5 `dimensions { }` DSL block supports an arbitrary number of dimensions —
2D, 3D, 5D, anything KGP's source-set hierarchy can handle. This guide covers
how to keep the resulting variant count tractable.

---

## The combinatorial blowup

Each dimension multiplies the variant count. With M targets, the compilation
matrix is `M × ∏(dimension sizes)`.

| Dimensions | Variants | Compilations × 12 targets |
|---|:---:|:---:|
| tier (2) | 2 | 24 |
| tier (2) × env (2) | 4 | 48 |
| tier (2) × env (3) | 6 | 72 |
| tier (2) × env (3) × form (2) | 12 | 144 |
| tier (2) × env (3) × form (2) × locale (4) | 48 | 576 |
| tier (3) × env (3) × form (3) × locale (5) | 135 | 1620 |

Past ~12 variants, CI minutes and IDE responsiveness suffer noticeably. **Use
`variantFilter {}` aggressively for N ≥ 3 dimensions.**

---

## variantFilter recipes

The v2.4 `variantFilter { }` DSL operates identically with v2.5 `dimensions {}` —
it sees the resolved variant before AGP cross-products. The filter runs for each
candidate variant; calling `exclude()` removes it from the buildable matrix.

### Prune by tier-environment exclusion

```kotlin
kmpFlavors {
    variantFilter {
        // Free tier never ships in staging — cost discipline.
        if (flavorNames.contains("free") && flavorNames.contains("staging")) {
            exclude()
        }
    }
}
```

For a `tier(2) × env(3) × form(2)` matrix (12 candidate variants), this prunes
2 variants → 10 buildable.

### Prune by build-type / flavor combination

```kotlin
kmpFlavors {
    variantFilter {
        if (buildType == "release" && flavorNames.contains("dev")) {
            exclude()
        }
    }
}
```

Don't ship a `releaseDev` variant — dev flavor implies non-production builds.

### Prune by inactive variant (CI cost reduction)

```kotlin
kmpFlavors {
    variantFilter {
        // Only build the current PR's variant on PR runs; full matrix on main.
        if (System.getenv("CI") == "true" &&
            System.getenv("GITHUB_EVENT_NAME") == "pull_request" &&
            variantName != System.getenv("CI_TARGET_VARIANT")) {
            exclude()
        }
    }
}
```

This shrinks PR-time CI from `12 × N` compilations to `1 × N` — main-branch
builds still exercise the full matrix.

### Multi-clause variant filters

```kotlin
kmpFlavors {
    variantFilter {
        // Compose: free + staging never ships; free + tablet never ships;
        // any × release + dev never ships.
        when {
            flavorNames.containsAll(listOf("free", "staging")) -> exclude()
            flavorNames.containsAll(listOf("free", "tablet")) -> exclude()
            buildType == "release" && flavorNames.contains("dev") -> exclude()
        }
    }
}
```

variantFilter runs per-variant; each exclude is local to that candidate. The
final matrix is the candidate cross-product minus all excluded variants.

---

## Canonical example

See [`samples/multi-dim-3d/`](../samples/multi-dim-3d/) for a 3-dimension matrix
(`tier × env × form-factor`) with the variant-filter recipe applied. The sample
declares:

- 2 × 2 × 2 = **8 candidate variants** (cross-product)
- `variantFilter` excludes `(free × prd × *)` = **2 variants pruned**
- **6 final buildable variants** — 25% reduction with no loss of coverage for
  the business rules modeled

Run `./gradlew :samples:multi-dim-3d:listFlavors` to see the resolved matrix.

---

## When to STOP adding dimensions

Beyond 3 dimensions, you're likely modeling a configuration matrix that should
live in code (BuildKonfig fields) rather than the variant matrix. Ask:

> *Do these axes produce structurally-different artifacts, or just
> structurally-different runtime values?*

- **Structurally-different artifacts** (different APK / IPA / JS bundle, different
  source code paths via `expect`/`actual`) → use a flavor dimension.

- **Structurally-different runtime values** (feature flags, A/B test buckets,
  env-var overrides) → use BuildKonfig `secret()` / `customField()` / `enum()`.

### Examples

| Axis | Use a dimension? | Why |
|---|:--:|---|
| `free` vs `paid` tier (different APIs, different DI scopes) | ✓ | Source-code paths diverge |
| `dev` vs `prod` env (different endpoints) | ✓ | Endpoint URL differs structurally |
| `en` vs `ja` locale (different translations) | ✗ | Use Android resource qualifiers / CMP resources |
| `experiment_A` vs `experiment_B` | ✗ | Use a feature flag (BuildKonfig field + runtime fetch) |
| `tablet` vs `phone` form-factor | ⚠ | Depends — UI layouts can use a flag; if you ship two APKs, use a dimension |

---

## AGP cross-product semantics

When v2.5 detects ≥2 dimensions, the AGP bridge's `propagateFlavorsCrossProduct`
path emits `flavorDimensions = ["tier", "env"]` + one product flavor per
dimension member (NOT one per resolved variant — AGP cross-products natively
from the flavorDimensions list).

For a `tier(free, paid) × env(dev, prod)` config:

- KMP-side resolved variants: `freeDev, freePrd, paidDev, paidPrd` (4 variants
  the matrix-mode compilation registrar walks)
- AGP-side flavorDimensions: `["tier", "env"]`
- AGP-side productFlavors: `free, paid, dev, prod` (each with `dimension =` set)
- AGP-derived variants: `freeDev, freePrd, paidDev, paidPrd` (computed by AGP
  from cross-product)

Both sides agree on the resolved variant set. v2.5 Phase 1 added KMPF-V25 to
detect re-apply scenarios where AGP-side flavors conflict with KMP-side
declarations (cross-vault hand-edit case).

---

## CI cost guidance

v2.5's `sample-target-coverage.yml` workflow exercises 14 non-Android targets ×
9 variants = 126 compilations. CI minutes growth budget is `≤ 2× v2.4 baseline`
(documented in `04-discipline.md` AC 10).

For consumer projects, the CI cost rule of thumb:

| Compilations | Recommended CI strategy |
|---|---|
| ≤ 50 | Single matrix job — runs in <15 min on standard runner |
| 50–150 | Shard by target family (Linux runner / macOS runner / Windows runner) |
| 150–500 | Add variantFilter for PR-time pruning; full matrix on main only |
| > 500 | Reconsider dimension structure — likely should be runtime config |

---

## Cross-references

- **Canonical 3-dim sample:** [`samples/multi-dim-3d/build.gradle.kts`](../samples/multi-dim-3d/build.gradle.kts)
- **DSL implementation:** [`build-logic/flavor-plugin/src/main/kotlin/com/mobilebytelabs/kmpflavors/DimensionsDsl.kt`](../build-logic/flavor-plugin/src/main/kotlin/com/mobilebytelabs/kmpflavors/DimensionsDsl.kt)
- **AGP cross-product bridge:** [`build-logic/flavor-plugin/src/main/kotlin/com/mobilebytelabs/kmpflavors/internal/AgpBridge.kt`](../build-logic/flavor-plugin/src/main/kotlin/com/mobilebytelabs/kmpflavors/internal/AgpBridge.kt)
  (`propagateFlavorsCrossProduct` for ≥2-dim configs)
- **Validator codes for multi-dim issues:** `KMPF-V02`, `KMPF-V03`, `KMPF-V04`,
  `KMPF-V24`, `KMPF-V25` — see [`ERROR_CODES.md`](ERROR_CODES.md)

  (opens with "You do not need to migrate.")
- **BuildKonfig DSL** (when runtime values are the right tool):
  [`SECRETS_INTEGRATION.md`](SECRETS_INTEGRATION.md)
