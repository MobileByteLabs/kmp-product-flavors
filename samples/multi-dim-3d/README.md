# samples/multi-dim-3d — v2.5 Canonical 3-Dimension Sample

> Showcases the new v2.5 ergonomic `dimensions {}` DSL block + the `variantFilter` discipline
> required for arbitrary-N dimensions.

## What this sample exercises

| Capability | How |
|---|---|
| v2.5 `dimensions {}` ergonomic DSL block | `build.gradle.kts` declares 3 dimensions (tier × env × form) using the new tree-shaped sugar |
| Arbitrary-N dimensions (D5 lock) | 3-dimension cross-product = 8 candidate variants |
| `variantFilter` pruning discipline | Excludes `free × prd` combinations → 6 buildable variants |
| AGP bridge cross-product path (≥2-dim) | When this sample is wired into an Android app, exercises `AgpBridge.propagateFlavorsCrossProduct` |
| Per-flavor BuildKonfig from multiple dimensions | `IS_PREMIUM` from tier, `API_BASE_URL` from env, `IS_TABLET` from form |
| Default flavor per dimension | Each dimension has one `isDefault.set(true)` flavor (free/dev/phone) — resolves to the `freeDevPhone` variant when `-PkmpFlavor` is unset |

## Running

```bash
# Build all 6 buildable variants in one shot (matrix mode):
./gradlew :samples:multi-dim-3d:assembleAllVariants

# List the resolved variant matrix (verify variantFilter pruning):
./gradlew :samples:multi-dim-3d:listFlavors

# Switch active variant:
./gradlew :samples:multi-dim-3d:assemble -PkmpFlavor=paidPrdTablet
```

## Expected variant matrix

```
Candidate variants (cross-product 2 × 2 × 2 = 8):
  freeDevPhone, freeDevTablet, freePrdPhone, freePrdTablet,
  paidDevPhone, paidDevTablet, paidPrdPhone, paidPrdTablet

variantFilter excludes (free × prd):
  freePrdPhone, freePrdTablet  ← removed

Final buildable variants (6):
  freeDevPhone, freeDevTablet,
  paidDevPhone, paidDevTablet,
  paidPrdPhone, paidPrdTablet
```

## See also

- `docs/MULTI_DIM_GUIDE.md` — variant-filter discipline + combinatorial-cost guidance for arbitrary-N dimensions (authored in Phase 4 of the v2.5 epic)
- `samples/matrix-mode/` — 2-dimension (flavors × buildTypes) reference using the v2.4 flat DSL
- `samples/multi-target-multi-variant/` — target rotation reference (12 KMP targets × 9 variants = 108 compilations)
