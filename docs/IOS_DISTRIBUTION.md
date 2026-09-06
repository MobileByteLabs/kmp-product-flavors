# iOS Distribution

> **Related:** [PRODUCT_FLAVORS](PRODUCT_FLAVORS.md) · [BUILD_VARIANTS](BUILD_VARIANTS.md) · [Integration guide for kmp-project-template](KMP_PROJECT_TEMPLATE_INTEGRATION.md)

`kmp-product-flavors` uses **Swift Package Manager (SPM)** for iOS framework distribution, **on by default since v2.9**. The plugin generates, per flavor variant:

1. `Package.swift` — a manifest whose `binaryTarget` points at your XCFramework
2. `embed-xcframework.sh` — the Xcode Run-Script that assembles the XCFramework for the flavor being built and stages the SDK-matching slice

Both are wired to the XCFramework producer in your build, so a manifest can never reference a binary nothing produces.

## Why SPM — and what CocoaPods support does and does not mean

CocoaPods is **deprecated** in JetBrains' KMP roadmap. The plugin will not gain CocoaPods integration for **framework distribution** — that is a deliberate scope decision. If you need to ship to a CocoaPods-only consumer, use a community wrapper or hand-write a `Podspec`.

That is separate from [`iosIncludePodsXcconfig`](REFERENCE.md#-iosincludepodsxcconfig) (opt-in, default `false`), which emits one optional Pods xcconfig `#include?` for **hybrid brownfield apps** — apps that take the KMP framework via SPM but still use CocoaPods for other native SDKs. It applies no CocoaPods plugin, writes no podspec, and runs no `pod install`.

SPM has been the default Apple-side package manager for new KMP integrations since Kotlin 2.0+, and is supported natively by Xcode and `swift package` tooling.

## Quick start

SPM generation is on by default for any module with an iOS target. You only need a
**producer** — KGP's `XCFramework()` aggregator — plus your flavors:

```kotlin
kotlin {
    val xcf = XCFramework("Shared")
    listOf(iosArm64(), iosSimulatorArm64()).forEach { t ->
        t.binaries.framework { baseName = "Shared"; xcf.add(this) }
    }
}

kmpFlavors {
    spm {
        // generateManifest defaults to `true` since v2.9 — shown for clarity only.
        xcframeworkName.set("Shared")        // matches your KMP framework's baseName
        distribution.set(SpmDistribution.LOCAL)
    }
    flavorDimensions {
        register("tier") { priority.set(0) }
    }
    flavors {
        register("free") { dimension.set("tier"); isDefault.set(true) }
        register("paid") { dimension.set("tier") }
    }
}
```

Build:

```bash
./gradlew assemble
# → writes build/spm/free/Package.swift  (active flavor manifest)
```

The generator runs as a side-effect of `:assemble` (lifecycle hook) and writes one `Package.swift` per active variant under `build/spm/<variant>/`.

## Distribution modes

### `LOCAL` — path-based (development workflow)

Default. Manifest references the XCFramework via a relative path:

```swift
.binaryTarget(
    name: "SharedFree",
    path: "../../XCFrameworks/free/Shared.xcframework"
)
```

Override the path with `xcframeworkPath`. The default convention is
`../../XCFrameworks/<variant>/<xcframeworkName>.xcframework` relative to the generated manifest.

### `REMOTE` — URL + checksum (production distribution)

Use when shipping to external consumers via CDN / S3 / GitHub Releases:

```kotlin
spm {
    generateManifest.set(true)
    xcframeworkName.set("Shared")
    distribution.set(SpmDistribution.REMOTE)
    binaryUrlTemplate.set("https://cdn.example.com/{flavor}/{version}/Shared.xcframework.zip")
    checksumStrategy.set(SpmChecksumStrategy.AUTO)
}
```

Placeholders in `binaryUrlTemplate`:

| Placeholder | Replaced by |
|---|---|
| `{flavor}` | active flavor name (e.g. `free`) |
| `{variant}` | full active variant name (e.g. `freeProdRelease`) |
| `{version}` | `project.version` at task time |

## Checksum strategies

`SpmChecksumStrategy` controls how the SHA-256 in the `Package.swift` is sourced:

| Strategy | Behaviour |
|---|---|
| `AUTO` *(default)* | Reads `<xcframeworkPath>.checksum` if present; otherwise computes SHA-256 from the local XCFramework if available; otherwise emits a `<TODO-checksum>` placeholder that fails downstream `swift package` validation. |
| `REQUIRE_FILE` | Only uses the sidecar `.checksum` file. Task fails loudly if missing. Recommended for release pipelines. |
| `SKIP` | Emits a `<SKIP-checksum>` placeholder. Useful for samples / CI smoke tests where the binary is not actually fetched. |

Generate a sidecar checksum file via Apple's tooling on macOS:

```bash
swift package compute-checksum build/XCFrameworks/freeProdRelease/Shared.xcframework > \
  build/XCFrameworks/freeProdRelease/Shared.xcframework.checksum
```

## Per-flavor manifest design (decision D6)

Each flavor variant gets its own `Package.swift`. This is intentional:

- **Smaller download** — Swift consumers only fetch the binary they're going to use.
- **Cleaner dependency tree** — no Swift-side `#if compiled` branching to pick a target.
- **Simpler URL templating** — `{flavor}` resolves to a single value at manifest-write time.

If you need a unified manifest with conditional product targets (one `Package.swift` that exposes `Shared-Free`, `Shared-Paid`, etc.), it is roadmapped for `v1.3.0+`. Until then, consumers can compose by adding multiple package dependencies in their own `Package.swift`.

## Linux-CI compatibility

Manifest generation runs in pure JVM — no `swift` binary required. Tests use string-match assertions on the generated text. macOS-only `swift package describe` validation runs as a separate sample-build step in CI on `macos-latest` runners.

## Roadmap

| Capability | Status |
|---|---|
| Per-flavor `Package.swift` | ✅ v1.1.0 |
| Sidecar checksum auto-load | ✅ v1.1.0 |
| Local-path + remote-URL distribution modes | ✅ v1.1.0 |
| Sample `samples/spm-distribution/` | ✅ v2.9 |
| XCFramework producer wiring (`dependsOn` + path resolution) | ✅ v2.9 |
| Generated flavor-aware Xcode embed script | ✅ v2.9 |
| SPM on by default | ✅ v2.9 |
| Per-flavor entitlements / Info.plist patching | 🟡 planned |
| Unified manifest with conditional product targets | 🟡 planned |
| CocoaPods for framework distribution | ❌ never (deprecated by JetBrains) |
| Pods xcconfig passthrough for hybrid apps | ✅ opt-in, default off |

## End-to-end wiring (v2.9)

Before v2.9 `generateSpmManifest` had no dependency on any XCFramework build, so `assemble`
could emit a `Package.swift` whose `binaryTarget` path did not exist — surfacing later as a
confusing SwiftPM *resolution* error inside Xcode. The plugin now resolves the producer your
build already registers, most-specific first:

```
assemble{Name}{Variant}XCFramework   →   assemble{Name}{BuildType}XCFramework   →   assemble{Name}XCFramework
```

and makes the manifest depend on it, deriving the `binaryTarget` path from that producer's
output. Controls:

| Property | Default | Purpose |
|---|---|---|
| `spm.xcframeworkTask` | *(auto)* | Pin a non-conventional producer task name |
| `spm.requireXcframework` | `true` | Skip generation (with a warning) rather than emit a dangling manifest |
| `spm.generateEmbedScript` | `true` | Also generate the Xcode Run-Script |
| `spm.embedScriptPath` | `<iosDir>/scripts/embed-xcframework.sh` | Where to write it |

Modules with an iOS target but no XCFramework producer (e.g. libraries publishing klibs) are
**not** broken by the default-on flip: generation is skipped with an actionable warning.

### Build-type mapping

The generated embed script maps each Xcode configuration to a Kotlin `NativeBuildType` using
the **declared `isDebuggable` flag**, not a name glob. A build type named `staging` with
`isDebuggable = true` therefore selects the `debug` slice — which a `*Debug` glob gets wrong.
This is the piece the Kotlin CocoaPods plugin's `xcodeConfigurationToNativeBuildType[…]` block
used to own, and the single non-trivial part of a CocoaPods→SPM migration.
