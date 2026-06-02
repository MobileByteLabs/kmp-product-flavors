# Migration: v2.6 → v2.7

**You do not need to migrate.**

All v2.6 DSL surfaces are fully supported in v2.7. Version floor unchanged (Gradle 8.0+ / KGP 2.0.21+ / AGP 8.2+ / JDK 17+ / CMP 1.7.0+). This doc is a cookbook for what's new — not a required migration path.

---

## What v2.7 adds (opt-in to none required)

- **AGP 9.2.1 matrix-CI certification** — the plugin is now matrix-tested against AGP 8.2.2 / 8.5.2 / 8.10.0 / 9.2.1. If you bump your consumer build to AGP 9.x independently, this plugin works transparently. See [`AGP_9_MIGRATION_NOTES.md`](AGP_9_MIGRATION_NOTES.md) for the consumer-side AGP 9 cookbook.
- **Kotlin 2.3.21 build alignment** — the plugin is now built against Kotlin 2.3.21 (was 2.3.0). Consumers stay on whatever KGP version their build pins; the plugin's reflective bridge handles cross-version interop.
- **Line-coverage gate ramped 25 → 60** — affects contributors only. Empirical coverage doubled to **61.36%** (was 30.7% at v2.6 GA) on the back of **332 new tests across 36 new classes** + a sealed Kover exclusion list for genuinely-untestable Gradle Action SAM lambdas. The roadmap continues toward 100% across v2.7.x point releases. See [`COVERAGE_GUIDE.md`](COVERAGE_GUIDE.md) + [`COVERAGE_DEEP_DIVE.md`](COVERAGE_DEEP_DIVE.md) for the contributor playbook.
- **Snapshot fixture growth + new TestKit regression** — `NetworkDslRegressionTest` (new) plus 7 new `BuildKonfigCodegenSnapshotTest` `@Test` methods covering previously-uncovered network DSL emit branches.
- **36 new test classes** covering DSL config, codegen tasks, validators, AGP-reflection bridge, source-set resolution, secret resolution, feature-flag generators, and dimensions ergonomic sugar. Test count went **281 → 661** (+135%).

---

## DSL surfaces — UNCHANGED

Every v2.6 DSL surface stays identical in v2.7:

```kotlin
kmpFlavors {
    buildMatrix.set(true)
    buildConfigPackage.set("com.example.app")
    createInactiveFlavorSourceSets.set(false) // v2.6 Tier E.1 default

    dimensions {
        dimension("tier") { flavor("free") { isDefault.set(true) }; flavor("paid") }
        dimension("env")  { flavor("dev") { isDefault.set(true) }; flavor("prod") }
    }

    variantFilter {
        if (flavorNames.contains("free")) excludeTargets("watchosArm64", "tvosArm64")
    }

    buildKonfig {
        network {
            baseUrl(
                "free" to "https://api.free.example.com",
                "paid" to "https://api.paid.example.com",
            )
            timeout(seconds = 30)
        }
    }

    di {
        koin {
            variantModule("network") {
                "free" { single("FreeNetworkFactory()"); bind("NetworkFactory") }
                "paid" { single("PaidNetworkFactory()"); bind("NetworkFactory") }
            }
        }
    }

    analytics {
        enabled.set(true)
        customTag("environment") { variant ->
            variant.flavors.firstOrNull { it.name in listOf("dev", "prod") }?.name ?: "default"
        }
    }
}
```

Run unchanged against the v2.7 plugin and you'll get identical behaviour to v2.6.

---

## Validator codes — UNCHANGED

V01–V30 stay in place. No new validator codes ship in v2.7. The AGP 9.2.1 matrix CI verifies the existing validators against the new toolchain.

---

## Quality bonuses (no consumer-side changes required)

- **AGP matrix CI** validates the reflective bridge against AGP 8.2.2 / 8.5.2 / 8.10.0 / 9.2.1 on every PR
- **Coverage gate** ramped from floor 25 to floor 60; PRs that regress local coverage fail CI
- **Pitest mutation testing** baseline continues as PR artifact (informational; promoted to gate in v2.7.1+ per the v27-agp9-support GOAL)

These ship transparently; nothing to opt into.

---

## See also

- [`MIGRATION_v2.5_TO_v2.6.md`](MIGRATION_v2.5_TO_v2.6.md) — predecessor cookbook (also opens "You do not need to migrate.")
- [`AGP_9_MIGRATION_NOTES.md`](AGP_9_MIGRATION_NOTES.md) — consumer-side AGP 9 migration (independent of bumping this plugin)
- [`COMPATIBILITY_MATRIX.md`](COMPATIBILITY_MATRIX.md) — version floor + Built-against table
- [`COVERAGE_GUIDE.md`](COVERAGE_GUIDE.md) — gate definition (contributors only)
- [`COVERAGE_DEEP_DIVE.md`](COVERAGE_DEEP_DIVE.md) — contributor playbook for the three gap-closing patterns
