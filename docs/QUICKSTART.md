# Quickstart — kmp-product-flavors

> 5-minute setup. From `plugins { … }` declaration to first variant compilation.

This guide gets you to a working flavor-aware KMP build with the minimum DSL. For full reference + advanced features, see [`REFERENCE.md`](REFERENCE.md).

---

## 1. Apply the plugin

```kotlin
// build.gradle.kts
plugins {
    kotlin("multiplatform")
    id("io.github.mobilebytelabs.kmp-product-flavors") version "2.4.0-alpha.0"
}
```

Or pin via `gradle.properties` if you use a version catalog:

```toml
# gradle/libs.versions.toml
[versions]
kmpProductFlavors = "2.4.0-alpha.0"

[plugins]
kmp-product-flavors = { id = "io.github.mobilebytelabs.kmp-product-flavors", version.ref = "kmpProductFlavors" }
```

---

## 2. Declare flavors + build types

```kotlin
// build.gradle.kts
kmpFlavors {
    flavors {
        register("free") { isDefault.set(true) }
        register("paid")
    }
    buildTypes {
        register("debug") { isDefault.set(true) }
        register("release")
    }
}
```

That's a 4-variant matrix: `freeDebug`, `freeRelease`, `paidDebug`, `paidRelease`. The `isDefault` flags mark which variant compiles via the standard `compileKotlin*` tasks (Active variant: `freeDebug`).

---

## 3. Compile + run

```bash
./gradlew compileKotlinDesktop          # active variant only (freeDebug)
./gradlew assembleAllVariants            # every variant on every target
./gradlew listFlavors                    # show resolved matrix
./gradlew listActiveVariant              # show active + switch instructions
```

Switch the active variant from the CLI:

```bash
./gradlew compileKotlinDesktop -PkmpFlavor=paidRelease
```

---

## 4. Per-flavor source files

Code that should only be visible to one flavor goes in `src/common{Flavor}/`:

```
src/
├── commonMain/kotlin/MyClass.kt          # visible to ALL variants
├── commonFree/kotlin/AdManager.kt        # visible to free* variants only
└── commonPaid/kotlin/PremiumFeatures.kt  # visible to paid* variants only
```

Cross-variant isolation is enforced: `commonPaid` code can't reference `commonFree` symbols and vice-versa.

---

## 5. BuildConfig per flavor (optional)

```kotlin
kmpFlavors {
    generateBuildConfig.set(true)
    buildConfigPackage.set("com.example.app")
    flavors {
        register("free") {
            isDefault.set(true)
            buildConfigField("Int", "MAX_ITEMS", "10")
            buildConfigField("String", "TIER_NAME", "\"free\"")
        }
        register("paid") {
            buildConfigField("Int", "MAX_ITEMS", "1000")
            buildConfigField("String", "TIER_NAME", "\"paid\"")
        }
    }
}
```

Then in your Kotlin code:

```kotlin
val maxItems = BuildKonfig.MAX_ITEMS    // 10 in free*, 1000 in paid*
```

Auto-generated flavor flags also land in `BuildKonfig`:

```kotlin
BuildKonfig.IS_FREE       // true in free* variants
BuildKonfig.IS_PAID       // true in paid* variants
BuildKonfig.VARIANT_NAME  // "freeDebug" / "paidRelease" / etc.
```

**Naming gotcha**: don't name custom fields the same as auto-derived `IS_<FLAVOR>` / `IS_<BUILDTYPE>` constants. The codegen produces a duplicate `const val` → Kotlin compile fails. Future plugin version surfaces this as **KMPF-V23** at apply time; until then, use a different prefix for custom fields (`MAX_*`, `TIER_*`, `PREMIUM_*`).

---

## 6. Common next steps

| Want to… | Read |
|---|---|
| Build every variant in one go | [`MATRIX_MODE.md`](MATRIX_MODE.md) — set `kmpFlavors.buildMatrix.set(true)`. |
| Publish per-variant Maven artefacts | [`PUBLISHING.md`](PUBLISHING.md) — set `kmpFlavors.publishMatrix.set(true)`. |
| Pin against unreleased HEAD | [`PUBLISHING.md` "Snapshot channel"](PUBLISHING.md#snapshot-channel-v23) — nightly snapshot publish. |
| Strip dep from one variant only | [`VARIANT_DEPENDENCY_EXCLUDES.md`](VARIANT_DEPENDENCY_EXCLUDES.md) — `variants.matching { … }.configureEach { dependencies { exclude(...) } }`. |
| Run per-target Detekt | [`MATRIX_MODE.md` "detektPerVariant"](MATRIX_MODE.md). |
| Switch variants without restart | [`COMPOSE_HOT_RELOAD.md`](COMPOSE_HOT_RELOAD.md) + `./gradlew switchVariantAndReload --to=<variant>`. |

---

## 7. Troubleshooting

Any warning starting with `KMPF-V…` is from the plugin's structured validator. Full catalogue: [`ERROR_CODES.md`](ERROR_CODES.md). Common ones:

| Code | Means | Fix |
|---|---|---|
| `KMPF-V01` | Flavor + build-type name collision | Rename one. Convention: flavor names are nouns, build types are adjectives. |
| `KMPF-V05` | Matrix mode enabled but no KMP targets | Add a `kotlin { jvm() / iosArm64() / … }` target. |
| `KMPF-V08` | Matrix mode enabled but no flavors registered | Add flavors or remove `buildMatrix.set(true)`. |
| `KMPF-V20` | `variantCacheNamespacing=true` but `buildMatrix=false` | Set `buildMatrix.set(true)` or leave cache namespacing off. |
| `KMPF-V22` | Variant `exclude(group="", module="")` (both empty) | Pass at least one coordinate. |

If you hit a regression that isn't covered, file an issue at https://github.com/MobileByteLabs/kmp-product-flavors/issues with the `KMPF-V<code>` (if any) + your build.gradle.kts `kmpFlavors { }` block.
