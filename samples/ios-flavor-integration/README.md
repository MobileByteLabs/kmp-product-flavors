# ios-flavor-integration

Demonstrates per-flavor iOS xcconfig generation and `KmpFlavorsRuntime` iOS actual wiring.

## Capability

- KMP project with iOS targets (iosX64, iosArm64, iosSimulatorArm64)
- Per-flavor `BuildKonfig` (API_URL + IS_DEMO constants)
- `createIntermediateSourceSets` for `nativeMain` intermediate source set
- `:kmpFlavorsXcodeIntegrate` task generates xcconfig files for Xcode build

## Run

```bash
# List flavors
./gradlew :samples:ios-flavor-integration:listFlavors

# Compile for iOS Simulator
./gradlew :samples:ios-flavor-integration:compileKotlinIosSimulatorArm64

# Generate xcconfig (requires iosApp/ directory)
./gradlew :samples:ios-flavor-integration:kmpFlavorsXcodeIntegrate
```
