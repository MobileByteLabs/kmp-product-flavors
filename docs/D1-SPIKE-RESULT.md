# D1 Spike — Per-variant `KotlinCompilation` on non-Android targets

**Date**: 2026-05-12
**Branch**: `spike/d1-per-variant-compilation`
**Verdict**: ✅ **GREEN — architecturally feasible**

---

## Problem

KMP non-Android targets today build only the **active** variant — the one resolved from `-PkmpFlavor=…`. AGP, by contrast, builds the **full matrix** of Android variants in parallel. Closing this gap is the v2.0 product story for `kmp-product-flavors`: register one `KotlinCompilation` per variant per target so the plugin can build all N variants in a single Gradle invocation.

## Experiment

In `samples/basic-flavors/build.gradle.kts`, programmatically created a second `KotlinCompilation` named `freeDev` on the `jvm("desktop")` target, alongside the default `main` compilation:

```kotlin
jvm("desktop") {
    compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
    compilations {
        val main by getting
        val freeDev by creating {
            defaultSourceSet {
                kotlin.srcDir("src/commonMain/kotlin")
                kotlin.srcDir("src/commonFree/kotlin")
                kotlin.srcDir("src/commonDev/kotlin")
            }
            associateWith(main)
        }
    }
}
```

## Result

`./gradlew :samples:basic-flavors:tasks --all` shows KGP cleanly registered:

| Task | Source |
|---|---|
| `compileFreeDevKotlinDesktop` | KGP, "Compiles the compilation 'freeDev' in target 'desktop'" |
| `compileDesktopFreeDevJava` | Gradle java plugin |
| `desktopFreeDevClasses` | Output assembly aggregator |
| `desktopFreeDevProcessResources` | Resource processing |

`./gradlew :samples:basic-flavors:compileFreeDevKotlinDesktop --rerun-tasks` → **BUILD SUCCESSFUL in 14s**.

Output layout:
```
build/classes/kotlin/desktop/freeDev/com/...   # output classes
build/kotlin/compileFreeDevKotlinDesktop/      # incremental cache
```

The `main` compilation's output at `build/classes/kotlin/desktop/main/` is untouched — both variants coexist in the same build.

## What this proves

1. **Multiple `KotlinCompilation`s per non-Android target are supported** by KGP at configuration time.
2. Each compilation gets:
   - Independent source set roots
   - Independent output classes directory
   - Independent incremental compilation cache
   - Its own Kotlin + Java + resources tasks
3. `associateWith(main)` lets the additional compilation inherit the main classpath — so transitive dependencies (e.g., `kotlinx-coroutines-core` from `commonMain`) resolve cleanly without redeclaration.

## What this does NOT yet prove (RFC work)

These are the next questions for a v2.0 RFC, not for the spike:

- **BuildConfig codegen per variant**: `:generateFlavorBuildConfig` currently emits ONE `AppConfig.kt` under `build/generated/kmpFlavors/commonMain/kotlin/`. v2.0 needs per-variant outputs (e.g., `build/generated/kmpFlavors/freeDevDesktop/kotlin/`) wired into the matching compilation's `kotlin.srcDir(...)`.
- **iOS / JS / WasmJs targets**: only proved JVM. KotlinNativeTarget compilations behave differently (per-platform klibs).
- **Test compilations** for each variant.
- **Kotlin Hierarchy Template** integration — does it traverse the new compilations correctly? Likely needs a custom hierarchy declaration per variant.
- **Resource processing per variant** (Android handles this via `resValue` and `res/<flavor>`; KMP non-Android resources are simpler — but BuildConfig + resources need coordinated source-set hygiene).
- **Publish artifacts per variant**: today the plugin produces one JAR per target. v2.0 needs decisions on classifier-based publishing (`-freeDev`, `-paidProd`, …) vs. separate published modules.

## Estimate (from spike to v2.0 GA)

The plan's original estimate was "~6 weeks of plugin work + ~2 weeks of consumer migration." The spike doesn't change that estimate — the foundation is solid, but the surface area (codegen, hierarchy, tests, resources, publish) is real. The RFC and design phase is ~2 weeks; implementation is ~4 weeks of full-time work plus migration shim.

## Recommended next step

Write the v2.0 RFC, with this spike branch linked as the feasibility proof. A draft PR has been opened to keep this experiment discoverable: see PR opened from this branch.

The RFC should answer, in order:

1. **Naming** of additional compilations (`freeDevDesktop` vs `desktopFreeDev` vs `desktop/freeDev` — affects task names and IDE recognition).
2. **Source-set inheritance** strategy — direct `kotlin.srcDir()` vs registered `KotlinSourceSet` with `dependsOn` chains.
3. **BuildConfig generator changes** — multi-output task vs N single-output tasks vs one task with `@OutputDirectories` per variant.
4. **iOS / native** path — does each variant get its own framework binary? Compatible with cocoapods plugin's framework registration?
5. **Migration shim** — how does a v1.x consumer opt into v2.0 partial-matrix behavior incrementally?
6. **Publish story** — Maven coordinates per variant.

## Spike commit

This file + the `compilations { val freeDev by creating { … } }` block in `samples/basic-flavors/build.gradle.kts` are the entire spike. Both are isolated to `spike/d1-per-variant-compilation` — do not merge to `development`; this branch exists for reference only.
