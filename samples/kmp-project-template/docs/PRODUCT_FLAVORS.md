# Product Flavors

This document explains the flavor dimensions, what each one controls, and how to extend the matrix.

## Dimensions

The app is configured with two flavor dimensions plus a build type:

```
consumer × tier × buildType
```

### `consumer` — Who is this build for?

Controls the **server URLs** and **URL override capability** compiled into the binary.

| Flavor | App ID Suffix | API_URL_RELEASE | ALLOW_URL_OVERRIDE | Use |
|--------|--------------|-----------------|-------------------|-----|
| `internal` | (none) | `api.yourdomain.com` | false | Your own app on stores |
| `demo` | `.demo` | `demo.yourdomain.com` | **true** | Prospects evaluating the product |
| `clientA` | `.clienta` | `api.banka.com` | false | White-label for Bank A |
| `clientB` | `.clientb` | `api.bankb.com` | false | White-label for Bank B |

Each consumer flavor compiles **three** URL constants: `API_URL_DEBUG`, `API_URL_STAGING`, `API_URL_RELEASE`. The active URL is selected by the build type (see below).

### `tier` — What feature set?

Controls which features are compiled into the binary. Features not in a tier are **absent from the binary entirely**, not just hidden.

| Flavor | CLIENT_TIER | Analytics | Reports | Bulk Operations |
|--------|-------------|-----------|---------|-----------------|
| `advanced` | `"advanced"` | ✅ | ✅ | ✅ |
| `basic` | `"basic"` | ✗ | ✗ | ✗ |

`advanced` is the default. The feature source code lives in `commonAdvanced/` and `commonBasic/` source sets via `FeatureFlags` expect/actual.

### Build Types — How is it compiled?

Controls the **active server URL**, signing, minification, and distribution target.

| Build Type | Active URL | Debuggable | Minified | ALLOW_ENV_SWITCH | Distribution |
|-----------|-----------|-----------|---------|-----------------|-------------|
| `debug` | `API_URL_DEBUG` | ✅ | ✗ | **true** | Firebase (dev) |
| `staging` | `API_URL_STAGING` | ✗ | ✗ | false | Firebase (QA) |
| `release` | `API_URL_RELEASE` | ✗ | ✅ | false | App Store / Play Store |

`ALLOW_ENV_SWITCH` in debug builds enables a developer settings screen (compile-time inclusion) that lets your dev/QA team override the active URL at runtime. This screen does not exist in staging or release binaries.

## Variant Matrix

4 consumers × 2 tiers × 3 build types = **24 variants**

```
internalAdvancedDebug      internalAdvancedStaging      internalAdvancedRelease  ← your app
internalBasicDebug         internalBasicStaging         internalBasicRelease
demoAdvancedDebug          demoAdvancedStaging          demoAdvancedRelease      ← demo app
demoBasicDebug             demoBasicStaging             demoBasicRelease
clientAAdvancedDebug       clientAAdvancedStaging       clientAAdvancedRelease   ← Bank A
clientABasicDebug          clientABasicStaging          clientABasicRelease
clientBAdvancedDebug       clientBAdvancedStaging       clientBAdvancedRelease   ← Bank B
clientBBasicDebug          clientBBasicStaging          clientBBasicRelease
```

### What actually ships to stores

| Variant | Store | Who downloads it |
|---------|-------|-----------------|
| `internalAdvancedRelease` | Your Play Store / App Store | Your end users |
| `demoAdvancedRelease` | Your Play Store / App Store | Prospects evaluating |
| `clientABasicRelease` | Bank A's store account | Bank A customers (basic) |
| `clientAAdvancedRelease` | Bank A's store account | Bank A customers (advanced) |
| `clientBBasicRelease` | Bank B's store account | Bank B customers (basic) |
| `clientBAdvancedRelease` | Bank B's store account | Bank B customers (advanced) |

All `*Debug` and `*Staging` variants are for internal development/QA — never published.

## Source Set Hierarchy

The plugin wires source sets automatically. For a variant like `clientAAdvancedDebug`:

```
commonMain
├── commonClientA/          ← Bank A URLs, no URL override
├── commonAdvanced/         ← analytics, reports, bulk ops compiled in
└── commonDebug/            ← DevSettingsScreen compiled in, env switch enabled
    └── commonClientAAdvancedDebug/   ← variant-specific overrides if needed
```

### Source set files

```
cmp-shared/src/
├── commonMain/kotlin/cmp/shared/flavor/
│   ├── AppVariant.kt          ← typed accessors for all FlavorConfig constants
│   ├── ContentRepository.kt   ← expect: data source abstraction
│   └── FeatureFlags.kt        ← expect: tier feature flags
├── commonInternal/kotlin/cmp/shared/flavor/
│   └── ContentRepository.kt   ← actual: remote-api, no URL override
├── commonDemo/kotlin/cmp/shared/flavor/
│   └── ContentRepository.kt   ← actual: demo-remote, allowsServerUrlOverride=true
├── commonClientA/kotlin/cmp/shared/flavor/
│   └── ContentRepository.kt   ← actual: remote-api for Bank A
├── commonClientB/kotlin/cmp/shared/flavor/
│   └── ContentRepository.kt   ← actual: remote-api for Bank B
├── commonAdvanced/kotlin/cmp/shared/flavor/
│   └── FeatureFlags.kt        ← actual: all features true
└── commonBasic/kotlin/cmp/shared/flavor/
    └── FeatureFlags.kt        ← actual: all features false
```

## Accessing Flavor Config in Code

```kotlin
import cmp.shared.flavor.AppVariant

// Active server URL (resolves debug/staging/release automatically)
val url = AppVariant.activeApiUrl

// Gate a feature at runtime (backed by compile-time constant)
if (AppVariant.featureAnalytics) {
    // analytics screen is only compiled in for advanced tier
}

// Show URL override UI in demo consumer only
if (AppVariant.allowUrlOverride) {
    // ServerUrlScreen — only compiled into commonDemo source set
}

// Conditional logging
if (AppVariant.enableLogging) {
    println("[${AppVariant.logTag}] Request sent to ${AppVariant.activeApiUrl}")
}
```

## Adding a New Client

1. Register the new flavor in `KMPFlavorsConventionPlugin.kt`:

```kotlin
register("clientC") {
    dimension.set("consumer")
    applicationIdSuffix.set(".clientc")
    bundleIdSuffix.set(".clientc")
    buildConfigField("String", "CLIENT_ID", "\"clientC\"")
    buildConfigField("String", "API_URL_DEBUG",   "\"https://dev.bankc.com\"")
    buildConfigField("String", "API_URL_STAGING", "\"https://staging.bankc.com\"")
    buildConfigField("String", "API_URL_RELEASE", "\"https://api.bankc.com\"")
    buildConfigField("Boolean", "ALLOW_URL_OVERRIDE", "false")
}
```

2. Create its source set:

```
cmp-shared/src/commonClientC/kotlin/cmp/shared/flavor/ContentRepository.kt
```

3. That's it. The plugin generates `commonClientC/` in the source set hierarchy and creates `IS_CLIENT_C`, `CLIENT_ID`, and URL constants in `FlavorConfig` automatically.
