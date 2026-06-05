# pure-agp-app

Demonstrates the KMP Product Flavors plugin applied to a pure `com.android.library` project — no Kotlin Multiplatform plugin required.

## Capability

Shows that `kmpFlavors {}` works on plain Android modules:
- `flavorDimensions {}` + `flavors {}` DSL
- Per-flavor `versionCode` / `versionName` (v2.8 new feature)
- `applicationIdSuffix` per flavor

## Run

```bash
# List configured flavors
./gradlew :samples:pure-agp-app:listFlavors

# Validate flavor configuration
./gradlew :samples:pure-agp-app:validateFlavors
```
