# Documentation index — kmp-product-flavors

Curated entry point. Find the right doc per topic instead of crawling the full `docs/` tree.

## Start here

| Doc | What it covers |
|---|---|
| [`QUICKSTART.md`](QUICKSTART.md) | 5-minute onboarding. Plugin declaration → first variant compilation. |
| [`REFERENCE.md`](REFERENCE.md) | Complete `kmpFlavors { }` DSL reference. Every property + DSL method with stability bucket + default + example. |

## Deep-dive guides

| Doc | What it covers |
|---|---|
| [`MATRIX_MODE.md`](MATRIX_MODE.md) | Matrix-mode deep dive — `buildMatrix.set(true)`, per-variant compilations, the `variants` public API. |
| [`PUBLISHING.md`](PUBLISHING.md) | Per-variant publishing — Maven Central, classifier-tagged artefacts, XCFramework, SPM, npm, Sonatype Snapshots channel. |
| [`PRODUCT_FLAVORS.md`](PRODUCT_FLAVORS.md) | Flavor + flavor-dimension semantics. |
| [`BUILD_VARIANTS.md`](BUILD_VARIANTS.md) | Build-type axis + variant resolution. |
| [`MULTI_DIM_GUIDE.md`](MULTI_DIM_GUIDE.md) | Multi-dimensional flavor authoring + 2^n variant matrix. |
| [`VARIANT_FILTERS.md`](VARIANT_FILTERS.md) | `variantFilter { … }` DSL — AGP-shaped filtering. |
| [`VARIANT_DEPENDENCY_EXCLUDES.md`](VARIANT_DEPENDENCY_EXCLUDES.md) | Variant-conditional `dependencies { exclude(...) }`. |
| [`COMPOSE_HOT_RELOAD.md`](COMPOSE_HOT_RELOAD.md) | Per-variant Compose hot-reload (Option A shipped; Option B-workaround via `switchVariantAndReload`). |
| [`CONDITIONAL_TARGETS.md`](CONDITIONAL_TARGETS.md) | Conditional KMP target sets per variant via `variantFilter { excludeTargets() }`. |
| [`IOS_DISTRIBUTION.md`](IOS_DISTRIBUTION.md) | iOS framework distribution patterns. |
| [`SUPPORTED_TARGETS.md`](SUPPORTED_TARGETS.md) | Per-target coverage matrix. |
| [`SOURCE_SET_DISCIPLINE.md`](SOURCE_SET_DISCIPLINE.md) | Single-axis `{F}Main` source set model + KGP rationale. |
| [`KMP_AGP_PARITY.md`](KMP_AGP_PARITY.md) | KMP↔AGP variantFilter parity contract. |
| [`KMP_PROJECT_TEMPLATE_INTEGRATION.md`](KMP_PROJECT_TEMPLATE_INTEGRATION.md) | Integration guide for `openMF/kmp-project-template` adopters. |

## Integration guides

| Doc | What it covers |
|---|---|
| [`ANALYTICS_INTEGRATION.md`](ANALYTICS_INTEGRATION.md) | `analytics { customTag() }` per-variant tags. |
| [`DI_INTEGRATION.md`](DI_INTEGRATION.md) | `di { koin { variantModule() } }` per-variant Koin modules. |
| [`NETWORK_CONFIG.md`](NETWORK_CONFIG.md) | `buildKonfig { network { baseUrl() } }` per-variant Ktor base URLs. |
| [`SECRETS_INTEGRATION.md`](SECRETS_INTEGRATION.md) | Per-variant secrets via `buildKonfig { secret() }`. |

## Operational guides

| Doc | What it covers |
|---|---|
| [`RELEASE.md`](RELEASE.md) | End-to-end release cascade. SemVer-pre-release-aware bumping, pre-release flag auto-detection, cron auto-merge safety-net. |
| [`ROLLBACK.md`](ROLLBACK.md) | Plugin version rollback procedure. |
| [`ERROR_CODES.md`](ERROR_CODES.md) | Complete `KMPF-V<NN>` catalogue with severity + fix steps. |
| [`COVERAGE_GUIDE.md`](COVERAGE_GUIDE.md) | Kover line-coverage gate operating manual. |
| [`COVERAGE_DEEP_DIVE.md`](COVERAGE_DEEP_DIVE.md) | Tier E sealed exclusion list rationale (contributor playbook). |

## Design context

| Doc | What it covers |
|---|---|
| [`RFC-v2.0-per-variant-compilation.md`](RFC-v2.0-per-variant-compilation.md) | v2.0 RFC — per-variant Kotlin compilation matrix design. |
| [`AGP_SUPPORT.md`](AGP_SUPPORT.md) | AGP 9.2.1+ floor contract. Rationale + version table. |
| [`LEARNINGS.md`](LEARNINGS.md) | Execution-discovered locked contracts L1–L6 — AGP propagation timing / reflective setter contract / RuntimeApi codegen-host election / KGP single-axis source set / reflection-safe Android template / Phase 6 ↔ Phase 7 wiring ordering. |

## Quick lookup

**"How do I…?"**

- **…publish a snapshot of my work-in-progress branch?** → [`PUBLISHING.md` "Snapshot channel"](PUBLISHING.md#snapshot-channel-v23)
- **…switch the active variant without restarting Gradle?** → [`COMPOSE_HOT_RELOAD.md`](COMPOSE_HOT_RELOAD.md) + `./gradlew switchVariantAndReload --to=<v>`
- **…strip a dep from one variant only?** → [`VARIANT_DEPENDENCY_EXCLUDES.md`](VARIANT_DEPENDENCY_EXCLUDES.md)
- **…understand a `KMPF-V…` warning?** → [`ERROR_CODES.md`](ERROR_CODES.md)
- **…cut a v2.x.0 release?** → [`RELEASE.md`](RELEASE.md)
- **…check why coverage dropped?** → [`COVERAGE_DEEP_DIVE.md`](COVERAGE_DEEP_DIVE.md)
