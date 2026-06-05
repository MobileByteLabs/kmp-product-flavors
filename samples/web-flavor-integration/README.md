# web-flavor-integration

Demonstrates per-flavor Webpack DefinePlugin constant injection for JS and WasmJs targets.

## Capability

- KMP project with JS (IR) + WasmJs targets
- Per-flavor `BuildKonfig` (API_BASE, ENABLE_DEBUG_TOOLS)
- Webpack DefinePlugin constants injected as `__KMPF_*__` globals

## Run

```bash
# Compile JS
./gradlew :samples:web-flavor-integration:compileKotlinJs

# Compile WasmJs
./gradlew :samples:web-flavor-integration:compileKotlinWasmJs

# Build with production flavor
./gradlew :samples:web-flavor-integration:compileKotlinJs -PkmpFlavor=production
```
