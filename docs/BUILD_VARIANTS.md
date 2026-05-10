# Build Variants & CI/CD

> **Related:** [PRODUCT_FLAVORS](PRODUCT_FLAVORS.md) · [VARIANT_FILTERS](VARIANT_FILTERS.md) · [IOS_DISTRIBUTION](IOS_DISTRIBUTION.md) · [Integration guide for kmp-project-template](KMP_PROJECT_TEMPLATE_INTEGRATION.md)


## The rule: build type = environment = deployment target

| Build type | Active URL constant | Distribution |
|-----------|-------------------|-------------|
| `debug` | `API_URL_DEBUG` | Firebase App Distribution (dev) |
| `staging` | `API_URL_STAGING` | Firebase App Distribution (QA) |
| `release` | `API_URL_RELEASE` | App Store / Play Store |

No manual URL selection. Each binary has exactly one active server URL determined at compile time.

## CI/CD pipeline

```
git push origin develop   →  builds *Debug   →  Firebase dev channel
git push origin staging   →  builds *Staging →  Firebase QA channel
git tag / manual trigger  →  builds *Release →  App Store / Play Store
```

## Building locally

```bash
# List all variants
./gradlew listFlavors

# Build specific variant
./gradlew -PkmpVariant=internalAdvancedDebug assemble

# Split properties
./gradlew -PkmpFlavor=internalAdvanced -PkmpBuildType=debug assemble
```

Set defaults in `gradle.properties`:
```properties
kmpFlavor=internalAdvanced
kmpBuildType=debug
```

## Application IDs

| Consumer | Build type | Android applicationId |
|----------|-----------|----------------------|
| internal | debug | `com.example.app.debug` |
| internal | staging | `com.example.app.staging` |
| internal | release | `com.example.app` |
| demo | release | `com.example.app.demo` |
| clientA | release | `com.example.app.clienta` |

Clients publishing under their own store account override `applicationId` in their app module.
