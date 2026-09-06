/*
 * Copyright 2026 MobileByteLabs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.mobilebytelabs.kmpflavors.internal

/**
 * v2.9 — resolves which task in the consumer's build actually PRODUCES the XCFramework
 * that a generated `Package.swift` points at.
 *
 * ## Why this exists
 *
 * Up to v2.8 `generateSpmManifest` had exactly one wiring edge — `assemble` depended on
 * *it* — and none in the other direction. So `./gradlew assemble` would happily write a
 * manifest whose `binaryTarget(path:)` referenced `build/XCFrameworks/<variant>/<name>.xcframework`
 * even when nothing in the build ever produced that directory. SwiftPM then fails at
 * *resolution* time inside Xcode, far from the Gradle build that caused it.
 *
 * ## Why we resolve instead of registering our own
 *
 * The plugin deliberately does NOT create its own `XCFramework()` aggregator on the
 * consumption path. Real consumers (e.g. `kmp-project-template`'s `cmp-shared`) already
 * declare KGP's own `XCFramework("ComposeApp")` DSL; registering a second aggregator for
 * the same framework name would double-link the binaries and can collide on task names.
 * Instead we locate the producer the consumer already has, depend on it, and derive the
 * manifest path from its conventional output location.
 *
 * ## Naming contract
 *
 * KGP's `XCFramework(name)` DSL registers:
 *   - `assemble{Name}{BuildType}XCFramework` — one per native build type (debug/release)
 *   - `assemble{Name}XCFramework`            — the umbrella over all build types
 *
 * and this plugin's own per-variant publishing path ([PerVariantIosXcframeworkConfigurator])
 * may additionally register `assemble{Name}{Variant}XCFramework`. Resolution therefore runs
 * most-specific-first: variant → build type → umbrella.
 */
internal object SpmXcframeworkResolver {

    /** KGP's conventional output root for XCFramework aggregation. */
    private const val XCFRAMEWORK_OUTPUT_ROOT: String = "build/XCFrameworks"

    /**
     * Kotlin target names that imply an Apple/iOS consumer. Used to gate SPM registration
     * so the v2.9 default-on flip adds no tasks to Android-only / JVM-only projects.
     */
    val IOS_TARGET_NAMES: Set<String> = setOf(
        "iosX64",
        "iosArm64",
        "iosSimulatorArm64",
        "iosArm32",
    )

    /**
     * Candidate producer task names for [variantName], ordered most-specific first.
     *
     * [buildTypeName] may be null for flavor-only variants (no build-type dimension), in
     * which case the build-type candidate is omitted rather than guessed.
     */
    fun candidateTaskNames(xcframeworkName: String, variantName: String, buildTypeName: String?): List<String> = buildList {
        add("assemble$xcframeworkName${variantName.capitalizeAscii()}XCFramework")
        if (!buildTypeName.isNullOrBlank()) {
            add("assemble$xcframeworkName${buildTypeName.capitalizeAscii()}XCFramework")
        }
        add("assemble${xcframeworkName}XCFramework")
    }.distinct()

    /**
     * The first of [candidateTaskNames] present in [existingTaskNames], or `null` when the
     * build registers no XCFramework producer at all — the caller then fails loudly with
     * [missingProducerMessage] rather than emitting a dangling manifest.
     */
    fun resolveProducer(xcframeworkName: String, variantName: String, buildTypeName: String?, existingTaskNames: Set<String>): String? =
        candidateTaskNames(xcframeworkName, variantName, buildTypeName)
            .firstOrNull { it in existingTaskNames }

    /**
     * Path from a generated manifest to the XCFramework its `binaryTarget` references.
     *
     * Returned RELATIVE (`../../XCFrameworks/...`), because the manifest is written to
     * `<module>/build/spm/<variant>/Package.swift` and the aggregate lands in
     * `<module>/build/XCFrameworks/<nativeBuildType>/`. An absolute path would bake the
     * author's home directory into a file that gets committed and consumed on CI.
     *
     * [nativeBuildType] is Kotlin's build type (`debug` / `release`) — NOT the flavor's
     * build-type name. KGP only ever emits those two buckets, so a build type called
     * `staging` that is `isDebuggable` resolves to `debug`.
     */
    fun conventionalOutputPath(xcframeworkName: String, nativeBuildType: String?): String {
        val bucket = nativeBuildType?.takeIf { it.isNotBlank() }?.lowercase() ?: "release"
        return "../../XCFrameworks/$bucket/$xcframeworkName.xcframework"
    }

    /**
     * Kotlin's `NativeBuildType` name for a flavor build type: debuggable → `debug`,
     * everything else (staging, release, …) → `release`.
     */
    fun nativeBuildTypeFor(debuggable: Boolean): String = if (debuggable) "debug" else "release"

    /**
     * Actionable diagnostic for a consumer whose build declares no XCFramework producer.
     * Names the exact DSL to add and the escape hatch, so the failure is self-servicing.
     */
    fun missingProducerMessage(xcframeworkName: String, variantName: String): String = buildString {
        append("[KMP Flavors] SPM manifest for variant '$variantName' would reference an ")
        append("XCFramework that nothing in this build produces.\n")
        append("  Declare KGP's aggregator in the module that exports the framework:\n")
        append("      kotlin {\n")
        append("          val xcf = XCFramework(\"$xcframeworkName\")\n")
        append("          listOf(iosArm64(), iosSimulatorArm64()).forEach { t ->\n")
        append("              t.binaries.framework { baseName = \"$xcframeworkName\"; xcf.add(this) }\n")
        append("          }\n")
        append("      }\n")
        append("  Or point at an existing producer with `spm.xcframeworkTask.set(\"<taskName>\")`,\n")
        append("  or opt out of the check with `spm.requireXcframework.set(false)` when the\n")
        append("  binary is produced outside this Gradle build (e.g. fetched from a CDN).")
    }

    private fun String.capitalizeAscii(): String = replaceFirstChar { if (it.isLowerCase()) it.uppercaseChar() else it }
}
