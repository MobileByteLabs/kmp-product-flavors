# Build Variants & CI/CD

How variants are built automatically and deployed to the right destination.

## The Rule: build type = environment = deployment target

```
debug   →  dev server     →  Firebase App Distribution  (dev channel)
staging →  staging server →  Firebase App Distribution  (QA channel)
release →  prod server    →  App Store / Play Store
```

No manual URL selection. No shared builds across environments. Each binary has exactly one server URL active.

## CI/CD Pipeline

### Trigger → Build → Deploy

```
git push origin develop
  └── builds: *AdvancedDebug (internal, demo, clientA, clientB)
      └── deploys to Firebase App Distribution — dev testers

git push origin staging
  └── builds: *AdvancedStaging (internal, demo, clientA, clientB)
      └── deploys to Firebase App Distribution — QA testers

git tag v1.x.x  OR  manual workflow dispatch
  └── builds: *Release (all consumer × tier combinations)
      └── deploys to App Store / Play Store
```

### GitHub Actions matrix

```yaml
# .github/workflows/build.yml (excerpt)
strategy:
  matrix:
    include:
      # Dev builds — all consumers, advanced tier only
      - variant: internalAdvancedDebug
        deploy-to: firebase-dev
      - variant: demoAdvancedDebug
        deploy-to: firebase-dev
      - variant: clientAAdvancedDebug
        deploy-to: firebase-clienta-dev
      - variant: clientBAdvancedDebug
        deploy-to: firebase-clientb-dev

      # Staging builds — all consumers, advanced tier only
      - variant: internalAdvancedStaging
        deploy-to: firebase-staging
      - variant: demoAdvancedStaging
        deploy-to: firebase-staging
      - variant: clientAAdvancedStaging
        deploy-to: firebase-clienta-staging
      - variant: clientBAdvancedStaging
        deploy-to: firebase-clientb-staging

      # Release builds — publish to stores
      - variant: internalAdvancedRelease
        deploy-to: playstore-internal
      - variant: demoAdvancedRelease
        deploy-to: playstore-demo
      - variant: clientABasicRelease
        deploy-to: playstore-clienta
      - variant: clientAAdvancedRelease
        deploy-to: playstore-clienta
      - variant: clientBBasicRelease
        deploy-to: playstore-clientb
      - variant: clientBAdvancedRelease
        deploy-to: playstore-clientb
```

## Building Locally

Build a specific variant using Gradle variant selection:

```bash
# Full variant name
./gradlew -PkmpVariant=internalAdvancedDebug assembleInternalAdvancedDebug

# Split properties
./gradlew -PkmpFlavor=internalAdvanced -PkmpBuildType=debug assembleInternalAdvancedDebug

# iOS
./gradlew -PkmpVariant=clientAAdvancedRelease linkReleaseFrameworkIosArm64

# List all available variants
./gradlew listFlavors
```

## Application IDs

Each variant gets a unique application ID from suffix stacking:

| Consumer | Build type | Android applicationId |
|----------|-----------|----------------------|
| internal | debug | `org.mifos.kmp.template.debug` |
| internal | staging | `org.mifos.kmp.template.staging` |
| internal | release | `org.mifos.kmp.template` |
| demo | debug | `org.mifos.kmp.template.demo.debug` |
| demo | release | `org.mifos.kmp.template.demo` |
| clientA | debug | `org.mifos.kmp.template.clienta.debug` |
| clientA | release | `org.mifos.kmp.template.clienta` |
| clientB | release | `org.mifos.kmp.template.clientb` |

Clients publishing under their own Play Store account override the base `applicationId` in their app-level `build.gradle.kts`:

```kotlin
android {
    defaultConfig {
        applicationId = "com.banka.mobileapp"  // client's own package name
    }
}
```

## Adding a Staging Firebase App

Each consumer needs a Firebase project per environment for distribution:

```
internal → firebase-project-internal-dev      (debug builds)
         → firebase-project-internal-staging  (staging builds)
demo     → firebase-project-demo-dev
         → firebase-project-demo-staging
clientA  → firebase-project-clienta-dev
         → firebase-project-clienta-staging
```

Store each `google-services.json` under its variant's resource directory:

```
cmp-android/src/
├── internalAdvancedDebug/google-services.json
├── internalAdvancedStaging/google-services.json
├── demoAdvancedDebug/google-services.json
└── clientAAdvancedDebug/google-services.json
```

## iOS Bundle IDs

iOS bundle IDs follow the same stacking pattern:

| Consumer | Build type | Bundle ID |
|----------|-----------|-----------|
| internal | debug | `org.mifos.kmp.template.debug` |
| internal | release | `org.mifos.kmp.template` |
| demo | release | `org.mifos.kmp.template.demo` |
| clientA | release | `org.mifos.kmp.template.clienta` |

Each distinct bundle ID requires a separate App Store Connect app record and provisioning profile.
