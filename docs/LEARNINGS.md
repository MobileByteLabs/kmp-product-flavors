# Execution learnings — v28 unified flavor API

Captured during end-to-end implementation against `kmp-project-template` (AGP 9.2.1, Kotlin 2.3.21, Gradle 9.5.1). Each finding is paired with the source file that codifies the fix.

---

## L1 — AGP cross-module flavor propagation timing

**Problem.** Registering `androidComponents.finalizeDsl { … }` from the convention plugin's `afterEvaluate` block silently produced zero AGP `productFlavors`. The DSL callback queue was never drained by AGP for our callback.

**Why.** AGP processes its `finalizeDsl` callback queue once, during AGP's first `afterEvaluate`. Gradle fires `afterEvaluate` hooks in registration order. AGP applies before the convention plugin chain reaches `KmpFlavorPlugin`, so any callback registered from `KmpFlavorPlugin`'s `afterEvaluate` arrives **after** AGP has drained the queue and is silently ignored.

**Fix.** Register synchronously, not from `afterEvaluate`:
- `KmpFlavorPlugin.apply()` registers a `pluginManager.withPlugin("com.android.application") { … }` callback up-front.
- Inside that callback, `AgpProductFlavorRegistrar.apply()` attaches `whenObjectAdded` + `configureEach` listeners to `ext.flavors`.
- `whenObjectAdded` lands an AGP flavor slot synchronously the moment the consumer adds the corresponding `kmpFlavors.flavors` entry.
- `configureEach` updates the slot with the dimension + suffix once the consumer's lambda finishes.

**Source.**
- `build-logic/flavor-plugin/src/main/kotlin/com/mobilebytelabs/kmpflavors/KmpFlavorPlugin.kt`
- `build-logic/flavor-plugin/src/main/kotlin/com/mobilebytelabs/kmpflavors/internal/AgpProductFlavorRegistrar.kt`

**Locked decision.** GOAL.md D36.

---

## L2 — AGP 9 `Property<T>` setter surface

**Problem.** Reflective `setDimension(String)` calls succeeded on AGP 8.x but silently no-op'd on AGP 9.x. Same story for `applicationIdSuffix`, `versionNameSuffix`, several others.

**Why.** AGP 9.0 release notes: "many former mutable getters/setters were converted to Gradle `Property<T>` surfaces." `getDimension(): Property<String>` replaces `setDimension(String)`. A reflective caller assuming the classic Java-bean shape silently no-ops on AGP 9.

**Fix.** `AgpReflectiveSetters.set(target, name, value)`:
1. **Pattern 1.** Look for `set$Cap(T)` of matching arity. Invoke if found.
2. **Pattern 2.** Look for `get$Cap(): Property<T>`. If found, look for `set(value)` on the returned Property. Invoke if found.
3. Return `false` if neither pattern resolves — silent no-op upstream.

Every reflective setter against an AGP DSL object now routes through this helper. Forward-compatible: if AGP rearranges the API again to e.g. `Provider<T>` setters or a new mutable-bean form, the same two-pattern probe still resolves.

**Source.**
- `build-logic/flavor-plugin/src/main/kotlin/com/mobilebytelabs/kmpflavors/internal/AgpReflectiveSetters.kt`
- `build-logic/flavor-plugin/src/main/kotlin/com/mobilebytelabs/kmpflavors/internal/AgpProductFlavorRegistrar.kt` (consumer)
- `build-logic/flavor-plugin/src/main/kotlin/com/mobilebytelabs/kmpflavors/internal/AgpBridge.kt` (consumer)

**Locked decision.** GOAL.md D37, D43.

---

## L3 — RuntimeApi codegen-host election

**Problem.** Multi-module KMP projects with the plugin applied to every module produced `Type kmp.project.template.kmpflavors.KmpFlavorsRuntime is defined multiple times` at Android dex merge time.

**Why.** Each module independently ran `RuntimeApiGenerator` and wrote `commonMain/.../KmpFlavorsRuntime.kt`. All sources rolled into the application APK → duplicate class definitions.

**Fix.** Codegen-host election at the rootProject level:
- Claim key: `kmpFlavors.runtimeApiClaim:$packageName`.
- The first module to attempt codegen wins. Subsequent modules detect the claim:
  - If the path is **lex-greater** than the existing claim, skip codegen — the lower path will publish the type onto the classpath.
  - If lex-lower, replace the claim and proceed.
