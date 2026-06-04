# KMP ↔ AGP Variant Filter Parity

> **Since v2.6** — closes the v2.5 asymmetry where `kmpFlavors.variantFilter { exclude() }`
> pruned the KMP-side variant list but AGP received the full cross-product.

The plugin now forwards every `kmpFlavors.variantFilter { exclude() }` decision
to AGP via a `beforeVariants` callback. KMP and AGP agree on the final variant
set; `./gradlew tasks --all` no longer shows AGP-side variants that have no
KMP-side compilation behind them.

---

## The asymmetry (v2.5 and earlier)

`FlavorVariantResolver.applyVariantFilters()` (lines 106–127 of
`FlavorVariantResolver.kt`) prunes the KMP-side resolved variant list. But
`AgpBridge.propagateFlavors*()` only registered productFlavors +
flavorDimensions on the AGP DSL; AGP then cross-products them natively.
Effect:

| Stage           | Variant set                              |
|-----------------|------------------------------------------|
| KMP resolved    | filtered                                 |
| AGP resolved    | full cross-product (no filter)           |

Functionally fine — KMP source-set wiring keeps the build correct — but
confusing in tooling. `./gradlew tasks --all` exposes AGP variants the
consumer thought they'd excluded.

---

## The fix (v2.6)

`AgpBridge.propagateVariantFilterToAgp()` registers an AGP `beforeVariants`
callback that disables AGP variants whose names don't appear in the KMP-side
filtered set:

```
KmpFlavorPlugin.configurePlugin()
  ↓
  allVariants = FlavorVariantResolver.resolveAllVariants(...)   // KMP filter applied
  ↓
  AgpBridge.apply(..., allowedVariantNames = allVariants.map { it.name }.toSet())
  ↓
  finalizeDsl proxy fires:
     propagateFlavors(androidExt, ...)
     propagateBuildTypes(androidExt, ...)
  ↓
  propagateVariantFilterToAgp(components, allowedVariantNames, logger)
     ↓
     components.beforeVariants(null, action)  // null selector = all variants
        ↓
        AGP fires action(variantBuilder) per variant
           if (variantName !in allowedVariantNames) variantBuilder.setEnabled(false)
```

Both sides now agree on the final variant set.

---

## Variant-name-matching contract

For the bridge to disable correctly, KMP's `FlavorVariant.name`
([`buildVariantName()`](../build-logic/flavor-plugin/src/main/kotlin/com/mobilebytelabs/kmpflavors/internal/FlavorVariantResolver.kt))
MUST produce the same string as AGP's variant naming. Both use:

> `<flavor1><Flavor2><Flavor3>...<BuildType>?`

with lowercase-first + capitalize-subsequent. Verified by `AgpBridgeTest`:

| Test | Coverage |
|------|----------|
| `KMP buildVariantName matches AGP variant-name for 2D config` | `tier × env` → `freeDev`, `freeProd`, `paidDev`, `paidProd` |
| `KMP buildVariantName matches AGP variant-name for 3D config` | `tier × env × form` → 8 names, asserted byte-identical |

**Discipline:** if you introduce a new dimension type or a different variant-name
shape (e.g. composite-name flavors), **add a parity test** alongside the
existing two. Without that test the bridge silently disables the wrong
variants — failure mode is "build looks broken; KMP and AGP disagree".

---

## AGP version compatibility

| AGP version    | Status                                                 |
|----------------|--------------------------------------------------------|
| ≥ 8.0          | Full parity (`beforeVariants` + `setEnabled` stable)   |
| 7.0 – 7.4      | Unsupported — plugin floor is AGP 9.2.1 (see [`AGP_SUPPORT.md`](AGP_SUPPORT.md)) |
| < 7.0          | `beforeVariants` not available → bridge logs WARN, no-op |

The bridge uses reflection (`Class.getMethods` lookup) for both
`beforeVariants(selector, action)` and `VariantBuilder.setEnabled(boolean)`.
A future AGP version that renames either degrades to WARN, not throw — the
build still succeeds, only parity becomes unenforced. CI matrix
`agp-matrix-compat.yml` runs the bridge against AGP 8.0.2, 8.5.2, 8.10.0,
9.0.0-rc01 on every PR that touches AgpBridge.

---

## Testing pattern

`MockAndroidComponentsExtension` (test-fixture, mirrors Phase 1's
`MockAndroidExtension`) captures the registered `Action<VariantBuilder>` and
fires it against `MockVariantBuilder` instances. A second mock
`MockVariantBuilderMissingSetEnabled` omits the `setEnabled` setter to
exercise the WARN fallback branch.

```kotlin
val components = MockAndroidComponentsExtension()
AgpBridge.propagateVariantFilterToAgp(components, setOf("freeDebug"), logger)

listOf("freeDebug", "freeRelease", "paidDebug", "paidRelease")
    .map { MockVariantBuilder(it) }
    .onEach { components.fireRegisteredAction(it) }
    .filter { it.enabled }
    // → [freeDebug]
```

---

## Out of scope

- **Forwarding build-type-only filters.** Build types don't have a KMP-side
  variant filter mechanism today; only flavor combinations do.
- **Modifying applicationId / signing per variant.** AGP's native DSL covers
  these surfaces; not the plugin's job.
- **Removing the legacy `propagateFlavors` 1-dim fast path.** Still
  byte-identical v2.4.3 behavior — deferred to v3.0 with codemod.

---

## See also

- `build-logic/flavor-plugin/src/main/kotlin/com/mobilebytelabs/kmpflavors/internal/AgpBridge.kt`
  — `apply()` + `propagateVariantFilterToAgp()`
- `build-logic/flavor-plugin/src/test/kotlin/com/mobilebytelabs/kmpflavors/AgpBridgeTest.kt`
  — 5 Phase 2 tests (3 forwarding + 1 WARN fallback + 2 parity)
- `.github/workflows/agp-matrix-compat.yml` — multi-AGP CI gate
- `docs/COVERAGE_GUIDE.md` — Tier A coverage gate
- `plan-layer/.../v26-stability-parity-beyond-platform/02-agp-parity.md` — originating epic plan
