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

import com.mobilebytelabs.kmpflavors.BuildTypeConfig
import com.mobilebytelabs.kmpflavors.FlavorConfig
import com.mobilebytelabs.kmpflavors.FlavorDimension
import com.mobilebytelabs.kmpflavors.FlavorVariant

/**
 * Severity of a validation finding.
 */
internal enum class KmpFlavorValidationSeverity { ERROR, WARNING }

/**
 * A single structured validation finding.
 *
 * Per RFC §3 Q23, every invalid configuration produces a finding with
 * a stable error code (`KMPF-Vxx`), human-readable message, and a
 * concrete fix suggestion. The full catalog ships in `docs/ERROR_CODES.md`
 * (v2.0 W5 deliverable).
 *
 * Codes are part of the public API surface. Once shipped at a given
 * version, the same code MUST keep the same meaning so that consumer
 * tooling (CI grep, IDE quick-fix integrations, error-aggregation
 * dashboards) doesn't break across minor versions.
 */
internal data class KmpFlavorValidationFinding(val code: String, val severity: KmpFlavorValidationSeverity, val message: String, val fix: String)

/**
 * v2.0 fail-fast configuration validator (RFC §3 Q23).
 *
 * Pure function over inputs — no [org.gradle.api.Project] dependency,
 * so the validator is trivially unit-testable and free of
 * configuration-cache concerns.
 *
 * `KMPF-Vxx` code catalog (W1 ships 4 codes; W2-W5 extend):
 *
 * | Code | Severity | Meaning |
 * |---|---|---|
 * | KMPF-V01 | ERROR | Flavor name collides with build-type name |
 * | KMPF-V02 | ERROR | Flavor declared without dimension when dimensions are registered |
 * | KMPF-V03 | ERROR | Dimension has no flavors assigned |
 * | KMPF-V04 | ERROR | Variant filter excluded ALL variants (no buildable variant remains) |
 * | KMPF-V05 | WARNING | Matrix mode opted in but zero KMP targets detected |
 * | KMPF-V06 | WARNING | Unknown active variant (`-PkmpFlavor=ghost` when ghost isn't a flavor) — soft-fall (project-wide property) |
 * | KMPF-V07 | ERROR | `buildConfigField` has an invalid Kotlin literal type |
 * | KMPF-V08 | ERROR | Matrix mode opted in but no flavors are registered |
 */
internal object KmpFlavorPluginValidator {

    const val CODE_FLAVOR_BUILD_TYPE_COLLISION: String = "KMPF-V01"
    const val CODE_FLAVOR_MISSING_DIMENSION: String = "KMPF-V02"
    const val CODE_DIMENSION_HAS_NO_FLAVORS: String = "KMPF-V03"
    const val CODE_VARIANT_FILTER_EXCLUDED_ALL: String = "KMPF-V04"
    const val CODE_ZERO_KMP_TARGETS: String = "KMPF-V05"
    const val CODE_UNKNOWN_ACTIVE_VARIANT: String = "KMPF-V06"
    const val CODE_INVALID_BUILD_CONFIG_FIELD_TYPE: String = "KMPF-V07"
    const val CODE_MATRIX_MODE_WITHOUT_FLAVORS: String = "KMPF-V08"
    const val CODE_CMP_COMPOSE_RESOURCES_VERSION_INCOMPATIBLE: String = "KMPF-V14"
    const val CODE_IOS_ROSETTA_REQUIRED: String = "KMPF-V15"
    const val CODE_CMP_KGP_VERSION_INCOMPATIBLE: String = "KMPF-V16"
    const val CODE_KGP_GRADLE_VERSION_INCOMPATIBLE: String = "KMPF-V17"

    /**
     * Supported Kotlin literal types for `buildConfigField`. Other types
     * (custom classes, generics, nullable wrappers) require runtime
     * serialization the plugin doesn't perform.
     */
    val SUPPORTED_BUILD_CONFIG_FIELD_TYPES: Set<String> = setOf("Boolean", "Int", "Long", "Float", "Double", "String")

