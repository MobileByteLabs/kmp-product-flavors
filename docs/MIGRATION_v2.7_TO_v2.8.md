# Migration Guide: v2.7 → v2.8

## Automated migration via `:kmpFlavorsMigrateFromV27`

v2.8 ships a migration task that handles the most common v2.7 → v2.8 transitions
automatically. Run it from your consumer project root:

```bash
# Dry-run — see what would change (no files modified)
./gradlew :kmpFlavorsMigrateFromV27

# Apply the changes
./gradlew :kmpFlavorsMigrateFromV27 --apply
```

The task detects and fixes:

1. **Deletes `cmp-android/.../AppFlavor.kt`** — the v2.7 pure-AGP workaround file.
   In v2.8, `AgpProductFlavorRegistrar` wires AGP product flavors automatically;
   the hand-written bridge is no longer needed.
2. **Rewrites `build-logic/convention/.../KMPFlavorsConventionPlugin.kt`** — removes
   the `fun configureFlavors(CommonExtension)` extension function that was needed
   to bridge v2.7's plugin limitations. v2.8 handles this internally.

Always review the diff with `git diff` before committing. If your project has
hand-customized any of the above files beyond v2.7's standard shape, the task
surfaces a warning and skips that file — follow the manual steps below.

---

## Manual migration steps

### 1. Delete `AppFlavor.kt`

Remove the v2.7 AGP workaround from every Android module that had it:

```
cmp-android/src/androidMain/kotlin/…/AppFlavor.kt   ← DELETE
```

### 2. Update `KMPFlavorsConventionPlugin.kt`

Remove the `configureFlavors(CommonExtension)` extension function and the
corresponding `withPlugin("com.android.application")` call that invoked it.
v2.8's `AgpProductFlavorRegistrar` auto-wires AGP product flavors from the
`kmpFlavors { flavors {} }` DSL.

Before (v2.7):

```kotlin
target.plugins.withId("com.android.application") {
    target.extensions.configure(CommonExtension::class.java) {
        configureFlavors(this)
    }
}

// v2.7 — pure-AGP workaround
private fun CommonExtension<*,*,*,*,*,*>.configureFlavors(…) {
    productFlavors {
        create("demo") { … }
        create("prod") { … }
    }
}
```

After (v2.8) — delete both the call site and the extension function entirely.

### 3. Upgrade `signingConfigs` (optional, v2.8 new feature)

v2.8 adds a first-class `signingConfigs {}` DSL block in `kmpFlavors {}`:

```kotlin
kmpFlavors {
    signingConfigs {
        register("release") {
            storeFile.set(file("release.keystore"))
            storePasswordFromEnv("STORE_PASSWORD")
            keyAlias.set("myKey")
            keyPasswordFromEnv("KEY_PASSWORD")
        }
    }
    flavors {
        register("prod") {
            signingConfig.set("release")
        }
    }
}
```

### 4. Use per-flavor `versionCode` / `versionName` (optional, v2.8 new feature)

```kotlin
kmpFlavors {
    flavors {
        register("demo") { versionCode.set(1); versionName.set("0.1.0-demo") }
        register("prod") { versionCode.set(100); versionName.set("2.8.0") }
    }
}
```

---

## Breaking changes

None. v2.8 is fully backwards compatible with v2.7 consumer DSL.
The `AppFlavor.kt` file and `configureFlavors` extension are optional to remove —
they continue to compile in v2.8. Removing them is a cleanup step, not a requirement.
