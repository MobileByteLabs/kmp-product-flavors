# dsl-completeness-integration

"Kitchen sink" v2.8 sample — exercises every public DSL surface added through v2.8.

## Capability

- Multi-dimensional flavors: `tier` (free, premium) × `env` (demo, prod)
- `signingConfigs {}` DSL block (v2.8 Wave A1)
- Per-flavor `versionCode` / `versionName` (v2.8 Wave A1)
- `buildConfigField` — String, Boolean, Int
- `applicationIdSuffix`
- `createIntermediateSourceSets`

## Flavor Variants

| tier \ env | demo (default) | prod |
|---|---|---|
| **free** (default) | freeDemо | freeProd |
| **premium** | premiumDemo | premiumProd |

## Run

```bash
# List all flavors and active variant
./gradlew :samples:dsl-completeness-integration:listFlavors

# Compile Desktop (default variant: freeDemo)
./gradlew :samples:dsl-completeness-integration:compileKotlinDesktop

# Compile with prod signing config + versionCode 2800
./gradlew :samples:dsl-completeness-integration:compileKotlinDesktop -PkmpFlavor=premiumProd
```

## DSL Features Demonstrated

### `signingConfigs {}`

```kotlin
signingConfigs {
    register("release") {
        storeFile.set(file("release.keystore"))
        storePasswordFromEnv("STORE_PASSWORD")     // env-var intake
        keyAlias.set("releaseKey")
        keyPasswordFromEnv("KEY_PASSWORD")
    }
}
```

### Per-flavor version properties

```kotlin
register("demo") {
    versionCode.set(1)
    versionName.set("2.8.0-demo")
}
register("prod") {
    signingConfig.set("release")   // reference by name
    versionCode.set(2800)
    versionName.set("2.8.0")
}
```
