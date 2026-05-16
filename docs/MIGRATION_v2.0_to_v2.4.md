# Migration: v2.0 → v2.4 (point-release diffs)

> Each v2.x bump is drop-in for consumers using only the v1.x-stable DSL surface. New features are opt-in; defaults preserve prior-version semantics. The exception is `v2.2`'s `autoEnable=true` default — see "v2.1 → v2.2" below.

## v2.0 → v2.1

**Released**: 2026-05-14.

### Added (opt-in)

- `detektPerVariant: Property<Boolean>` — per-variant Detekt scope. Default `false`.
- `excludeGeneratedFromFormatters: Property<Boolean>` — auto-exclude `build/generated/kmpFlavors/**` from Spotless + Detekt. Default `false`.
- `dependencyGuardPerVariant: Property<Boolean>` — per-(variant × target) `dependencyGuard.configuration(...)`. Default `false`.
- `generateVariantRunConfigurations` task — emit per-(variant × target) `.run.xml` files.

### Breaking changes

None.

---

## v2.1 → v2.2

**Released**: 2026-05-15 (`v2.2.0-alpha.0` / `alpha.1`; GA pending).

### Added (auto-enabled when conditions met)

- **`autoEnable: Property<Boolean>`** — default **`true`**. Auto-flips `buildMatrix`, `publishMatrix`, `detektPerVariant`, `excludeGeneratedFromFormatters`, `dependencyGuardPerVariant`, `enableBuildTypes` based on plugin detection + shape heuristics.
- Per-variant XCFramework MavenPublication (iOS).
- Per-variant `Package.swift` (SPM).
- Opt-in npm tarball publishing.
- Build Scan custom values + per-variant CycloneDX SBOM.
- Cross-variant `common{BuildType}` intermediate source sets (RFC §10) — opt-in via `createIntermediateBuildTypeSourceSets.set(true)`.
- Variant promotion DSL: `kmpFlavors.promote(from, to) { applyTransform(...) }`.
- Per-variant feature-flag hooks: `kmpFlavors.featureFlags { growthbook { defaultPayload.set(...) } }`.

### Breaking changes

**The `autoEnable=true` default is a behaviour change**. Multi-target projects that trip the heuristic (≥2 non-Android targets + ≥2 flavors) now auto-enable matrix mode, which moves `BuildKonfig` codegen out of `commonMain` into per-flavor source sets. Code reading `BuildKonfig` from `commonMain` breaks.

**Migration**: add `autoEnable.set(false)` at the top of your `kmpFlavors { }` block to preserve v2.1 semantics. Then incrementally migrate to per-flavor source sets, and drop the opt-out when ready.

### Validator codes added

- `KMPF-V13` (Gradle 9 Project Isolation), `KMPF-V14` (CMP < 1.7.0), `KMPF-V15` (Apple Silicon Rosetta), `KMPF-V16` (CMP × KGP), `KMPF-V17` (KGP × Gradle).

---

## v2.2 → v2.3

**Released**: 2026-05-15 (rolled into `v2.4.0-alpha.0`; not separately tagged).

### Added (opt-in)

- `detektPerVariantPerTarget: Property<Boolean>` — per-(variant × non-Android target) Detekt. Requires `detektPerVariant=true`. Default `false`.
- `variantCacheNamespacing: Property<Boolean>` — stub in v2.3 (no-op + forward-compat property). Default `false`.
- `composeHotReloadPerVariant: Property<Boolean>` — Option A: register `composeHotReload{Variant}{Target}` per variant. Default `false`.
- Sonatype Snapshots channel — nightly `publish-snapshot.yml` workflow. See [`PUBLISHING.md`](PUBLISHING.md) "Snapshot channel".

### Cross-repo improvements

- `mbl-actionhub-bump-version@v1.6.0` — SemVer-pre-release-aware bumping (`2.2.0-alpha.0` → `2.2.0-alpha.1`, not `2.2.1`).
- `mbl-actionhub@v1.6.1` — pre-release-aware GitHub Release flag auto-detection.
- `.github/workflows/auto-merge-bump-cron.yml` — 10-min cron safety-net for bump-PR auto-merge.

