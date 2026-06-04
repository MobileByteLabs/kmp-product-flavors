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
| [`VARIANT_FILTERS.md`](VARIANT_FILTERS.md) | `variantFilter { … }` DSL — AGP-shaped filtering. |
| [`VARIANT_DEPENDENCY_EXCLUDES.md`](VARIANT_DEPENDENCY_EXCLUDES.md) | Variant-conditional `dependencies { exclude(...) }`. |
| [`COMPOSE_HOT_RELOAD.md`](COMPOSE_HOT_RELOAD.md) | Per-variant Compose hot-reload (Option A shipped; Option B-workaround via `switchVariantAndReload`). |
| [`IOS_DISTRIBUTION.md`](IOS_DISTRIBUTION.md) | iOS framework distribution patterns. |
| [`KMP_PROJECT_TEMPLATE_INTEGRATION.md`](KMP_PROJECT_TEMPLATE_INTEGRATION.md) | Integration guide for `openMF/kmp-project-template` adopters. |

## Operational guides

| Doc | What it covers |
|---|---|
| [`RELEASE.md`](RELEASE.md) | End-to-end release cascade. SemVer-pre-release-aware bumping, pre-release flag auto-detection, cron auto-merge safety-net. |
| [`ROLLBACK.md`](ROLLBACK.md) | Plugin version rollback procedure. |
| [`ERROR_CODES.md`](ERROR_CODES.md) | Complete `KMPF-V<NN>` catalogue with severity + fix steps. |
| [`v2.4-BETA-TESTING.md`](v2.4-BETA-TESTING.md) | One-page guide for piloting `2.4.0-beta` on a real consumer project. Required path to `v2.4.0` GA. |

## Migration

| Doc | What it covers |
|---|---|
| [`MIGRATION_v1_to_v2.md`](MIGRATION_v1_to_v2.md) | v1.x → v2.x migration. **Critical pre-2026-11-14**: v1.x compat shim removal expires after that date per RFC §3 Q15. |
| [`MIGRATION_v2.0_to_v2.4.md`](MIGRATION_v2.0_to_v2.4.md) | Point-release diffs across the 2.x cycle. |
| [`MIGRATION_v2.4_TO_v2.5.md`](MIGRATION_v2.4_TO_v2.5.md) | Multi-dim DSL + 9-target coverage + BuildKonfig expansion. |
| [`MIGRATION_v2.5_TO_v2.6.md`](MIGRATION_v2.5_TO_v2.6.md) | KMP↔AGP variantFilter parity + stability tier reshuffle. |
| [`MIGRATION_v2.6_TO_v2.7.md`](MIGRATION_v2.6_TO_v2.7.md) | AGP 9.2.1 support + 100% coverage. Opens with "You do not need to migrate." |
| [`MIGRATION_v2.7_TO_v2.8.md`](MIGRATION_v2.7_TO_v2.8.md) | **AGP 9.2.1+ floor cut.** AGP 8.x consumers MUST migrate. Step-by-step + validator codes + rollback. |

## Design context

| Doc | What it covers |
|---|---|
| [`RFC-v2.0-per-variant-compilation.md`](RFC-v2.0-per-variant-compilation.md) | v2.0 RFC — per-variant Kotlin compilation matrix design. |
| [`AGP_SUPPORT.md`](AGP_SUPPORT.md) | AGP 9.2.1+ floor contract. Rationale, version table, retired `agp-matrix-compat.yml` workflow. |
| [`LEARNINGS.md`](LEARNINGS.md) | Execution-discovered locked contracts L1–L5 — AGP propagation timing / reflective setter contract / RuntimeApi codegen-host election / KGP single-axis source set / reflection-safe Android template. AGP 9 breaking-change index. |
| [`AGP_9_MIGRATION_NOTES.md`](AGP_9_MIGRATION_NOTES.md) | Consumer-side AGP 9 cookbook — `CommonExtension` type-param drop, dataBinding deprecation, `com.android.kotlin.multiplatform.library` adoption, dependencyGuard workaround. |

## Quick lookup

**"How do I…?"**

- **…publish a snapshot of my work-in-progress branch?** → [`PUBLISHING.md` "Snapshot channel"](PUBLISHING.md#snapshot-channel-v23)
- **…switch the active variant without restarting Gradle?** → [`COMPOSE_HOT_RELOAD.md`](COMPOSE_HOT_RELOAD.md) + `./gradlew switchVariantAndReload --to=<v>`
- **…strip a dep from one variant only?** → [`VARIANT_DEPENDENCY_EXCLUDES.md`](VARIANT_DEPENDENCY_EXCLUDES.md)
- **…understand a `KMPF-V…` warning?** → [`ERROR_CODES.md`](ERROR_CODES.md)
- **…cut a v2.x.0 release?** → [`RELEASE.md`](RELEASE.md)
- **…migrate from v1.x?** → [`MIGRATION_v1_to_v2.md`](MIGRATION_v1_to_v2.md)
