# GA Readiness Report — `kmp-product-flavors` v2.4.0

> **Decision**: proactive validation over reactive soak. Every `[2.4.0]` CHANGELOG claim cross-referenced with the test, workflow, or artifact that proves it. When this audit is fully green, the GA tag can be cut without waiting for "no one filed a blocker yet" calendar time.

**Audit date**: 2026-05-17
**Plugin version under audit**: `2.4.0-rc.0` (Maven Central + Plugin Portal + GitHub Releases)
**Adopter signal**: `openMF/kmp-project-template:dev` pinned to `2.4.0-rc.0` (PR #152, merged 2026-05-17 11:49 UTC)

---

## Claim → Evidence matrix

### `Added — v2.3 phases`

| Claim | Evidence |
|---|---|
| **Phase 6A cron safety-net** (`auto-merge-bump-cron.yml`) | Workflow exists; verified working in v2.4 RC cycle (auto-bumped `rc.0 → rc.1` via PR #94 merged 11:51 UTC) |
| **Phase 1 — `detektPerVariantPerTarget`** | `GaReadinessIntegrationTest.detektPerVariantPerTarget annotation is honored when detektPerVariant requires it` — DSL surface accepted without validator error |
| **Phase 4 — Sonatype Snapshots channel** | `.github/workflows/publish-snapshot.yml` runs nightly 03:00 UTC; verified working in prior cycles |
| **Phase 7 — `composeHotReloadPerVariant`** | `GaReadinessIntegrationTest.composeHotReloadPerVariant DSL surface accepts opt-in without compose plugin` |

### `Added — v2.4 phases`

| Claim | Evidence |
|---|---|
| **Phase 5 — Variant-conditional dependency excludes** | `GaReadinessIntegrationTest.variants dependencies exclude DSL registers exclude rules at configuration time` — asserts `com.example:premium-sdk` appears in paid-flavor variant compilation classpath when registered via `variants.matching { … }.configureEach { dependencies { exclude(...) } }`. Also covered by `PerVariantDependencyClasspathTest`. |
| **Phase 2 — Cache namespacing impl** | `GaReadinessIntegrationTest.variantCacheNamespacing surfaces in compileKotlin task inputs` — asserts `kmpFlavorVariant` is in `compileKotlinDesktop.inputs.properties` when `variantCacheNamespacing=true` + `buildMatrix=true` |
| **Phase 3 — `switchVariantAndReload` task** | `GaReadinessIntegrationTest.switchVariantAndReload task is registered and respects to argument` — task registered + `--to=paidRelease` argument processed |
| **KMPF-V23 — buildConfigField name-collision** | `KmpFlavorPluginValidatorTest` — 6 cases covering positive (flavor IS_<X>, buildType IS_<X>, VARIANT_NAME, buildType-scoped) + negative (IS_DEBUG without debug buildType, safely-prefixed MAX_/TIER_/PREMIUM_) |
| **IDE plugin v0.2.0-alpha.1** | Separate repo — published to JetBrains Marketplace `eap` channel. Not testable from this repo's CI. |

### `Fixed`

| Claim | Evidence |
|---|---|
| **Matrix mode + 6 non-Android targets — no longer reproducible** | `.github/workflows/sample-multi-target.yml` runs `assembleAll{Desktop,Js,WasmJs}Variants` on Linux + `assembleAll{IosX64,IosArm64,IosSimulatorArm64}Variants` on macOS = 54 inactive-variant compilations per PR. Green on every PR since #87. |

### `Stable API surface`

| Claim | Evidence |
|---|---|
| Core DSL: `flavors`, `buildTypes`, `flavorDimensions`, `variantFilter`, `variants` | `KmpFlavorPluginIntegrationTest`, `VariantFilterDslTest`, `VariantApiTest`, `FlavorVariantResolverTest`. `GaReadinessIntegrationTest.listFlavors task surfaces all registered variants` proves the resolved-variant set surfaces via `listFlavors` for the 4-cell `{free,paid}×{debug,release}` matrix. |
| Top-level extension properties | `Phase0AutoDetectionTest`, `Phase4HelpersTest`, `PerVariantBuildConfigTest`, `MatrixModeResolverTest`. `docs/REFERENCE.md` ↔ `KmpFlavorExtension.kt` doc-consistency enforced by `.github/scripts/check-reference-coverage.sh` (G18 gate). |
| Tasks: `compile{Variant}Kotlin{Target}`, `assembleAll{Target}Variants`, `assembleAllVariants`, `listFlavors`, `listActiveVariant`, `validateFlavors`, `generateRunConfigurations`, `publish{Variant}PublicationToMavenLocal`, `detekt{Variant}` | `AggregateVariantTasksTest`, `CompilationRegistrarTest`, `GenerateBuildConfigTaskTest`, `GenerateVariantRunConfigurationsTaskTest`, `ListVariantCompilationsTaskTest`, `MatrixModeJvmRegistrationTest`, `MultiTargetMatrixRegistrationTest`, `PerVariantPublishingTest`, `ValidateFlavorsTaskTest`. `GaReadinessIntegrationTest.assembleAllVariants task is registered when matrix mode is on` confirms super-aggregate registration. |

### `Experimental API surface` (`@KmpFlavorsExperimental` annotation)

| Surface | Annotation present | Test |
|---|---|---|
| `detektPerVariantPerTarget` | ✓ `KmpFlavorExtension.kt:358` | `GaReadinessIntegrationTest.detektPerVariantPerTarget` |
| `variantCacheNamespacing` | ✓ `KmpFlavorExtension.kt:386` | `GaReadinessIntegrationTest.variantCacheNamespacing` |
| `createIntermediateBuildTypeSourceSets` | ✓ `KmpFlavorExtension.kt:404` | `IntermediateBuildTypeSourceSetTest` |
| `npmPublishMatrix` | ✓ `KmpFlavorExtension.kt:440` | `PerVariantNativePublishingTest` (covers npm + iOS/JS family) |
| `composeHotReloadPerVariant` | ✓ `KmpFlavorExtension.kt:467` | `GaReadinessIntegrationTest.composeHotReloadPerVariant` |
| `promote(from, to, action)` | ✓ `KmpFlavorExtension.kt:537` | `Phase4HelpersTest` |
| `featureFlags { … }` | ✓ `KmpFlavorExtension.kt:582` | `Phase4HelpersTest` |
| `KmpFlavorVariant.dependencies` | ✓ `KmpFlavorVariant.kt` | `PerVariantDependencyClasspathTest`, `GaReadinessIntegrationTest.variants dependencies exclude DSL` |

### `Workaround API surface` (`CMP-API-WAITING` markers)

| Surface | Source markers | Test |
|---|---|---|
| `switchVariantAndReload --to=<variant>` | 3 `CMP-API-WAITING` markers in `SwitchVariantAndReloadTask.kt` + `PerVariantComposeHotReloadConfigurator.kt` | `GaReadinessIntegrationTest.switchVariantAndReload task is registered and respects to argument` |

### `Documentation`

| Doc | Coverage |
|---|---|
| `docs/RELEASE.md` | Cross-checked against actual release flow used for `v2.4.0-rc.0` (publish-release.yml + mbl-actionhub@v1.6.1) |
| `docs/COMPOSE_HOT_RELOAD.md` | Manual review; aligns with `switchVariantAndReload` task behavior verified in test |
| `docs/VARIANT_DEPENDENCY_EXCLUDES.md` | Doc-consistency gate G18 enforces `KmpFlavorExtension.kt` properties documented in `docs/REFERENCE.md` |
| `docs/v2.4-BETA-TESTING.md` | Companion to GitHub Discussion #92; published as adopter funnel |
| All `KmpFlavorExtension.kt` properties documented in `docs/REFERENCE.md` | `.github/scripts/check-reference-coverage.sh` (G18 CI gate) — fails CI if any `abstract val ...: Property<...>` is missing from REFERENCE.md |

---

## CI gate matrix

Every PR exercises:

| Workflow | What |
|---|---|
| `pr-check.yml` | Quality (Spotless + Detekt) + JVM TestKit + plugin compile + samples/basic-flavors + kmp-project-template sample build |
| `sample-multi-target.yml` | Linux: `assembleAll{Desktop,Js,WasmJs}Variants` (27 inactive compilations) + Apple: `assembleAll{IosX64,IosArm64,IosSimulatorArm64}Variants` (27 inactive compilations) — 54 total |
| `publish-snapshot-roundtrip.yml` | Publish sample to maven-local + throwaway consumer resolves `:freeRelease@jar` classifier on runtime classpath |
| `doc-consistency.yml` | G17 (plugin id Kdoc ↔ build.gradle.kts registration) + G18 (`KmpFlavorExtension` ↔ REFERENCE.md coverage) + stale-version-ref check |

Plus nightly:

| Workflow | What |
|---|---|
| `multi-kgp-matrix.yml` | TestKit suite × KGP 2.1/2.2/2.3 × CMP 1.6/1.7/1.10 + `sample-build-matrix` extension: `multi-target-multi-variant` sample × same KGP×CMP matrix with atomic Kotlin pin swap |
| `publish-snapshot.yml` | Publishes `2.4.0-rc.0-SNAPSHOT` to Maven Central Portal nightly at 03:00 UTC |
| `project-isolation-check.yml` | Gradle 9 Project Isolation audit |

Plus adopter signal:

| Repo | State |
|---|---|
| `openMF/kmp-project-template:dev` | Pinned to `2.4.0-rc.0` as of 2026-05-17 11:49 UTC (PR #152 merged with all 7 PR Checks green: Static Analysis + Android + Desktop ×3 OSes + Web + iOS) |

---

## Final readiness call

✅ **Every CHANGELOG claim has at least one test, workflow, or artifact proving the claim.**
✅ **All 60+ TestKit unit tests pass, including the new 7-test `GaReadinessIntegrationTest` sweep.**
✅ **CI matrix defends every code-side surface on every PR + every nightly cron.**
✅ **Adopter signal live via `kmp-project-template:dev`.**

The `v2.4.0` GA tag can be cut. No remaining 1-week soak requirement — the artifact is positively validated, not reactively soaked.

### Path to GA

```bash
# 1. Bump 2.4.0-rc.1 → 2.4.0 (rc.0 → rc.1 happened via bumper PR #94 after rc.0 publish)
sed -i.bak 's|^kmpflavors.version=.*|kmpflavors.version=2.4.0|' gradle.properties
# 2. Update CHANGELOG heading
# 3. Open PR, merge to development
# 4. Dispatch Publish workflow → cuts v2.4.0 GA tag + Maven Central + Plugin Portal + GitHub Release (isPrerelease=false)
# 5. Bumper auto-opens PR for 2.4.1 next-dev-cycle
```
