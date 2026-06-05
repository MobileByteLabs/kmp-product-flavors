# desktop-flavor-integration

Demonstrates per-flavor Compose Desktop `nativeDistributions` wiring and JAR Manifest entry injection.

## Capability

- KMP project with Desktop (JVM) target
- Per-flavor `versionCode` / `versionName` (v2.8 new feature)
- Per-flavor `BuildKonfig` constants (UPDATE_URL, IS_PRO)
- JAR Manifest entries injected with `KMPF-Flavor`, `KMPF-Version` etc.

## Run

```bash
# Compile
./gradlew :samples:desktop-flavor-integration:compileKotlinDesktop

# Build with pro flavor
./gradlew :samples:desktop-flavor-integration:compileKotlinDesktop -PkmpFlavor=pro

# List flavors
./gradlew :samples:desktop-flavor-integration:listFlavors
```
