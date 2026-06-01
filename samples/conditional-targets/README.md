# samples/conditional-targets

> **v2.6 Phase 4** — exercises `variantFilter { excludeTargets(...) }` for
> CI-cost discipline. The `free` tier skips watchOS / tvOS; the `paid` tier
> compiles for all 6 targets.

## Matrix shape

| Variant       | Targets compiled                                       | # comps |
|---------------|--------------------------------------------------------|:------:|
| `freeDev`     | desktop, iosArm64, iosSimulatorArm64                   |   3    |
| `freeProd`    | desktop, iosArm64, iosSimulatorArm64                   |   3    |
| `paidDev`     | desktop, iosArm64, iosSimulatorArm64, watchos*, tvos*  |   7    |
| `paidProd`    | desktop, iosArm64, iosSimulatorArm64, watchos*, tvos*  |   7    |
| **Total**     |                                                        | **20** |

Without `excludeTargets` discipline: 4 variants × 7 targets = **28** compilations.
Savings: ~28% of CI minutes for the `free` tier paths.

## DSL

```kotlin
kmpFlavors {
    variantFilter {
        if (flavorNames.contains("free")) {
            excludeTargets("watchosArm64", "watchosX64", "tvosArm64", "tvosX64")
        }
    }
}
```

## Try it

```bash
# Plugin tasks — list variants, see registered compilations
./gradlew :samples:conditional-targets:listFlavors
./gradlew :samples:conditional-targets:tasks --all --group="kmpFlavors variants"

# Verify excluded compilations do NOT exist
./gradlew :samples:conditional-targets:tasks --all 2>&1 | grep -E "compileFreeDevKotlinWatchos" && echo "BUG" || echo "OK — excluded correctly"
```

## Dead source sets

Source sets like `commonFree`, `watchosArm64Free`, `tvosArm64Free` still get
created on disk for the excluded variant × target combinations. They're
unused but harmless. See `docs/SOURCE_SET_DISCIPLINE.md` for the rationale.

## See also

- `docs/CONDITIONAL_TARGETS.md` — full pattern + dead-source-set rationale
- `docs/NETWORK_CONFIG.md` — companion v2.6 Phase 4 network constants codegen
- `samples/multi-dim-3d/` — 3-dimension cross-product sample (no target exclusion)
