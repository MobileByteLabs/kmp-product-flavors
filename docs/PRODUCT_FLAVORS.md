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
