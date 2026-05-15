# Per-variant Compose hot-reload (v2.3 Phase 7)

> v2.3 ships **Option A** opt-in. Option B (daemon-restart-free variant switcher) remains deferred to v2.4 pending CMP-internal classloader API surface stabilisation. See `KmpFlavorExtension.composeHotReloadPerVariant` + the rationale in `internal/PerVariantComposeHotReloadConfigurator.kt`.

---

## TL;DR

```kotlin
kmpFlavors {
    flavors {
        register("free") { isDefault.set(true) }
        register("paid")
    }
    composeHotReloadPerVariant.set(true)   // Phase 7 opt-in
}
```

Then for each inactive variant × JVM-family target, a `composeHotReload{Variant}{Target}` task is registered:

```bash
./gradlew composeHotReloadPaidDesktop    # hot-reload the inactive 'paid' variant on Desktop
./gradlew composeHotReload               # hot-reload the active variant — CMP's default task, unchanged
```

The active variant continues to use the default `composeHotReload` task that CMP registers itself — no behaviour change for the active path. Inactive variants get their own dedicated hot-reload tasks scoped to the variant's compilation.

---

## What you get (Option A — shipped in v2.3)

- One `composeHotReload{Variant}{Target}` task per inactive variant × applicable target.
- Each task is wired to the variant's `KotlinCompilation` so editing a file in `src/commonPaid/` only triggers a hot-reload for the `paid` variant's task.
- The CMP hot-reload watcher daemon picks up changes within its compilation's source-set hierarchy. Variant cross-contamination is impossible — `commonFree` files don't trigger the `paid` watcher and vice versa.

## What you don't get (Option B — deferred)

- **Daemon-restart-free variant switching.** Switching the active variant via `-PkmpFlavor=paid` still requires restarting the daemon (or re-running `./gradlew composeApp:run`) before the new active variant's hot-reload picks up. CMP's hot-reload watcher caches the compilation's source-set hierarchy at start time + doesn't re-resolve on file changes.
- **A single hot-reload task that "follows" the active variant.** You explicitly invoke `composeHotReload{Variant}{Target}` for whichever inactive variant you want to watch. The default `composeHotReload` only watches the active variant.

The v2.4 roadmap will revisit Option B if CMP exposes a public hot-reload-per-compilation reset API. Until then, the pragmatic workflow is:

1. Run the **inactive variant's** hot-reload task during development (`composeHotReloadPaidDesktop`).
2. Switch to the **active path** (`composeHotReload`) only when you need to test variant resolution against the default `isDefault.set(true)` flavor.
3. Use v2.2's `./gradlew listActiveVariant` CLI helper + the IDE plugin's variant-switcher widget to switch active variants between development sessions.

---

## Compatibility matrix

| CMP version | Phase 7 task registration | Notes |
|---|---|---|
| 1.7.x | ✅ Tested | Per-flavor `composeResources/` auto-discovery floor; lowest Phase 7-supported version. |
| 1.8.x | ✅ Tested | |
| 1.9.x | ✅ Compatible | `withCompilation(…)` experimental API not used yet; tracked for v2.4. |
| 1.6.x and below | ❌ No-op | Hot-reload subsystem signature different; configurator silently skips. KMPF-V14 already WARNs on apply. |
| No CMP | ❌ No-op | `composeHotReloadPerVariant.set(true)` without `org.jetbrains.compose` is silent. |

The configurator uses reflective access to `org.jetbrains.compose.reload.gradle.HotReloadTask` to keep the kmp-product-flavors classpath free of a hard dependency on `org.jetbrains.compose:hot-reload-gradle-plugin`. If reflective registration fails on a future CMP version, the configurator logs at INFO level + falls back to a documented no-op rather than failing the build.

---

## Smoke-test workflow

For a 2-flavor × 2-buildType matrix on Desktop:

```bash
# 1. Apply matrix mode + opt into Phase 7.
echo 'kmpFlavors.composeHotReloadPerVariant = true' >> gradle.properties

# 2. Discover the registered tasks.
./gradlew tasks --group="kmp flavors" | grep composeHotReload
# Expected output (4 inactive variants × 1 desktop target = 4 tasks + the
# default composeHotReload for the active variant):
#   composeHotReload                  (default — active variant only)
#   composeHotReloadFreeReleaseDesktop
#   composeHotReloadPaidDebugDesktop
#   composeHotReloadPaidReleaseDesktop

# 3. Start hot-reload for the inactive paid-debug variant.
./gradlew composeHotReloadPaidDebugDesktop

# 4. Edit src/commonPaid/composeResources/values/strings.xml.
#    The watcher picks it up + reloads the paid variant's app instance.
#    src/commonFree/ edits are NOT picked up by this watcher — variant isolation
#    is enforced at the source-set-hierarchy level.
```

---

## Limitations + future work

- **Option B (daemon-restart-free switcher)** — deferred to v2.4 pending CMP-internal classloader API stabilisation. The CMP hot-reload watcher would need to re-resolve its compilation on `-PkmpFlavor` property changes.
- **JVM-family targets only** — Phase 7 registers tasks for `jvm()` / Desktop targets. iOS / Wasm / JS hot-reload integration via CMP is still experimental in the CMP project itself; once stable, v2.4+ may extend Phase 7 to those families.
- **No KSP-style incremental invalidation** — full hot-reload semantics apply (whole-compilation reload on change). Per-symbol incremental reload is a CMP-side concern.

For the full v2.3 plan + the v2.4 roadmap, see `plan-layer/plans/2026-05-14-kmp-product-flavors-v2.3-plan.md` Phase 7.

---

## When CMP ships the public hot-reload reset API

Both Option A and the v2.4 `switchVariantAndReload` Option B-workaround are tagged with the comment marker `CMP-API-WAITING` in the source. When JetBrains releases the public hot-reload reset API (tracked at https://github.com/MobileByteLabs/kmp-product-flavors/issues/75, replace with the real issue link once filed), grep for every occurrence:

```bash
grep -rn "CMP-API-WAITING" .
```

Each marker block contains the per-file migration checklist. The expected migration:

1. Replace `SwitchVariantAndReloadTask`'s body with a direct call to CMP's reset hook (no daemon stop required).
2. Extend `PerVariantComposeHotReloadConfigurator` with a daemon-restart-free Option B branch that's preferred when the reset API is detected at apply time (reflective `Class.forName` check).
3. Flip this doc's compatibility matrix: Option B "supported on CMP X.Y+" + the table's "Hot-reload is still active-variant only" caveat is removed.
4. Optionally flip `kmpFlavors.composeHotReloadPerVariant` convention from `false` → `true` for matrix-mode-enabled modules. Decision deferred until real-world cache-hit data + CMP reset-API stability survey.
5. Remove every `CMP-API-WAITING` marker after the migration ships.
