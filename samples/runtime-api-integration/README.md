# runtime-api-integration

Demonstrates `KmpFlavorsRuntime` consumption from `commonMain` — the v2.8 expect/actual codegen that provides platform-native flavor identity at runtime.

## Capability

- KMP project with Desktop + iOS targets
- `KmpFlavorsRuntime` expect/actual files generated to `build/generated/kmpflavors-runtime/`
- Runtime access to `flavorName`, `bundleId`, `appVersion`, `isDebug`, `isDemo` from commonMain
- `createIntermediateSourceSets` for `nativeMain` intermediate

## Run

```bash
# Compile Desktop (includes runtime-api actual for JAR Manifest reading)
./gradlew :samples:runtime-api-integration:compileKotlinDesktop

# Compile iOS Simulator
./gradlew :samples:runtime-api-integration:compileKotlinIosSimulatorArm64

# Switch to prod flavor
./gradlew :samples:runtime-api-integration:compileKotlinDesktop -PkmpFlavor=prod
```
