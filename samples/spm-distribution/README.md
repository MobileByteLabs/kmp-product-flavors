# spm-distribution — end-to-end Swift Package Manager

The proving ground for the v2.9 SPM path. `docs/IOS_DISTRIBUTION.md` promised this sample
from v1.1.1 and it was never shipped, which is why the generated manifests had nothing
demonstrating that they actually resolve.

It wires all three pieces a real iOS consumer needs:

| Piece | Where |
|---|---|
| XCFramework **producer** | `XCFramework("Shared")` in `build.gradle.kts` |
| Generated `Package.swift` | `build/spm/{variant}/Package.swift` |
| Generated Xcode Run-Script | `cmp-ios/scripts/embed-xcframework.sh` |

## Try it

```bash
# Generate the manifest — this also BUILDS the XCFramework it points at,
# because the manifest task depends on the producer.
./gradlew :samples:spm-distribution:generateFreeReleaseSpmManifest

# Generate the flavor-aware Xcode Run-Script.
./gradlew :samples:spm-distribution:generateSpmEmbedScript

# Validate the manifest with real Swift tooling (macOS).
(cd build/spm/freeRelease && swift package describe)
```

## What this sample demonstrates

**The manifest can never dangle.** Delete `build/XCFrameworks/` and run only the manifest
task — the XCFramework is rebuilt first. Before v2.9 the manifest task had no such
dependency and would happily emit a `binaryTarget(path:)` pointing at nothing, failing later
inside Xcode's SwiftPM resolution rather than in Gradle.

**Build-type mapping comes from the DSL, not a name glob.** This sample declares three build
types, and `staging` is deliberately **debuggable without being named `*Debug`**:

```kotlin
buildTypes {
    register("debug")   { isDebuggable.set(true) }
    register("staging") { isDebuggable.set(true) }   // ← the interesting one
    register("release") { isDebuggable.set(false) }
}
```

The generated script therefore contains:

```bash
case "$CONFIG" in
  freeDebug|freeStaging|paidDebug|paidStaging) KOTLIN_BUILD_TYPE="Debug" ;;
  freeRelease|paidRelease)                     KOTLIN_BUILD_TYPE="Release" ;;
```

A hand-written `*Debug` glob would send `freeStaging` to the **Release** slice. This mapping
is what the Kotlin CocoaPods plugin's `xcodeConfigurationToNativeBuildType[…]` block used to
own — the single non-trivial piece of a CocoaPods→SPM migration.

## No CocoaPods

There is none, and none is needed. SPM is the default and only supported path for
distributing the KMP framework itself. The separate, opt-in
[`iosIncludePodsXcconfig`](../../docs/REFERENCE.md#-iosincludepodsxcconfig) flag exists only
for hybrid apps that still use CocoaPods for *other* native SDKs.
