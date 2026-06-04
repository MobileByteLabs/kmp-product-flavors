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

## KMPF-V13 — Gradle 9 Project Isolation violation in codegen-claim

| | |
|---|---|
| **Severity** | WARNING |
| **Since** | v2.2.0 (Phase 1B) |
| **Message** | Project Isolation enabled on Gradle 9.0+. The plugin's codegen-claim mechanism reads/writes `rootProject.extraProperties` to coordinate multi-module codegen-host election; this triggers a cross-project state warning under `--project-isolation`. |
| **Fix** | Set `kmpFlavors.codegenHost.set(true)` explicitly on your designated codegen-host module + `set(false)` on every other module that applies the plugin. Explicit claims short-circuit the rootProject-extras lookup. Full refactor to Gradle's `IsolatedProjects` API tracked for v2.3. |

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

## KMPF-V18 — Variant exclude target dependency missing

| | |
|---|---|
| **Severity** | WARNING |
| **Since** | v2.4.0 (Phase 6A) |
| **Message** | Variant '<variantName>' declared `dependencies { exclude(group="<g>", module="<m>") }` but the target module is not present in the variant's resolved compile classpath. The exclude is a no-op — possibly a typo in the coordinate. |
| **Fix** | Verify the (group, module) coordinate matches a real dependency in the variant's classpath. Run `./gradlew :module:dependencies --configuration <variant>RuntimeClasspath` to inspect. If the dep was supposed to be there transitively but isn't, the exclude was unnecessary in the first place. |
| **When fires** | At plugin-apply afterEvaluate phase, after `DependencyConfigurator.applyVariantExcludes` runs. **Currently surfaces as an INFO log; will promote to WARNING finding in a future v2.4.x once configuration-time classpath introspection is cheap enough**. |

---

## KMPF-V19 — Sonatype Snapshots configured but namespace not enabled

| | |
|---|---|
| **Severity** | ERROR (publish-time) |
| **Since** | v2.4.0 (Phase 6A — surfaced as a workflow failure mode, not a plugin-emitted code) |
| **Message** | `publish-snapshot.yml` workflow returned `403 Forbidden` from `central.sonatype.com/repository/maven-snapshots/`. The Sonatype Central Portal namespace is not snapshot-enabled. |
| **Fix** | Namespace owner enables snapshots: sign in to https://central.sonatype.com → Namespaces → `io.github.mobilebytelabs` → Settings → toggle "Publish SNAPSHOTs" on. Already enabled for `io.github.mobilebytelabs` as of 2026-05-15. |
| **When fires** | Workflow `publish-snapshot` step "Publish to Maven Central Portal (snapshot repo)" returns 403. Not a plugin runtime code — included in this catalog for symmetry. |

---

## KMPF-V20 — Variant cache namespacing requested without matrix mode

| | |
|---|---|
| **Severity** | INFO |
| **Since** | v2.4.0 (Phase 6A) |
| **Message** | `kmpFlavors.variantCacheNamespacing=true` but `buildMatrix=false`. Matrix mode is a prerequisite — without per-variant compilations, there's nothing to namespace. |
| **Fix** | Set `kmpFlavors.buildMatrix.set(true)` to enable per-variant cache scoping. If matrix mode isn't wanted, leave `variantCacheNamespacing` at its default `false`. |
| **When fires** | At plugin-apply time, when `VariantBuildCacheKeyConfigurator.configure()` runs. Currently surfaces as an INFO log line; promoted from log-only to a structured code in v2.4.0 (Phase 6A). |

---

## KMPF-V21 — Legacy `activeFlavor` DSL referenced post-deprecation

| | |
|---|---|
| **Severity** | ERROR |
| **Since** | v2.5.0 (Phase 4 — v1.x shim removal; cannot ship before 2026-11-14 per RFC §3 Q15 deprecation contract) |
| **Message** | `kmpFlavors.activeFlavor` was the v1.x DSL surface. The 6-month deprecation window from v2.0 GA (2026-05-14) expired on 2026-11-14; the shim has been removed. |
| **Fix** | Use the v2.x DSL: `kmpFlavors { flavors { register("free") { isDefault.set(true) } } }`. See [`REFERENCE.md`](REFERENCE.md). |
| **When fires** | Reserved for v2.5.0. The constant is registered in v2.4.0 so consumers can grep for KMPF-V21 references early. The actual runtime emission happens after the v1.x shim is removed; on v2.4.x, this code is never emitted. |

---

## KMPF-V22 — Variant exclude declared with empty coordinates

| | |
|---|---|
| **Severity** | WARNING |
| **Since** | v2.4.0 (Phase 5 — variant-conditional dep excludes) |
| **Message** | Variant '<variantName>' registered `exclude(group="", module="")`. Both coordinates empty — the exclude rule matches nothing. |
| **Fix** | Pass at least one coordinate: `exclude(group = "com.example")` (matches any module in that group), `exclude(module = "premium-sdk")` (matches any group with that module name), or both for the precise coordinate. |
| **When fires** | At plugin-apply afterEvaluate phase when `DependencyConfigurator.applyVariantExcludes` walks each variant's `exclude(...)` registrations. Currently logged as a Gradle warning; structured code reserved for future v2.4.x promotion. |

