# `kmp-product-flavors` Error Code Catalog

> Stable error codes raised by `KmpFlavorPluginValidator` and related runtime checks. Once shipped at a version, each code retains the same meaning across minor releases so CI tooling (grep, IDE quick-fixes, error-aggregation dashboards) stays portable.

Each entry: `code`, `severity`, `message` (rendered to consumers), `fix` (concrete suggestion), `since` (first plugin version shipping the code), and (where relevant) an `example` snippet that triggers the finding.

---

## KMPF-V01 — Flavor / build-type name collision

| | |
|---|---|
| **Severity** | ERROR |
| **Since** | v2.0.0 |
| **Message** | Flavor `<name>` has the same name as a build type. Variant names become ambiguous when this happens (the plugin can't tell whether `freeDebug` is `free × Debug` or `freeDebug × <unset>`). |
| **Fix** | Rename either the flavor or the build type so they no longer collide. Convention: flavor names are nouns (`free`, `paid`, `enterprise`); build type names are adjectives (`debug`, `release`, `staging`). |

---

## KMPF-V02 — Flavor declared without dimension assignment

| | |
|---|---|
| **Severity** | ERROR |
| **Since** | v2.1.0 |
| **Message** | Flavor `<name>` is declared without a `dimension.set(...)` call but `<N>` dimension(s) are registered (`<list>`). Mixed dimension/no-dimension flavors are ambiguous — every flavor must specify which dimension it belongs to. |
| **Fix** | Either set `dimension.set("<dimensionName>")` on every flavor, or remove all dimensions to use single-dimension semantics. |
| **Example** | `flavors { register("free") { dimension.set("tier") }; register("paid") /* missing */ }` with `flavorDimensions { register("tier") }`. |

---

## KMPF-V03 — Dimension has no flavors

| | |
|---|---|
| **Severity** | ERROR |
| **Since** | v2.1.0 (was an `IllegalStateException` from `FlavorVariantResolver` in v1.x / v2.0; migrated to a structured finding in v2.1) |
| **Message** | Dimension `<name>` has no flavors assigned to it. The dimension can never produce a variant. |
| **Fix** | Either assign at least one flavor to the dimension via `dimension.set("<name>")` on a flavor, or remove the empty dimension from `flavorDimensions { }`. |
| **Example** | `flavorDimensions { register("tier"); register("env") }` with only `tier`-dimensioned flavors → V03 fires for `env`. |
| **Note** | V03 suppresses V04 when both conditions hold (V03 is the more specific finding for an empty matrix). |

---

## KMPF-V04 — `variantFilter` excluded every variant

| | |
|---|---|
| **Severity** | ERROR |
| **Since** | v2.0.0 |
| **Message** | Variant filter excluded every variant — no buildable variant remains. With N flavor(s) and M build type(s) declared, the matrix should not be empty. |
| **Fix** | Relax the `variantFilter { }` predicate or remove it. Run `./gradlew :listFlavors` once the filter is fixed to verify the matrix. |

---

## KMPF-V05 — Matrix mode opted in but zero non-Android KMP targets

| | |
|---|---|
| **Severity** | WARNING |
| **Since** | v2.0.0 |
| **Message** | `kmpFlavors.buildMatrix` is enabled but no non-Android KMP targets are declared. Matrix mode has nothing to register; this is a no-op (warning, not error — likely a configuration ordering issue). |
| **Fix** | Add a non-Android KMP target (`jvm()`, `iosX64()`, `js(IR)`, `wasmJs()`, etc.) to `kotlin { }`, or remove the `buildMatrix` opt-in. If you ARE declaring targets but they're being filtered — note that the synthetic `metadata` target and the Android JVM target are deliberately excluded from matrix mode. |

---

## KMPF-V06 — Unknown active variant

| | |
|---|---|
| **Severity** | WARNING |
| **Since** | v2.1.0 |
| **Message** | `-PkmpFlavor=<name>` references variant `<name>`, which isn't a registered combination. Registered variants: `[…]`. Falling back to the default variant. |
| **Fix** | Pick a registered variant from the list (case-insensitive) OR omit `-PkmpFlavor` to let the plugin resolve from `isDefault` flags. If the property is intentional for a sibling project in a multi-project build, this warning is informational and can be ignored for the projects that don't recognise the value. |
| **Why WARNING, not ERROR** | The `-PkmpFlavor` property is project-wide: in a multi-project build, sibling projects with their own variant matrix legitimately won't recognise the value. Treating that as an ERROR would break the whole build for a benign case. The plugin soft-falls to the default variant. |

---

## KMPF-V07 — Invalid `buildConfigField` type

| | |
|---|---|
| **Severity** | ERROR |
| **Since** | v2.1.0 |
| **Message** | Flavor `<flavor>` declares `buildConfigField` `<name>` with type `<type>`, which is not a supported Kotlin literal type. Supported: `Boolean`, `Int`, `Long`, `Float`, `Double`, `String`. |
| **Fix** | Pick one of the supported types, or stringify the value (e.g. `buildConfigField("String", "X", "\"value\"")`). |
| **Example** | `buildConfigField("MyClass", "FOO", "Foo()")` → V07 fires because the codegen can only emit Kotlin `const val` literals for the supported types. |

---

## KMPF-V14 — Compose Multiplatform version too old for per-variant `composeResources/`

| | |
|---|---|
| **Severity** | WARNING |
| **Since** | v2.2.0 (Phase 0E) |
| **Message** | Compose Multiplatform version `<x.y.z>` is older than `1.7.0`. Per-variant `composeResources/` auto-discovery on custom source sets (`commonFree`, `commonPaid`, etc.) lands in CMP 1.7. |
| **Fix** | Upgrade `org.jetbrains.compose` to `>= 1.7.0` OR add the per-flavor resource directories manually via `kotlin.sourceSets.commonFlavor.resources.srcDir(...)` for each flavor. |
| **Why WARNING, not ERROR** | The plugin still configures everything else correctly — only the per-variant resource auto-discovery may silently no-op on older CMP. Compilation still succeeds; consumers see commonMain resources instead of their per-flavor overrides. |

---

## KMPF-V08 — Matrix mode opted in but no flavors registered

| | |
|---|---|
| **Severity** | ERROR |
| **Since** | v2.0.0 |
| **Message** | `kmpFlavors.buildMatrix` is enabled but no flavors are registered. Matrix mode requires at least one flavor to generate compilations from. |
| **Fix** | Either register flavors via `kmpFlavors { flavors { register("…") } }` in the convention plugin, or remove the `buildMatrix.set(true)` / `gradle.properties: kmpFlavors.buildMatrix=true` opt-in. |

---

## KMPF-V15 — Apple Silicon host targeting iosX64 simulator (Rosetta workaround)

| | |
|---|---|
| **Severity** | WARNING |
| **Since** | v2.2.0 (Phase 0I) |
| **Message** | Apple Silicon host (`aarch64` / `arm64`) is declaring an `iosX64` target. Some Kotlin/Native toolchain versions need Rosetta to assemble the iosX64 simulator framework on M-series hardware. |
| **Fix** | Either drop `iosX64()` (M-series simulators use `iosSimulatorArm64()`), OR run Gradle under Rosetta: `arch -x86_64 ./gradlew :module:assembleAllVariants`. |

---

## KMPF-V16 — CMP × KGP version combination known-incompatible

| | |
|---|---|
| **Severity** | WARNING |
| **Since** | v2.2.0 (Phase 0L) |
| **Message** | Known-incompatible combination: Compose Multiplatform `<x.y.z>` + Kotlin Gradle Plugin `<a.b.c>`. Per-variant `composeResources/` auto-discovery on custom source sets silently no-ops on this pairing. |
| **Fix** | Upgrade `org.jetbrains.compose` to `>= 1.7.0`, OR downgrade KGP to `< 2.2.0`, OR add per-flavor resource directories manually via `kotlin.sourceSets.commonFlavor.resources.srcDir(...)`. |

---

## KMPF-V17 — KGP × Gradle version combination known-incompatible

| | |
|---|---|
| **Severity** | WARNING |
| **Since** | v2.2.0 (Phase 0L) |
| **Message** | Known-incompatible combination: KGP `<a.b.c>` + Gradle `<x.y>`. The Hierarchy Template surface is unstable on this pairing; matrix-mode source-set wiring may emit spurious `Invalid Source Set Dependency Across Trees` warnings. |
| **Fix** | Upgrade Gradle to `>= 8.5` (recommended) OR upgrade KGP to `>= 2.1.0`. |

---

## How to suppress / triage in CI

Findings are surfaced through Gradle's standard logger:

- **ERROR** → `GradleException` thrown; build fails at configuration time.
- **WARNING** → `logger.warn(...)` printed; build continues.

To grep CI output for a specific code:

```bash
./gradlew assemble 2>&1 | grep -oE 'KMPF-V[0-9]+' | sort -u
```

---

## Backwards compatibility

A shipped code never changes meaning. If validation logic evolves, new codes are added with the next minor version (e.g., `KMPF-V09`). Consumers can pin their CI checks to specific codes safely.