    /**
     * Validate the resolved plugin configuration and return all findings.
     *
     * Returns an empty list when the configuration is valid. Callers
     * (e.g. `KmpFlavorPlugin.apply()`) decide whether to fail the build
     * or surface findings as Gradle warnings; the validator itself is
     * side-effect-free.
     */
    fun validate(
        flavors: List<FlavorConfig>,
        buildTypes: List<BuildTypeConfig>,
        resolvedVariants: List<FlavorVariant>,
        matrixModeEnabled: Boolean,
        detectedTargetCount: Int,
        dimensions: List<FlavorDimension> = emptyList(),
        requestedVariantName: String? = null,
    ): List<KmpFlavorValidationFinding> {
        val findings = mutableListOf<KmpFlavorValidationFinding>()

        // KMPF-V01: flavor + build-type name collision (variant names become ambiguous)
        val buildTypeNames = buildTypes.map { it.name }.toSet()
        flavors.filter { it.name in buildTypeNames }.forEach { collidingFlavor ->
            findings += KmpFlavorValidationFinding(
                code = CODE_FLAVOR_BUILD_TYPE_COLLISION,
                severity = KmpFlavorValidationSeverity.ERROR,
                message = "Flavor '${collidingFlavor.name}' has the same name as a build type. " +
                    "Variant names become ambiguous when this happens (the plugin can't tell " +
                    "whether `freeDebug` is `free × Debug` or `freeDebug × <unset>`).",
                fix = "Rename either the flavor or the build type so they no longer collide. " +
                    "Convention: flavor names are nouns ('free', 'paid', 'enterprise'); build " +
                    "type names are adjectives ('debug', 'release', 'staging').",
            )
        }

        // KMPF-V02: flavor declared without `dimension.set(...)` when dimensions are registered.
        // Mixed dimension/no-dimension flavors are ambiguous — every flavor must specify which
        // dimension it belongs to so the cartesian product is well-defined.
        if (dimensions.isNotEmpty()) {
            flavors.filter { it.dimension.orNull.isNullOrBlank() }.forEach { flavor ->
                findings += KmpFlavorValidationFinding(
                    code = CODE_FLAVOR_MISSING_DIMENSION,
                    severity = KmpFlavorValidationSeverity.ERROR,
                    message = "Flavor '${flavor.name}' is declared without a `dimension.set(...)` " +
                        "call, but ${dimensions.size} dimension(s) are registered " +
                        "(${dimensions.joinToString { it.name }}). Mixed dimension/no-dimension " +
                        "flavors are ambiguous — every flavor must specify which dimension it " +
                        "belongs to.",
                    fix = "Set the dimension on '${flavor.name}': `kmpFlavors { flavors { " +
                        "register(\"${flavor.name}\") { dimension.set(\"<dimensionName>\") } } }`. " +
                        "Or remove all dimensions if you want single-dimension semantics.",
                )
            }
        }

        // KMPF-V03: dimension has no flavors assigned — the dimension can never produce a
        // variant. Previously thrown by FlavorVariantResolver as IllegalStateException; v2.1
        // routes it through the structured validator so the message catalogue stays consistent.
        val dimensionsMissingFlavors = dimensions.filter { dim ->
            flavors.none { it.dimension.orNull == dim.name }
        }
        dimensionsMissingFlavors.forEach { dim ->
            findings += KmpFlavorValidationFinding(
                code = CODE_DIMENSION_HAS_NO_FLAVORS,
                severity = KmpFlavorValidationSeverity.ERROR,
                message = "Dimension '${dim.name}' has no flavors assigned to it. " +
                    "The dimension can never produce a variant.",
                fix = "Either assign at least one flavor to '${dim.name}' " +
                    "(`kmpFlavors { flavors { register(\"...\") { dimension.set(\"${dim.name}\") } } }`), " +
                    "or remove the empty dimension from `flavorDimensions { }`.",
            )
        }

        // KMPF-V07: `buildConfigField` declared with a type the codegen can't emit as a
        // Kotlin `const val`. Supported types are Boolean / Int / Long / Float / Double /
        // String. Other types either require runtime serialization or can't be `const`.
        flavors.forEach { flavor ->
            flavor.buildConfigFields.get().values.forEach { field ->
                if (field.type !in SUPPORTED_BUILD_CONFIG_FIELD_TYPES) {
                    findings += KmpFlavorValidationFinding(
                        code = CODE_INVALID_BUILD_CONFIG_FIELD_TYPE,
                        severity = KmpFlavorValidationSeverity.ERROR,
                        message = "Flavor '${flavor.name}' declares `buildConfigField` " +
                            "'${field.name}' with type '${field.type}', which is not a " +
                            "supported Kotlin literal type. Supported: " +
                            "${SUPPORTED_BUILD_CONFIG_FIELD_TYPES.sorted().joinToString(", ")}.",
                        fix = "Pick one of the supported types, or stringify the value: " +
                            "`buildConfigField(\"String\", \"${field.name}\", \"\\\"<value>\\\"\")`.",
                    )
                }
            }
        }

        // KMPF-V08: matrix mode opted in without any flavors
        if (matrixModeEnabled && flavors.isEmpty()) {
            findings += KmpFlavorValidationFinding(
                code = CODE_MATRIX_MODE_WITHOUT_FLAVORS,
                severity = KmpFlavorValidationSeverity.ERROR,
                message = "kmpFlavors.buildMatrix is enabled but no flavors are registered. " +
                    "Matrix mode requires at least one flavor to generate compilations from.",
                fix = "Either register flavors via `kmpFlavors { flavors { register(\"…\") } }` " +
                    "in the convention plugin, or remove the `buildMatrix.set(true)` / " +
                    "`gradle.properties: kmpFlavors.buildMatrix=true` opt-in.",
            )
        }

        // KMPF-V04: variantFilter excluded every variant — nothing buildable remains.
        // Gated against V03: if a dimension has no flavors, the resolver legitimately produces
        // an empty matrix and V03 is the more specific finding; suppress V04 in that case.
        if (flavors.isNotEmpty() && resolvedVariants.isEmpty() && dimensionsMissingFlavors.isEmpty()) {
            findings += KmpFlavorValidationFinding(
                code = CODE_VARIANT_FILTER_EXCLUDED_ALL,
                severity = KmpFlavorValidationSeverity.ERROR,
                message = "Variant filter excluded every variant — no buildable variant " +
                    "remains. With ${flavors.size} flavor(s) and ${buildTypes.size} build " +
                    "type(s) declared, the matrix should not be empty.",
                fix = "Relax the `variantFilter { }` predicate or remove it. Run " +
                    "`./gradlew :listFlavors` once the filter is fixed to verify the matrix.",
            )
        }

        // KMPF-V05: matrix mode opted in but zero KMP targets — registrar has nothing to do
        if (matrixModeEnabled && detectedTargetCount == 0) {
            findings += KmpFlavorValidationFinding(
                code = CODE_ZERO_KMP_TARGETS,
                severity = KmpFlavorValidationSeverity.WARNING,
                message = "kmpFlavors.buildMatrix is enabled but no non-Android KMP " +
                    "targets are declared. Matrix mode has nothing to register; this is a " +
                    "no-op (warning, not error — likely a configuration ordering issue).",
                fix = "Add a non-Android KMP target (`jvm()`, `iosX64()`, `js(IR)`, " +
                    "`wasmJs()`, etc.) to `kotlin { }`, or remove the buildMatrix opt-in. " +
                    "If you ARE declaring targets but they're being filtered out — note that " +
                    "the synthetic `metadata` target and the Android JVM target are " +
                    "deliberately excluded from matrix mode.",
            )
        }

        // KMPF-V06: `-PkmpFlavor=<name>` references a variant the resolver doesn't know.
        // WARNING (not ERROR) because the property is project-wide: in multi-project builds
        // sibling projects with their own variant matrix legitimately won't recognise the
        // value. Soft-fall to the default variant — V06 surfaces the mismatch without
        // breaking the whole build.
        if (!requestedVariantName.isNullOrBlank() &&
            resolvedVariants.none { it.name.equals(requestedVariantName, ignoreCase = true) }
        ) {
            findings += KmpFlavorValidationFinding(
                code = CODE_UNKNOWN_ACTIVE_VARIANT,
                severity = KmpFlavorValidationSeverity.WARNING,
                message = "-PkmpFlavor=$requestedVariantName references variant " +
                    "'$requestedVariantName', which isn't a registered combination. " +
                    "Registered variants: [${resolvedVariants.joinToString { it.name }}]. " +
                    "Falling back to the default variant.",
                fix = "Pick a registered variant from the list (case-insensitive) OR omit " +
                    "`-PkmpFlavor` to let the plugin resolve from `isDefault` flags. If the " +
                    "property is intentional for a sibling project in a multi-project build, " +
                    "this warning is informational and can be ignored for this project.",
            )
        }

        return findings
    }