### Breaking changes

None.

### Validator codes added

None (V13-V17 stayed; V18+ deferred to v2.4 Phase 6A).

---

## v2.3 → v2.4

**Released**: `v2.4.0-alpha.0` on 2026-05-16; GA pending stability plan.

### Added

- `variantCacheNamespacing` — graduated from v2.3 stub to full impl. Injects `kmpFlavorVariant` as `@Input` on every `compileKotlin*` task. Prerequisite: `buildMatrix=true`.
- `variants.matching { … }.configureEach { dependencies { exclude(group, module) } }` — variant-conditional dependency excludes. New `VariantDependenciesScope` class.
- `switchVariantAndReload --to=<variant>` task — Compose hot-reload Option-B-workaround. Persists target variant + prints follow-up command. Tagged `CMP-API-WAITING` for v2.5+ real-Option-B graduation.
- IDE plugin v0.2.0-alpha.1 published to JetBrains Marketplace `eap` channel — gutter icons + variant-aware Refactor → Rename + breakpoint scope data layer.

### Validator codes added

- `KMPF-V18` (variant exclude target dep missing — reserved, fires as INFO log today).
- `KMPF-V19` (Sonatype Snapshots namespace not enabled — workflow-time only).
- `KMPF-V20` (`variantCacheNamespacing=true` without `buildMatrix=true`).
- `KMPF-V21` (reserved for v2.5+ — legacy `activeFlavor` DSL post-2026-11-14).
- `KMPF-V22` (variant `exclude(group="", module="")` — both empty).

### Breaking changes

None.

### Time-gated change (planned for v2.5+ post-2026-11-14)

- v1.x compat shim removal per RFC §3 Q15 deprecation contract. Migrate v1.x DSL before then — see [`MIGRATION_v1_to_v2.md`](MIGRATION_v1_to_v2.md).

---

## v2.4 → v2.5 (planned)

**Target**: post-2026-11-14.

Expected changes:

- v1.x `activeFlavor` DSL removed; `KMPF-V21` fires as ERROR.
- Phase 3 Compose hot-reload Option B graduation when CMP exposes the public reset API (tracked at [issue #75](https://github.com/MobileByteLabs/kmp-product-flavors/issues/75)).
- Experimental-bucket promotions to Stable based on real adopter feedback (Phase 6 of stability plan).

---

## Cumulative upgrade path (v1.5.x → v2.4.0)

For projects on `v1.5.x` (or earlier) considering jumping straight to `v2.4.0`:

1. Bump pin → `v2.4.0-alpha.0` (or latest patch).
2. Add `autoEnable.set(false)` at the top of `kmpFlavors { }` to preserve v1.x semantics.
3. Convert `activeFlavor.set("name")` to `flavors { register("name") { isDefault.set(true) } }`.
4. Verify build passes for active variant.
5. (Optional) opt into matrix mode + per-variant features incrementally per "Step 6" of [`MIGRATION_v1_to_v2.md`](MIGRATION_v1_to_v2.md).
6. Before 2026-11-14: remove the `autoEnable.set(false)` opt-out + complete the per-flavor source-set migration.

The `openMF/kmp-project-template` canary [PR #149](https://github.com/openMF/kmp-project-template/pull/149) demonstrates this exact path: `1.1.5` → `2.4.0-alpha.0` + `autoEnable.set(false)` migration, validated end-to-end across Android, Desktop ×3 OSes, iOS, Web.

---

## See also

- [`MIGRATION_v1_to_v2.md`](MIGRATION_v1_to_v2.md) — v1.x → v2.x detailed migration.
- [`REFERENCE.md`](REFERENCE.md) — current DSL reference.
- [`ERROR_CODES.md`](ERROR_CODES.md) — every `KMPF-V<NN>` code.
- [`CHANGELOG.md`](../CHANGELOG.md) — release-by-release log.
