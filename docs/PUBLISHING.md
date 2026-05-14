# Per-variant publishing — Consumer reference

> v2.1+ opt-in. Builds on the v2.0 JVM publishing mechanism (Q21-D) and extends it to iOS and JS-family targets.

This document is the consumer-facing reference for **per-variant publishing**. The accompanying design context lives in `MATRIX_MODE.md` and the v2.1 implementation plan.

---

## TL;DR

```kotlin
kmpFlavors {
    buildMatrix.set(true)
    publishMatrix.set(true)
    flavors {
        register("free") { isDefault.set(true) }
        register("paid")
    }
}
```

That's the entire opt-in. The plugin registers classifier-tagged Maven publications per (inactive variant × target) automatically.

---

## What gets published per target family

### JVM (v2.0)

For each (inactive variant × `jvm("...")` target):

- **Jar task**: `jar{Variant}Kotlin{Target}` — packages the variant compilation's class output, with `archiveClassifier = "{variant}"`.
- **Maven publication**: `variant{Variant}` — registered on the `PublishingExtension`.
- **Resolve**: `./gradlew publishVariant{Variant}PublicationToMavenLocal`.

Consumer dependency form:

```kotlin
implementation("com.example:my-library:1.0.0:paid")
```

### iOS (v2.1, Phase 5A)

For each (inactive variant × `iosX64()` / `iosArm64()` / `iosSimulatorArm64()` target):

- **Zip task**: `zip{Variant}Kotlin{IosTarget}` — bundles the variant compilation's klibrary output.
- **Maven publication**: `variant{Variant}{IosTarget}Ios` — classifier `{variant}-{iosTarget}`.
- **Resolve**: `./gradlew publishVariant{Variant}{IosTarget}IosPublicationToMavenLocal`.

Consumer dependency form:

```kotlin
implementation("com.example:my-library:1.0.0:paid-iosArm64")
```

### JS / WasmJs (v2.1, Phase 5B+5C)

For each (inactive variant × `js(IR)` / `wasmJs()` target):

- **Zip task**: `zip{Variant}Kotlin{JsTarget}`.
- **Maven publication**: `variant{Variant}{JsTarget}Js` — classifier `{variant}-{jsTarget}`.
- **Resolve**: `./gradlew publishVariant{Variant}{JsTarget}JsPublicationToMavenLocal`.

Consumer dependency form:

```kotlin
implementation("com.example:my-library:1.0.0:paid-js")
implementation("com.example:my-library:1.0.0:paid-wasmJs")
```

---

## What is NOT auto-built in v2.1 (deferred to v2.2)

### Per-variant `XCFramework` aggregation

KGP's `XCFramework()` API aggregates **`Framework` binaries** (compiled Apple binaries, not klibs) across iOS targets into a single `.xcframework` directory.

Per-variant `Framework` binaries on custom compilations have known KGP edge cases (`binaries.framework {}` defaults to linking against the target's `main` compilation), which deserves a focused v2.2 pass. v2.1's iOS scope ships the Maven publication infrastructure today; consumers can wire per-variant `XCFramework` aggregation manually in their build script if needed.

### Per-variant `Package.swift` (SPM)

The existing `GenerateSpmManifestTask` (v2.0) ships single-variant SPM. Per-variant SPM entries require per-variant XCFramework (above) to be solid first. v2.2 scope.

### npm registry publishing

The plugin does **not** manage `~/.npmrc`, call `npm publish`, or set `package.json.name`. Per the v2.1 plan risk register, this is intentionally consumer-side:

- npm registry credentials live in the consumer's `~/.npmrc` (or CI secret store).
- The classifier-tagged Maven publication produced by `PerVariantJsPublishConfigurator` is the publishable artifact.
- For consumers who want true npm-tarball distribution, the typical pattern is:
  1. Build the per-variant artifact via `zip{Variant}KotlinJs`.
  2. Use KGP's `kotlinNpmPublishToRegistry` (or your own task) to push to npm with the right tarball metadata.

The `kmpFlavors.npmPackagePrefix` opt-in (configurable `package.json.name = "{prefix}-{variant}"`) is on the v2.2 roadmap if consumer survey data shows demand.

### Per-variant signing / GPG

Maven artifact signing is consumer-side (`signing { sign(publishing.publications) }`). The plugin's per-variant publications hook into the consumer's existing signing configuration unchanged.

---

## Risk register

### Apple Silicon + iOS framework toolchain

Per-variant iOS compilation produces klibraries cleanly on Apple Silicon. **Real XCFramework assembly** (deferred to v2.2) may need Rosetta workarounds for certain sub-target combinations on Apple Silicon — this will be documented when v2.2 lands.

### npm package-name collision

When `kmpFlavors.npmPackagePrefix` lands in v2.2, the convention is `package.json.name = "{root}-{variant}"`. If your consumer's existing npm package name collides, override via `kmpFlavors.npmPackagePrefix.set("custom-prefix")`.

### Maven classifier collisions

Multiple inactive variants × multiple targets produces N classifiers. Convention:
- JVM: `{variant}` (e.g., `paid`).
- iOS: `{variant}-{iosTarget}` (e.g., `paid-iosArm64`).
- JS-family: `{variant}-{jsTarget}` (e.g., `paid-js`, `paid-wasmJs`).

Classifiers don't overlap across target families. If your consumer's existing classifier scheme conflicts, the plugin's prefix is configurable in v2.2.

---

## Opt-out

Setting `publishMatrix.set(false)` (or removing the opt-in entirely) removes all per-variant publication tasks. The plugin reverts to single-published (v1.x) behaviour for your project's primary coordinate.

---

## Where per-variant publishing does NOT apply

| Excluded | Why |
|---|---|
| Android target (`androidTarget()`) | AGP's own publishing infrastructure handles per-flavor APKs/AARs natively. The KMP-flavors AGP bridge propagates flavors into AGP, so the standard AGP product-flavor publish path applies. |
| Synthetic `metadata` target | KGP rejects custom compilations on this target. |
| Modules with zero inactive variants | Matrix mode off / single-flavor module → no per-variant artifacts to publish. |

---

## See also

- `MATRIX_MODE.md` — the broader matrix-mode reference (compilation, source sets, BuildConfig, resources).
- `ERROR_CODES.md` — KMPF-Vxx validator code catalog.
- RFC §3 Q21 — the original design rationale for per-variant publishing.
