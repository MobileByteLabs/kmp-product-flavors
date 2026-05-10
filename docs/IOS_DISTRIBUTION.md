# iOS Distribution

> **Related:** [PRODUCT_FLAVORS](PRODUCT_FLAVORS.md) · [BUILD_VARIANTS](BUILD_VARIANTS.md) · [Integration guide for kmp-project-template](KMP_PROJECT_TEMPLATE_INTEGRATION.md)

`kmp-product-flavors` supports **Swift Package Manager (SPM) only** for iOS framework distribution. The plugin generates a per-flavor `Package.swift` manifest pointing at your XCFramework binary.

## Why SPM only — and not CocoaPods

CocoaPods is **deprecated** in JetBrains' KMP roadmap. The plugin will not gain CocoaPods integration — this is a deliberate scope decision, not an omission. If you need to ship to a CocoaPods-only consumer today, use a community wrapper or hand-write a `Podspec`; future versions of this plugin will not help with that.

SPM has been the default Apple-side package manager for new KMP integrations since Kotlin 2.0+, and is supported natively by Xcode and `swift package` tooling.

## Quick start

Enable manifest generation in your module's `build.gradle.kts`:

```kotlin
kmpFlavors {
    spm {
        generateManifest.set(true)
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

| Capability | Target |
|---|---|
| Per-flavor `Package.swift` | ✅ v1.1.0 |
| Sidecar checksum auto-load | ✅ v1.1.0 |
| Local-path + remote-URL distribution modes | ✅ v1.1.0 |
| Sample `samples/spm-distribution/` | 🟡 v1.1.1 follow-up |
| Per-flavor entitlements / Info.plist patching | 🟡 v1.2.0 (Phase N) |
| Unified manifest with conditional product targets | 🟡 v1.3.0+ |
| CocoaPods | ❌ never (deprecated by JetBrains) |
