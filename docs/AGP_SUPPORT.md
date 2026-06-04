# AGP support contract

**Floor: AGP 9.2.1.** This library only supports Android Gradle Plugin **9.2.1 or higher**. Older AGP versions are explicitly **not supported** and **will not** be tested in CI.

## Rationale

AGP 9.x is the active stable line. AGP 9.2.1 ships:

- Stable `androidComponents.finalizeDsl` callback contract
- Stable `flavorDimensions` + `productFlavors` containers on `CommonExtension`
- `com.android.kotlin.multiplatform.library` plugin (formerly preview)
- New variant API surface for build-time hooks

Pinning to 9.2.1+ as the single supported floor lets the library:

- Use AGP DSL types directly where helpful (no reflection-erasure workarounds for type bounds)
- Drop the per-version compat matrix CI (one less workflow to maintain)
- Drop legacy fallback code paths for AGP < 8.2 that no consumer currently exercises
- Track AGP 9.x stable patches as they land without a deprecation graveyard

## What this means for consumers

- Bump consumer `gradle/libs.versions.toml`:
  ```toml
  agp = "9.2.1"  # or any 9.x stable patch
  ```
- Bump Gradle wrapper to **9.5.1 or higher** (AGP 9.2.1 requires Gradle 9.5.0 minimum)
- Use Kotlin **2.3.21 or higher** (KGP requirement aligned with AGP 9.x)

## What this library no longer does

- Test against AGP 8.2 / 8.5 / 8.10 (removed `agp-matrix-compat.yml`)
- Carry version-shim fallbacks for AGP < 9.x DSL surfaces
- Reflect over AGP types just to avoid a compile-time floor that 9.x makes safe

## Removed workflow

`.github/workflows/agp-matrix-compat.yml` was retired. The single-floor design replaces it.
