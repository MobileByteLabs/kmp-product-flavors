# Product Flavors

> **Related:** [BUILD_VARIANTS](BUILD_VARIANTS.md) · [Integration guide for kmp-project-template](KMP_PROJECT_TEMPLATE_INTEGRATION.md)


The plugin supports N flavor dimensions. Each dimension is an independent axis of variation — the active variant is the cartesian product of all dimensions plus a build type.

## Recommended design for SaaS / white-label KMP apps

| Dimension | Values | Controls |
|-----------|--------|---------|
| `consumer` | `internal`, `demo`, `clientA`, `clientB` | Server URLs compiled in, URL override flag |
| `tier` | `advanced` (default), `basic` | Feature flags — absent from binary when off |
| Build type | `debug`, `staging`, `release` | Active URL, signing, dev screens |

**consumer** — who is this build for?
- `internal`: your own app published to your stores
- `demo`: prospects evaluating the product — `ALLOW_URL_OVERRIDE=true` so they can point it at their own server
- `clientA/B/...`: white-label builds for each bank/enterprise client — their server URLs compiled in

**tier** — what feature set is purchased?
- `advanced`: all features enabled (analytics, reports, bulk ops)
- `basic`: features excluded from binary via `commonBasic/` source set (not just hidden — absent)

**buildType** — which environment does this binary target?
- `debug` → dev URL, `ALLOW_ENV_SWITCH=true`, dev settings screen compiled in
- `staging` → staging URL, no env switch, no dev screen
- `release` → prod URL, minified, no dev screen

## Variant matrix

4 consumers × 2 tiers × 3 build types = **24 variants**

Of these, only ~6 ship to stores:

| Variant | Store |
|---------|-------|
| `internalAdvancedRelease` | Your App Store / Play Store |
| `demoAdvancedRelease` | Your App Store / Play Store |
| `clientABasicRelease` | Client A's store account |
| `clientAAdvancedRelease` | Client A's store account |
| `clientBBasicRelease` | Client B's store account |
| `clientBAdvancedRelease` | Client B's store account |

## Source set wiring

The plugin registers flavor-specific source sets automatically. For `clientAAdvancedDebug`:

```
commonMain
├── commonClientA/     ← Bank A URLs, no URL override
├── commonAdvanced/    ← analytics / reports / bulk ops compiled in
└── commonDebug/       ← DevSettingsScreen compiled in
```

## Adding a new client

1. Register a new `consumer` flavor in your convention plugin
2. Create `commonClientC/` source set with actual implementations
3. Sync — `IS_CLIENT_C`, all URL constants, and the source set chain are wired automatically

## Per-flavor compose resources

Compose Multiplatform's `composeResources/` directory is inherited from a source set's parent chain. Drop flavor-specific assets under the appropriate source set:

```
src/
├── commonMain/
│   └── composeResources/
│       └── drawable/
│           └── logo.png            ← shared default
├── commonDemo/
│   └── composeResources/
│       └── drawable/
│           └── logo.png            ← overrides the default in demo builds
└── commonProd/
    └── composeResources/
        └── drawable/
            └── logo.png            ← overrides the default in prod builds
```

When the `demo` flavor is active, Compose's `Res.drawable.logo` resolves to `commonDemo/composeResources/drawable/logo.png`. When `prod` is active, it resolves to `commonProd/...`. The plugin's `dependsOn` wiring (only the active variant's flavor source sets are on the compile path) ensures **only one resource entry per logical name** ever ships in the final binary — there's no runtime selection cost and no name collision.

Same pattern works for nested directories (`drawable-night/`, `font/`, `string/`) and for platform-flavor combinations (e.g. `androidDemo/composeResources/` for Android-only demo overrides).

## Per-flavor tests

When the consumer module declares any test compilation (i.e. `commonTest` exists), the plugin also creates per-flavor *Test* source sets mirroring the *Main* hierarchy:

```
src/
├── commonTest/
│   └── kotlin/                    ← shared test infrastructure
├── commonDemoTest/
│   └── kotlin/                    ← tests / fixtures specific to the demo flavor
├── commonProdTest/
│   └── kotlin/
├── iosDemoTest/
│   └── kotlin/                    ← iOS-only tests for the demo flavor
└── androidDemoTest/
    └── kotlin/
```

`<flavor>Test` source sets `dependsOn(commonTest)` only when the flavor is active, so non-active flavor test sources never reach the test classpath of another variant. This mirrors the production-code wiring rules.