    /**
     * v2.2 Phase 0I + 0L — platform + version compatibility checks.
     *
     * Runs alongside the main [validate] call but with broader inputs (host OS arch,
     * KGP version, Gradle version, CMP version). Emits structured findings instead of
     * letting consumers debug raw `xcodebuild`/`compileKotlin` errors.
     *
     * Codes:
     *   - V15: Apple Silicon host (`aarch64`/`arm64`) + iOS targets that historically
     *     need Rosetta for the `iosX64` simulator (notably under Kotlin/Native pre-2.0
     *     toolchains). Emits the recommended `arch -x86_64 ./gradlew` workaround.
     *   - V16: known-bad combinations of Compose Multiplatform + KGP versions.
     *     Currently: CMP < 1.7.0 with KGP 2.2+ silently no-ops `composeResources/`
     *     auto-discovery on custom source sets.
     *   - V17: known-bad combinations of KGP + Gradle versions. Currently: KGP 2.0.x
     *     with Gradle 8.0–8.4 has unstable Hierarchy Template.
     */
    fun validatePlatformAndVersionCompatibility(
        hostOsArch: String,
        gradleVersion: String,
        kgpVersion: String?,
        cmpVersion: String?,
        declaredIosTargetNames: Set<String>,
    ): List<KmpFlavorValidationFinding> {
        val findings = mutableListOf<KmpFlavorValidationFinding>()

        // V15: Apple Silicon + iosX64 simulator → may need Rosetta on older Kotlin/Native.
        if ((hostOsArch == "aarch64" || hostOsArch == "arm64") &&
            "iosX64" in declaredIosTargetNames
        ) {
            findings += KmpFlavorValidationFinding(
                code = CODE_IOS_ROSETTA_REQUIRED,
                severity = KmpFlavorValidationSeverity.WARNING,
                message = "Apple Silicon host (`$hostOsArch`) is declaring an iosX64 target. " +
                    "Some Kotlin/Native toolchain versions need Rosetta to assemble the " +
                    "iosX64 simulator framework on M-series hardware. If `xcodebuild` " +
                    "fails with arch-mismatch errors, retry the Gradle build under Rosetta.",
                fix = "Either drop the `iosX64()` target (M-series simulators use " +
                    "`iosSimulatorArm64()`), OR run the Gradle build under Rosetta: " +
                    "`arch -x86_64 ./gradlew :module:assembleAllVariants`. " +
                    "Document the toolchain matrix in docs/PUBLISHING.md for your project.",
            )
        }

        // V16: CMP × KGP compatibility. Currently a single known-bad combo:
        // CMP < 1.7.0 + KGP >= 2.2 silently breaks composeResources auto-discovery on
        // custom source sets.
        if (cmpVersion != null && kgpVersion != null) {
            if (versionLessThan(cmpVersion, "1.7.0") && !versionLessThan(kgpVersion, "2.2.0")) {
                findings += KmpFlavorValidationFinding(
                    code = CODE_CMP_KGP_VERSION_INCOMPATIBLE,
                    severity = KmpFlavorValidationSeverity.WARNING,
                    message = "Known-incompatible combination: Compose Multiplatform " +
                        "`$cmpVersion` + Kotlin Gradle Plugin `$kgpVersion`. Per-variant " +
                        "`composeResources/` auto-discovery on custom source sets " +
                        "(commonFree, commonPaid, etc.) silently no-ops on this pairing.",
                    fix = "Upgrade `org.jetbrains.compose` to `>= 1.7.0`, OR downgrade KGP " +
                        "to `< 2.2.0`, OR add per-flavor resource directories manually via " +
                        "`kotlin.sourceSets.commonFlavor.resources.srcDir(...)`.",
                )
            }
        }

        // V17: KGP × Gradle compatibility. Currently: KGP 2.0.x + Gradle 8.0-8.4 has
        // unstable Hierarchy Template.
        if (kgpVersion != null) {
            val kgpMajor = kgpVersion.substringBefore(".").toIntOrNull() ?: 0
            val kgpMinor = kgpVersion.substringAfter(".").substringBefore(".").toIntOrNull() ?: 0
            val gradleMajor = gradleVersion.substringBefore(".").toIntOrNull() ?: 0
            val gradleMinor = gradleVersion.substringAfter(".").substringBefore(".").toIntOrNull() ?: 0
            if (kgpMajor == 2 && kgpMinor == 0 && gradleMajor == 8 && gradleMinor < 5) {
                findings += KmpFlavorValidationFinding(
                    code = CODE_KGP_GRADLE_VERSION_INCOMPATIBLE,
                    severity = KmpFlavorValidationSeverity.WARNING,
                    message = "Known-incompatible combination: KGP `$kgpVersion` + Gradle " +
                        "`$gradleVersion`. The Hierarchy Template surface is unstable on this " +
                        "pairing; matrix-mode source-set wiring may emit spurious " +
                        "`Invalid Source Set Dependency Across Trees` warnings.",
                    fix = "Upgrade Gradle to `>= 8.5` (recommended) OR upgrade KGP to `>= 2.1.0`.",
                )
            }
        }

        return findings
    }

    /**
     * Naïve semver comparison sufficient for major.minor.patch strings used by
     * Kotlin / Compose / Gradle. Returns true iff [a] < [b].
     */
    private fun versionLessThan(a: String, b: String): Boolean {
        val aParts = a.split(".").mapNotNull { it.toIntOrNull() }
        val bParts = b.split(".").mapNotNull { it.toIntOrNull() }
        for (i in 0 until minOf(aParts.size, bParts.size)) {
            if (aParts[i] != bParts[i]) return aParts[i] < bParts[i]
        }
        return aParts.size < bParts.size
    }
}