---

## KMPF-V23 — Custom `buildConfigField` name collides with auto-derived `BuildKonfig` constant

| | |
|---|---|
| **Severity** | ERROR |
| **Since** | v2.4.0 (stability-phase Phase 1 follow-up) |
| **Message** | `<flavor or buildType> '<sourceName>'` declares `buildConfigField '<fieldName>'`, which collides with `<one of: an auto-generated constant emitted by every BuildKonfig / an auto-generated constant emitted when buildTypes are registered / the auto-derived flavor flag for flavor '<X>' / the auto-derived build-type flag for build type '<X>'>`. BuildKonfig codegen would emit two `const val <fieldName>` entries and the Kotlin compiler would fail with "Conflicting declarations". |
| **Fix** | Rename the custom field to avoid the reserved namespace. Avoid the `IS_*` prefix for custom flags (the plugin reserves it for auto-derived flavor/build-type flags) and the literal names `VARIANT_NAME` / `BUILD_TYPE`. Convention: prefix custom flags with the tier semantic — e.g. `MAX_*`, `TIER_*`, `PREMIUM_*`, `FEATURE_*`. The validator suggests a concrete rename: `IS_<X>` → `TIER_<X>`, `VARIANT_NAME` → `APP_VARIANT_NAME`, `BUILD_TYPE` → `APP_BUILD_TYPE`. |
| **When fires** | At plugin-apply time. Reserved-name set is computed from THIS configuration — only actually-registered flavors/buildTypes contribute auto-derived constants (a literal `IS_DEBUG` field on a project that doesn't declare a `debug` build type is fine). Surfaces the collision BEFORE codegen so the consumer doesn't hit Kotlin's "Conflicting declarations" at compile time. Discovered via the `samples/multi-target-multi-variant/` stability sample. |

---

## KMPF-V24 — Mutex: `dimensions {}` AND legacy flat DSL used together

| | |
|---|---|
| **Severity** | ERROR |
| **Since** | v2.5.0 (Phase 1 — multi-dim DSL ergonomic sugar + AGP cross-product bridge) |
| **Message** | `kmpFlavors {}` cannot mix the v2.5 `dimensions { }` sugar with the legacy `flavorDimensions { } + flavors { }` blocks. Pick one style per project: either `dimensions { dimension("tier") { flavor("free") } }` OR `flavorDimensions { register("tier") } + flavors { register("free") { dimension.set("tier") } }`. |
| **Fix** | Pick one DSL style per project. See [`REFERENCE.md`](REFERENCE.md) for the canonical `dimensions { … }` shape. |
| **When fires** | At plugin-apply time. The validator's `validate()` function receives `dimensionsDslUsed` + `legacyFlatDslUsed` flags wired from `KmpFlavorExtension`; when both are true, V24 fires. Strict-additive contract preserved — v2.4 consumers using only the flat DSL never see V24. |

---

## KMPF-V25 — Duplicate dimension name OR AGP-side conflict on re-apply

| | |
|---|---|
| **Severity** | ERROR |
| **Since** | v2.5.0 (Phase 1 — AGP cross-product bridge) |
| **Message** | Dimension '<dimName>' is declared more than once. Each dimension must have a unique name — duplicate declarations produce ambiguous flavor↔dimension mappings. (Also fires from the AGP bridge cross-product path when an existing AGP flavor with the same name has a CONFLICTING `dimension =` assignment — cross-vault hand-edit case.) |
| **Fix** | Rename one of the duplicate declarations OR remove it. If you intended two SEPARATE axes of variation, give them distinct names (e.g. "tier" + "tierVariant"). For AGP-side conflicts: ensure no hand-written `android { productFlavors {} }` conflicts with KMP-driven flavors. |
| **When fires** | (1) Validator emit-site — fires when `dimensions.groupBy { it.name }.filter { size > 1 }` is non-empty. (2) AgpBridge emit-site (warn-only log) — fires when `propagateFlavorsCrossProduct` detects existing AGP flavors that don't cover the KMP-side declarations. |

---

## KMPF-V26 — Secret resolution failure / schema-fallback

| | |
|---|---|
| **Severity** | ERROR (resolution-fail at task-execution time) OR WARNING (schema v2.0 fallback at configuration time) |
| **Since** | v2.5.0 (Phase 3 — BuildKonfig codegen expansion) |
| **Message** | `kmpFlavors.buildKonfig { secret(...) }` is declared for `<secretIds>`, but the consumer's `secrets-manifest.yaml` is `schema_version='<X.Y>'`. Schema v2.1+ is required for flavor-aware secret resolution. The plugin will emit placeholder values (e.g. `<unresolved:schema-v2.0>`) instead of hardcoded secrets (SV15 compliance per `RULE-SECRETS-VAULT-001`). |
| **Fix** | Upgrade `secrets-manifest.yaml` to `schema_version: "2.1"` and add `needs[].flavor_selector` blocks for the declared secret IDs. See [`docs/SECRETS_INTEGRATION.md`](SECRETS_INTEGRATION.md) for the consumer contract. |
| **When fires** | WARN path: validator's `validateBuildKonfigDsl` emits at configuration time when `buildKonfig { secret(id) }` is declared AND the consumer's manifest is schema < v2.1. ERROR path: `BuildKonfigSecretResolver.resolveForVariant` returns `SecretResolution.Unavailable` and downstream task action emits the structured ERROR (real value flow ships in v2.5.x patch — see SECRETS_INTEGRATION.md). |

---

## KMPF-V27 — Custom type emit failure

| | |
|---|---|
| **Severity** | ERROR |
| **Since** | v2.5.0 (Phase 3 — BuildKonfig codegen expansion) |
| **Message** | `kmpFlavors.buildKonfig { customField<T>("<name>", ...) }` declared with type '<typeDesc>', which the codegen cannot emit. Supported: primitives (Boolean/Int/Long/Float/Double/String), sealed classes, and flat `List<T>` where T is a primitive or sealed class. |
| **Fix** | Convert to a sealed class with explicit subclass objects, OR stringify the value via a primitive customField. Nested generics (`Map<K, V>`, `List<List<T>>`) and open classes are out of scope for v2.5. |
| **When fires** | Validator emit-site (`validateBuildKonfigDsl`) — fires at configuration time when caller reports unsupported types via the `customFieldUnsupportedTypes` parameter. Codegen-side detection (during `GenerateBuildConfigTask.generate()`) is reserved for v2.5.x — the v2.5 task uses the DSL's `typeDescriptor: String` directly, so type-inference failures are surfaced as Kotlin compile errors on consumer source. |

---

## KMPF-V28 — `perTarget` references a target not in `kotlin.targets`

| | |
|---|---|
| **Severity** | ERROR |
| **Since** | v2.5.0 (Phase 3 — BuildKonfig codegen expansion) |
| **Message** | `kmpFlavors.buildKonfig { perTarget("<targetName>") { } }` references a target that isn't declared in this project's `kotlin { ... }` block. Available targets: '<list>'. |
| **Fix** | Use a target name actually declared in `kotlin { ... }` (e.g. 'iosMain', 'androidMain', 'desktopMain', 'wasmJsMain'), OR add the missing target to the `kotlin { ... }` block. |
| **When fires** | Validator emit-site (`validateBuildKonfigDsl`) — fires at configuration time when `perTargetNamesDeclared - kotlinTargetNames` is non-empty. The check uses both `kotlin.targets.map { it.name }` (target names) AND `kotlin.sourceSets.map { it.name }` (source-set names like `iosMain`) so consumers can name either. |

## KMPF-V29 — `baseUrl` flavor missing

| | |
|---|---|
| **Severity** | ERROR |
| **Since** | v2.6.0 (Phase 4 — conditional target sets + Network/Ktor codegen) |
| **Trigger** | `kmpFlavors.buildKonfig.network { baseUrl("X" to "...") }` references flavor `"X"` but no flavor with that name is registered in any dimension or in `flavors { register() }` |
| **Message** | `kmpFlavors.buildKonfig.network { baseUrl("X" to ...) } references flavor 'X' but no flavor with that name is registered. Available flavors: ...` |
| **Fix** | Either register the flavor via `dimensions { dimension("...") { flavor("X") } }` or `flavors { register("X") {} }`, or remove the orphan baseUrl key |
| **When fires** | Configuration time — `validateBuildKonfigDsl` computes `buildKonfigBaseUrlFlavors - registeredFlavorNames`; non-empty diff fires V29 per missing flavor |

## KMPF-V30 — No baseUrl for active variant

| | |
|---|---|
| **Severity** | ERROR |
| **Since** | v2.6.0 (Phase 4 — conditional target sets + Network/Ktor codegen) |
| **Trigger** | Some resolved variant's primary active flavor has no `baseUrl` mapped in the `network {}` block |
| **Message** | `Variant 'X' resolves to active flavor 'Y' but kmpFlavors.buildKonfig.network has no baseUrl mapped for it. baseUrl flavors: ...` |
| **Fix** | Either add `baseUrl("Y" to "https://...")` to the `network {}` block, or refine `variantFilter {}` to exclude variant `'X'` |
| **When fires** | Configuration time — validator iterates resolved variants; for each variant's active flavor, checks `flavor ∈ buildKonfigBaseUrlFlavors`; misses fire V30 per offending variant |

---

## Backwards compatibility

A shipped code never changes meaning. If validation logic evolves, new codes are added with the next minor version (v2.5 used the next sequential range `V24-V28`; `V09-V13` remain reserved-but-unused per the original v2.0 catalog plan). Consumers can pin their CI checks to specific codes safely.
