# Rollback Strategy

> What to do if a `kmp-product-flavors` release regresses your build.

---

## TL;DR

| Current pin | If broken, roll back to | Why |
|---|---|---|
| `1.1.5` | `1.1.0` | Last fully public release before v1.1.5. `1.1.1`–`1.1.4` were development iterations published only to mavenLocal, never to Maven Central / Gradle Plugin Portal. |
| `1.1.0` | `1.0.x` (latest public 1.0 line) | Tagged releases on Central. |
| `1.0.x` | Pin to a specific verified version | See [Maven Central listing](https://repo1.maven.org/maven2/io/github/mobilebytelabs/kmpflavors/flavor-plugin/) for available versions. |

The fastest recovery is **always: pin to a known-good version + open a regression issue**.

---

## Available versions on Maven Central

```bash
curl -sL https://repo1.maven.org/maven2/io/github/mobilebytelabs/kmpflavors/flavor-plugin/maven-metadata.xml \
  | xmllint --xpath '//versions/version/text()' -
```

Or browse:
- Maven Central: <https://repo1.maven.org/maven2/io/github/mobilebytelabs/kmpflavors/flavor-plugin/>
- Gradle Plugin Portal: <https://plugins.gradle.org/plugin/io.github.mobilebytelabs.kmp-product-flavors>

---

## How to roll back

### 1. Update your version pin

In `gradle/libs.versions.toml`:

```toml
[versions]
kmpProductFlavors = "1.1.0"   # was "1.1.5"
```

### 2. Restore workarounds if the older version needs them

v1.1.0 didn't have v1.1.5's zero-config defaults. If you previously deleted these lines during the v1.1.5 adoption, **add them back temporarily**:

```kotlin
extensions.configure<KmpFlavorExtension> {
    buildConfigPackage.set(...)
    buildConfigClassName.set("FlavorConfig")    // v1.1.0 default
    enableBuildTypes.set(true)
    bridgeAgpProductFlavors.set(false)          // bridge not idempotent in v1.1.0
    bridgeAgpBuildTypes.set(false)
    // ... your flavors and buildTypes
}
```

In `gradle.properties` you may need to re-add:

```properties
kotlin.suppressGradlePluginWarnings=UnusedSourceSetsWarning
```

The CHANGELOG entry for v1.1.5 has the full migration list — reverse those steps to roll back.

### 3. Verify the build

```bash
./gradlew :cmp-android:assembleDemoDebug
```

### 4. Open a regression issue

Title: `[regression] v1.1.x → v1.1.5 broke …`

Include:
- The Gradle output (especially the first FAILURE block + Caused by chain).
- Your `kmpFlavors { }` extension config from `KMPFlavorsConventionPlugin.kt`.
- Whether you use `codegenHost.set(true)` and where.
- Your AGP + Kotlin Gradle Plugin versions.
- The output of `./gradlew :flavor-plugin:dependencies --configuration kotlinCompileClasspathMain` from your consumer (helps diagnose KLIB resolver conflicts).

---

## What's NOT a regression

Some warnings are noisy by design and don't justify a rollback:

| Warning | Status | Justification |
|---|---|---|
| `KLIB resolver: The same 'unique_name=annotation_commonMain' found in more than one library` | Cosmetic | Comes from Compose Multiplatform's dependency graph, NOT this plugin. See `plan-layer/plans/2026-05-12-kmp-product-flavors-followups.md` Phase B6. |
| `Unused Kotlin Source Sets` for inactive flavors | Expected pre-v1.1.5 | v1.1.5 lazy creation eliminates this. If still seeing it on v1.1.5, file a bug. |
| `Redundant dependsOn Kotlin Source Sets` (webMain) | Expected pre-v1.1.5 | v1.1.5 delegates web wiring to Kotlin 2.1+ hierarchy template. If still seeing it on v1.1.5, file a bug. |
| `'annotation class Preview : Annotation' is deprecated` | Compose 1.10 deprecation, not flavor-plugin | The deprecation message is misleading — the `org.jetbrains.compose.ui.tooling.preview.Preview` import IS the canonical cross-platform shim for now. JetBrains will expose the androidx-prefixed Preview cross-platform in a future Compose release. |

Filing a "regression" against the plugin for any of the above will be closed as not-a-bug. Take them up with JetBrains Compose roadmap instead.

---

## Why v1.1.1–v1.1.4 aren't on Central

During the v1.1.5 release cycle (2026-05-11/12), patch numbers 1.1.1 through 1.1.4 were used as **mavenLocal-only iteration markers** — each was a `:flavor-plugin:publishToMavenLocal` build to validate plugin changes against the consumer template before a single coherent release to Central as `1.1.5`.

If you accidentally pinned to one of those during development, switch to:

- `1.1.5` (recommended — has all the v1.1.5 improvements)
- `1.1.0` (rollback — pre-v1.1.5 baseline)

---

## Catastrophic rollback (Central is having a bad day)

If Maven Central is down or has stale metadata, you can pin to a specific commit of the plugin source via composite includeBuild:

```kotlin
// build-logic/settings.gradle.kts
pluginManagement {
    val upstreamFlavorPluginBuildLogic = file("../../../../../mbs/kmp-product-flavors/source/kmp-product-flavors/build-logic")
    if (upstreamFlavorPluginBuildLogic.exists()) {
        includeBuild(upstreamFlavorPluginBuildLogic) {
            name = "kmp-product-flavors-build-logic"
        }
    }
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}
```

Then check out the tag of the plugin source you want to use:

```bash
cd ../../mbs/kmp-product-flavors
git checkout v1.1.0   # or whatever known-good tag
```

This is the same setup the plugin source repo uses to dogfood itself, so it's a tested path.

---

## See also

- [`CHANGELOG.md`](../CHANGELOG.md) — what each version added/changed
- [openMF/kmp-project-template#141](https://github.com/openMF/kmp-project-template/pull/141) — canonical v1.1.5 adoption reference
- `plan-layer/plans/2026-05-12-kmp-product-flavors-followups.md` (claude-product-cycle framework) — Phase B canary rollout order for downstream apps