- Election lives in `FlavorPhaseDispatcher.generateRuntimeApi` and uses `rootProject.extensions.extraProperties`.

Symmetrical to the pre-existing `shouldGenerateCodegen` rule for `FlavorConfig`.

**Source.**
- `build-logic/flavor-plugin/src/main/kotlin/com/mobilebytelabs/kmpflavors/internal/FlavorPhaseDispatcher.kt`

**Locked decision.** GOAL.md D38.

---

## L4 — Single per-flavor source set axis

**Problem.** Creating per-(target × flavor) source sets like `iosSimulatorArm64ProdMain` that `dependsOn(iosSimulatorArm64Main)` raised KGP error: "Kotlin Source Set 'iosSimulatorArm64ProdMain' can't depend on 'iosSimulatorArm64Main' which is a default source set for compilation."

**Why.** KGP's `applyDefaultHierarchyTemplate` locks per-target source sets as compilation defaults. By KGP rule, no Kotlin source set may `dependsOn` a default-for-compilation source set. Dual-axis fan-out is permanently impossible under current KGP.

**Fix.** Single axis. Only `{F}Main` (depending on `commonMain`) is created. Consumers needing per-(target × flavor) logic place it inside `{F}Main` with `expect`/`actual` or platform-conditional code.

**Source.**
- `build-logic/flavor-plugin/src/main/kotlin/com/mobilebytelabs/kmpflavors/internal/KmpFlavorSourceSetWiring.kt`
- `build-logic/flavor-plugin/src/main/kotlin/com/mobilebytelabs/kmpflavors/internal/SourceSetHierarchy.kt`

**Locked decision.** GOAL.md D39.

---

## L5 — Reflection-safe Android template

**Problem.** Generated `KmpFlavorsRuntime` Android actual referenced `BuildConfig.APPLICATION_ID` directly. Worked in `com.android.application` modules; broke compilation in `com.android.library` KMP modules (`core-base:designsystem`) with `Unresolved reference 'BuildConfig'`.

**Why.** `com.android.library` modules don't emit `BuildConfig.APPLICATION_ID`; only `com.android.application` does. Direct references compile in some modules, fail in others — a hard contract divergence at codegen time.

**Fix.** `RuntimeApiGenerator.RuntimeAndroidActualTemplate` emits:
```kotlin
private fun readStringField(className: String, fieldName: String): String? = try {
    Class.forName(className).getField(fieldName).get(null) as? String
} catch (_: Throwable) { null }
```

with a String/Boolean fallback at every site. Compiles in every Android module. Reads the actual `BuildConfig` field at runtime where available, falls through to the codegen-time default otherwise.

**Source.**
- `build-logic/flavor-plugin/src/main/kotlin/com/mobilebytelabs/kmpflavors/internal/RuntimeApiGenerator.kt`

**Locked decision.** GOAL.md D40.

---

## AGP 9.2.1+ breaking-change index

The patterns above neutralise the following AGP 9 surface changes. New library code MUST route through these helpers — direct reflective calls or hard-coded DSL types against AGP-mutated names are forbidden.

| AGP 9 change | Library response |
|---|---|
| `ProductFlavor.setDimension(String)` removed | `AgpReflectiveSetters.set(flavor, "dimension", value)` |
| `ProductFlavor.setApplicationIdSuffix(String)` removed | `AgpReflectiveSetters.set(flavor, "applicationIdSuffix", value)` |
| `BuildType.setVersionNameSuffix(String)` removed | `AgpReflectiveSetters.set(buildType, "versionNameSuffix", value)` |
| `AndroidComponentsExtension.finalizeDSl(Action)` removed | `Proxy.newProxyInstance` implementing **both** `Action` and `Function1` (D42) |
| `ComponentBuilder.enabled` → `.enable` | Name fallback in `AgpReflectiveSetters` (D43) |
| `flavorDimensions(String...)` setter signature relaxed | Reflective lookup probes both varargs + List signatures |

**Source.**
- `build-logic/flavor-plugin/src/main/kotlin/com/mobilebytelabs/kmpflavors/internal/AgpReflectiveSetters.kt`
- `docs/AGP_SUPPORT.md`

---

## Cross-references

- **GOAL.md** — locked decisions D36-D43
- **PLAN.md** — Phase 18 + G-18 acceptance gate
- **18-execution-learnings.md** — Phase 18 sub-plan tasks T1-T7
- **AGP_SUPPORT.md** — 9.2.1+ floor contract + retired matrix CI rationale
