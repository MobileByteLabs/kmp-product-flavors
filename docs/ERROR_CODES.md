# `kmp-product-flavors` Error Code Catalog

> Stable error codes raised by `KmpFlavorPluginValidator` and related runtime checks. Once shipped at a version, each code retains the same meaning across minor releases so CI tooling (grep, IDE quick-fixes, error-aggregation dashboards) stays portable.

Each entry: `code`, `severity`, `message` (rendered to consumers), `fix` (concrete suggestion), `since` (first plugin version shipping the code).

---

## KMPF-V01 — Flavor / build-type name collision

| | |
|---|---|
| **Severity** | ERROR |
| **Since** | v2.0.0 |
| **Message** | Flavor `<name>` has the same name as a build type. Variant names become ambiguous when this happens (the plugin can't tell whether `freeDebug` is `free × Debug` or `freeDebug × <unset>`). |
| **Fix** | Rename either the flavor or the build type so they no longer collide. Convention: flavor names are nouns (`free`, `paid`, `enterprise`); build type names are adjectives (`debug`, `release`, `staging`). |

---

## KMPF-V02 — Flavor declared without dimension assignment *(W2 — pending)*

| | |
|---|---|
| **Severity** | ERROR |
| **Since** | v2.0.0+ (W2 follow-up) |
| **Message** | Flavor `<name>` is declared without a `dimension.set(...)` call but dimensions are registered. Mixed dimension/no-dimension flavors are ambiguous. |
| **Fix** | Either set `dimension.set("...")` on every flavor, or remove all dimensions to use single-dimension semantics. |

---

## KMPF-V03 — Dimension has no flavors *(currently thrown by FlavorVariantResolver)*

| | |
|---|---|
| **Severity** | ERROR |
| **Since** | v1.x (currently as `IllegalStateException`; migrated to KMPF-V03 in W2) |
| **Message** | Dimension `<name>` has no flavors assigned to it. The dimension can never produce a variant. |
| **Fix** | Either assign at least one flavor to the dimension via `dimension.set("<name>")` on the flavor, or remove the empty dimension. |

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

## KMPF-V06 — Unknown active variant *(W2 — pending)*

| | |
|---|---|
| **Severity** | ERROR |
| **Since** | v2.0.0+ (W2 follow-up) |
| **Message** | `-PkmpFlavor=<name>` references variant `<name>`, which isn't a registered combination. Registered variants: `[…]`. |
| **Fix** | Pick a registered variant from the list (case-sensitive) OR omit `-PkmpFlavor` to let the plugin resolve from `isDefault` flags. |

---

## KMPF-V07 — Invalid `buildConfigField` type *(W2 — pending)*

| | |
|---|---|
| **Severity** | ERROR |
| **Since** | v2.0.0+ (W2 follow-up; currently as `IllegalArgumentException` in `GenerateBuildConfigTask`) |
| **Message** | `buildConfigField` declared `<type>` is not a supported Kotlin literal type. Supported: `Boolean`, `Int`, `Long`, `Float`, `Double`, `String`. |
| **Fix** | Pick one of the supported types, or stringify the value (e.g. `buildConfigField("String", "X", "\"value\"")`). |

---

## KMPF-V08 — Matrix mode opted in but no flavors registered

| | |
|---|---|
| **Severity** | ERROR |
| **Since** | v2.0.0 |
| **Message** | `kmpFlavors.buildMatrix` is enabled but no flavors are registered. Matrix mode requires at least one flavor to generate compilations from. |
| **Fix** | Either register flavors via `kmpFlavors { flavors { register("…") } }` in the convention plugin, or remove the `buildMatrix.set(true)` / `gradle.properties: kmpFlavors.buildMatrix=true` opt-in. |

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
