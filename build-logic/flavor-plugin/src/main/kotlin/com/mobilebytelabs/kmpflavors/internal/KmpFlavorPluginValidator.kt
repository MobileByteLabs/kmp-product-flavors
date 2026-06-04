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
import com.mobilebytelabs.kmpflavors.SigningConfig

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
 * | KMPF-V23 | ERROR | Custom `buildConfigField` name collides with an auto-derived BuildKonfig constant |
 * | KMPF-V24 | ERROR | Mutex — `dimensions { }` block AND legacy `flavorDimensions { } / flavors { }` blocks BOTH used in same kmpFlavors{} |
 * | KMPF-V25 | ERROR | Two `dimension(name)` declarations share the same name |
 * | KMPF-V26 | ERROR/WARNING | Vault-integrated `buildKonfig { secret(id) }` failed to resolve OR `secrets-manifest.yaml` schema < v2.1 (graceful-degrade WARN) |
 * | KMPF-V27 | ERROR | `buildKonfig { customField<T>(name, value) }` type T cannot be emitted by codegen |
 * | KMPF-V28 | ERROR | `buildKonfig { perTarget(name) { } }` references a target name not present in `kotlin.targets` |
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

    /** v2.4 Phase 6A — V18+ adoption-driven codes. */
    const val CODE_VARIANT_EXCLUDE_TARGET_MISSING: String = "KMPF-V18"
    const val CODE_SONATYPE_SNAPSHOTS_NOT_ENABLED: String = "KMPF-V19"
    const val CODE_VARIANT_CACHE_NAMESPACING_NO_MATRIX: String = "KMPF-V20"
    const val CODE_LEGACY_ACTIVEFLAVOR_DSL: String = "KMPF-V21"
    const val CODE_VARIANT_EXCLUDE_EMPTY_COORDINATES: String = "KMPF-V22"

    /**
     * v2.4 stability-phase Phase 1 follow-up — custom `buildConfigField` name
     * collides with an auto-derived `BuildKonfig` constant. Detected before
     * codegen so consumers don't hit Kotlin "Conflicting declarations" at
     * compile time. See `samples/multi-target-multi-variant/` STABILITY-PLAN-TODO.
     */
    const val CODE_BUILD_CONFIG_FIELD_AUTO_DERIVED_COLLISION: String = "KMPF-V23"

    /**
     * v2.5 Phase 1 — `dimensions { }` ergonomic DSL block (v2.5+) is mutually
     * exclusive with the legacy flat `flavorDimensions { } + flavors { }` pair
     * (v2.4-). Mixing both in the same `kmpFlavors {}` block is a configuration
     * error because the same identifier (e.g. "free") could be registered twice
     * with conflicting properties. Strict-additive contract preserved: existing
     * v2.4 projects using only the flat DSL never see V24; only opt-in to
     * `dimensions {}` triggers the mutex check.
     */
    const val CODE_DIMENSIONS_VS_FLAT_MUTEX: String = "KMPF-V24"

    /**
     * v2.5 Phase 1 — duplicate dimension names. Two `dimension(name) { }`
     * declarations sharing the same name produce ambiguous flavor↔dimension
     * mappings. Also fires for AGP-side conflict detection when the bridge
     * re-applies and finds an existing AGP flavor with a different `dimension =`
     * assignment than what KMP wants to register (cross-vault hand-edit case).
     */
    const val CODE_DIMENSION_NAME_CLASH: String = "KMPF-V25"

    /**
     * v2.5 Phase 3 — vault-integrated `buildKonfig { secret(id) }` resolution.
     *
     * Dual severity: ERROR when the manifest declares the secret but lookup fails
     * (e.g. no `flavor_selector` entry for the active variant); WARNING when the
     * consumer's `secrets-manifest.yaml` is schema v2.0 (no `flavor_selector` field
     * at all). The WARN path emits placeholder values instead of hardcoded secrets
     * (SV15 compliance — see RULE-SECRETS-VAULT-001).
     */
    const val CODE_SECRET_RESOLUTION_FAIL: String = "KMPF-V26"

    /**
     * v2.5 Phase 3 — `buildKonfig { customField<T>(name, value) }` type T cannot
     * be emitted by the codegen. Fires when T is neither a primitive (already
     * covered by V07), nor a sealed class, nor a flat `List<T>`. Nested generics
     * (`Map<K, V>`, `List<List<T>>`) and open classes are explicitly out of scope
     * for v2.5 — use a sealed class or a primitive.
     */
    const val CODE_CUSTOM_TYPE_EMIT_FAIL: String = "KMPF-V27"

    /**
     * v2.5 Phase 3 — `buildKonfig { perTarget(name) { } }` references a target
     * name not present in `kotlin.targets`. The plugin can't filter the per-target
     * field if the target doesn't exist; clearer to fail at configuration time
     * than to silently drop the field at codegen time.
     */
    const val CODE_PERTARGET_ON_NON_KMP: String = "KMPF-V28"

    /**
     * v2.6 Phase 4 — `buildKonfig { network { baseUrl("X" to ...) } }` references
     * a flavor name not registered in `flavors {}` or any `dimensions { dimension { flavor() } }`
     * block. Fires at configuration time so consumers see the typo before codegen.
     */
    const val CODE_BASE_URL_FLAVOR_MISSING: String = "KMPF-V29"

    /**
     * v2.6 Phase 4 — a resolved variant's primary flavor has no matching
     * `network { baseUrl("flavor" to ...) }` entry. The active variant would
     * compile but `BuildKonfig.Network.BASE_URL` would point at the sentinel
     * placeholder. Fires at configuration time to make the gap explicit.
     */
    const val CODE_BASE_URL_NOT_FOUND_FOR_VARIANT: String = "KMPF-V30"

    /**
     * Auto-generated `BuildKonfig` constants the codegen ALWAYS emits regardless
     * of whether build types are enabled. A custom `buildConfigField` matching
     * one of these names produces a duplicate `const val` at compile time.
     */
    private val ALWAYS_RESERVED_BUILD_CONFIG_NAMES: Set<String> = setOf("VARIANT_NAME", "BUILD_TYPE")

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
        // v2.5 — mutex detection between `dimensions {}` sugar (v2.5+) and
        // the legacy flat `flavorDimensions {}/flavors {}` pair (v2.4-).
        // Default false preserves the v2.4 call surface — existing call-sites
        // (KmpFlavorPlugin.kt and tests) don't change unless they opt into
        // the new tracking.
        dimensionsDslUsed: Boolean = false,
        legacyFlatDslUsed: Boolean = false,
    ): List<KmpFlavorValidationFinding> {
        val findings = mutableListOf<KmpFlavorValidationFinding>()

        // KMPF-V24: mutex — both v2.5 dimensions {} sugar AND legacy flat DSL used.
        // Fires at configuration time, before any variant resolution. Surfaces a
        // single, actionable error pointing at the migration cookbook.
        if (dimensionsDslUsed && legacyFlatDslUsed) {
            findings += KmpFlavorValidationFinding(
                code = CODE_DIMENSIONS_VS_FLAT_MUTEX,
                severity = KmpFlavorValidationSeverity.ERROR,
                message = "kmpFlavors {} cannot mix the v2.5 `dimensions { }` sugar with the " +
                    "legacy `flavorDimensions { } + flavors { }` blocks. Pick one style per " +
                    "project: either `dimensions { dimension(\"tier\") { flavor(\"free\") } }` " +
                    "OR `flavorDimensions { register(\"tier\") } + flavors { register(\"free\") { dimension.set(\"tier\") } }`.",
                fix = "Pick one DSL style. See `docs/REFERENCE.md` (kmpFlavors {} block) or " +
                    "`docs/PRODUCT_FLAVORS.md` for the canonical dimensions/flavors shape.",
            )
        }

        // KMPF-V25: duplicate dimension names. Two `dimension(\"tier\") { ... }` blocks
        // (or `flavorDimensions { register(\"tier\"); register(\"tier\") }`) produce
        // ambiguous flavor-to-dimension resolution.
        val duplicateDimNames = dimensions
            .groupBy { it.name }
            .filterValues { it.size > 1 }
            .keys
        duplicateDimNames.forEach { dupName ->
            findings += KmpFlavorValidationFinding(
                code = CODE_DIMENSION_NAME_CLASH,
                severity = KmpFlavorValidationSeverity.ERROR,
                message = "Dimension '$dupName' is declared more than once. Each dimension " +
                    "must have a unique name — duplicate declarations produce ambiguous " +
                    "flavor↔dimension mappings.",
                fix = "Rename one of the '$dupName' declarations OR remove the duplicate. " +
                    "If you intended two SEPARATE axes of variation, give them distinct names " +
                    "(e.g. \"tier\" + \"tierVariant\").",
            )
        }

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

        // KMPF-V23: a custom `buildConfigField` name collides with an auto-derived
        // BuildKonfig constant (`VARIANT_NAME`, `BUILD_TYPE`, `IS_<FLAVOR>`, `IS_<BUILDTYPE>`).
        // Without this check the codegen produces two `const val <NAME>` entries and the
        // Kotlin compiler fails with "Conflicting declarations". Surfaced before codegen.
        //
        // Reserved-name set is computed from THIS configuration — only actually-registered
        // flavors/buildTypes contribute auto-derived constants. A literal `IS_DEBUG` field
        // on a project that doesn't declare a `debug` buildType is fine.
        val reservedNames: Set<String> = buildSet {
            addAll(ALWAYS_RESERVED_BUILD_CONFIG_NAMES)
            flavors.forEach { add("IS_${it.name.uppercase()}") }
            buildTypes.forEach { add("IS_${it.name.uppercase()}") }
        }
        // Sequence over flavor-level AND buildType-level custom fields. Pair each field
        // with its source name (flavor or buildType) so the message points at the right
        // DSL block.
        val customFields: List<Triple<String, String, com.mobilebytelabs.kmpflavors.BuildConfigField>> =
            flavors.flatMap { flavor ->
                flavor.buildConfigFields.get().values.map { Triple("flavor", flavor.name, it) }
            } +
                buildTypes.flatMap { buildType ->
                    buildType.buildConfigFields.get().values.map { Triple("buildType", buildType.name, it) }
                }
        customFields.forEach { (sourceKind, sourceName, field) ->
            if (field.name in reservedNames) {
                val derivation = when {
                    field.name == "VARIANT_NAME" ->
                        "an auto-generated constant emitted by every BuildKonfig"

                    field.name == "BUILD_TYPE" ->
                        "an auto-generated constant emitted when buildTypes are registered"

                    flavors.any { "IS_${it.name.uppercase()}" == field.name } ->
                        "the auto-derived flavor flag for flavor '" +
                            flavors.first { "IS_${it.name.uppercase()}" == field.name }.name + "'"

                    else ->
                        "the auto-derived build-type flag for build type '" +
                            buildTypes.first { "IS_${it.name.uppercase()}" == field.name }.name + "'"
                }
                findings += KmpFlavorValidationFinding(
                    code = CODE_BUILD_CONFIG_FIELD_AUTO_DERIVED_COLLISION,
                    severity = KmpFlavorValidationSeverity.ERROR,
                    message = "$sourceKind '$sourceName' declares `buildConfigField` " +
                        "'${field.name}', which collides with $derivation. " +
                        "BuildKonfig codegen would emit two `const val ${field.name}` " +
                        "entries and the Kotlin compiler would fail with " +
                        "\"Conflicting declarations\".",
                    fix = "Rename the custom field to avoid the reserved namespace. " +
                        "Avoid the `IS_*` prefix for custom flags (the plugin reserves it " +
                        "for auto-derived flavor/build-type flags) and the literal names " +
                        "`VARIANT_NAME` / `BUILD_TYPE`. Convention: prefix custom flags " +
                        "with the tier semantic — e.g. `MAX_*`, `TIER_*`, `PREMIUM_*`, " +
                        "`FEATURE_*`. Example: rename '${field.name}' → " +
                        "'${suggestRename(field.name)}'.",
                )
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
     * v2.5 Phase 3 — validate the `kmpFlavors.buildKonfig {}` DSL block.
     *
     * Decoupled from [validate] because the BuildKonfig DSL inputs are
     * structurally different (target names, custom-field types, secret IDs)
     * from the core flavor/dimension validations. Same separation pattern as
     * [validatePlatformAndVersionCompatibility].
     *
     * Three sub-checks:
     *
     * - **KMPF-V26** — `secrets-manifest.yaml` schema version is < v2.1 but
     *   `buildKonfig { secret(id) }` is declared. WARN — plugin emits placeholder
     *   values instead of hardcoded secrets (SV15 compliance per
     *   RULE-SECRETS-VAULT-001). ERROR variant fires from
     *   [BuildKonfigSecretResolver] at task-execution time when the manifest
     *   schema is v2.1+ but lookup fails.
     *
     * - **KMPF-V27** — `customField<T>` declared with an unsupported type
     *   (anything except primitive, sealed class, or flat `List<T>`).
     *
     * - **KMPF-V28** — `perTarget(name) { }` references a target name that
     *   isn't in `kotlin.targets`.
     *
     * @param buildKonfigSecretIds Secret IDs declared via `buildKonfig { secret(id) }`.
     *   Empty list = nothing to validate, returns immediately.
     * @param secretsManifestSchemaVersion The `schema_version` field from the consumer's
     *   `secrets-manifest.yaml`, or null if the manifest is missing. Used only when
     *   `buildKonfigSecretIds` is non-empty.
     * @param customFieldUnsupportedTypes Pairs of (customField name, type description)
     *   for fields the codegen can't emit. Caller computes the set.
     * @param perTargetNamesDeclared Target names declared via `perTarget(name) { }`.
     * @param kotlinTargetNames Names of all `kotlin.targets` actually configured on the
     *   project (e.g. `{"iosArm64", "desktop", "wasmJs", ...}`). Used to spot
     *   perTarget declarations that point at non-existent targets.
     */
    fun validateBuildKonfigDsl(
        buildKonfigSecretIds: List<String> = emptyList(),
        secretsManifestSchemaVersion: String? = null,
        customFieldUnsupportedTypes: List<Pair<String, String>> = emptyList(),
        perTargetNamesDeclared: Set<String> = emptySet(),
        kotlinTargetNames: Set<String> = emptySet(),
        // v2.6 Phase 4 inputs — empty defaults so existing callers stay back-compat.
        buildKonfigBaseUrlFlavors: Set<String> = emptySet(),
        registeredFlavorNames: Set<String> = emptySet(),
        variantActiveFlavors: Map<String, String> = emptyMap(),
    ): List<KmpFlavorValidationFinding> {
        val findings = mutableListOf<KmpFlavorValidationFinding>()

        // KMPF-V26 (WARN path) — schema-fallback when consumer's manifest is v2.0.
        // The ERROR path (resolution-fail at task-execution time) is emitted from
        // BuildKonfigSecretResolver directly with the same code constant.
        if (buildKonfigSecretIds.isNotEmpty() &&
            secretsManifestSchemaVersion != null &&
            versionLessThan(secretsManifestSchemaVersion, "2.1")
        ) {
            findings += KmpFlavorValidationFinding(
                code = CODE_SECRET_RESOLUTION_FAIL,
                severity = KmpFlavorValidationSeverity.WARNING,
                message = "kmpFlavors.buildKonfig { secret(...) } is declared for " +
                    "${buildKonfigSecretIds.joinToString { "'$it'" }}, but the consumer's " +
                    "secrets-manifest.yaml is schema_version='$secretsManifestSchemaVersion'. " +
                    "Schema v2.1+ is required for flavor-aware secret resolution. " +
                    "The plugin will emit placeholder values (e.g. " +
                    "`<unresolved:schema-v2.0>`) instead of hardcoded secrets " +
                    "(SV15 compliance per RULE-SECRETS-VAULT-001).",
                fix = "Upgrade secrets-manifest.yaml to schema_version: \"2.1\" and add " +
                    "needs[].flavor_selector blocks for the declared secret IDs. See " +
                    "docs/SECRETS_INTEGRATION.md for the consumer contract.",
            )
        }

        // KMPF-V27 — customField type cannot be emitted by codegen.
        customFieldUnsupportedTypes.forEach { (name, typeDesc) ->
            findings += KmpFlavorValidationFinding(
                code = CODE_CUSTOM_TYPE_EMIT_FAIL,
                severity = KmpFlavorValidationSeverity.ERROR,
                message = "kmpFlavors.buildKonfig { customField<T>(\"$name\", ...) } " +
                    "declared with type '$typeDesc', which the codegen cannot emit. " +
                    "Supported: primitives (Boolean/Int/Long/Float/Double/String), " +
                    "sealed classes, and flat List<T> where T is a primitive or sealed class.",
                fix = "Convert to a sealed class with explicit subclass objects, OR " +
                    "stringify the value via a primitive customField. Nested generics " +
                    "(Map<K, V>, List<List<T>>) and open classes are out of scope for v2.5.",
            )
        }

        // KMPF-V29 — `network { baseUrl("X" to ...) }` references an unregistered flavor.
        val missingBaseUrlFlavors = buildKonfigBaseUrlFlavors - registeredFlavorNames
        missingBaseUrlFlavors.forEach { flavor ->
            findings += KmpFlavorValidationFinding(
                code = CODE_BASE_URL_FLAVOR_MISSING,
                severity = KmpFlavorValidationSeverity.ERROR,
                message = "kmpFlavors.buildKonfig.network { baseUrl(\"$flavor\" to ...) } " +
                    "references flavor '$flavor', but no flavor with that name is registered. " +
                    "Available flavors: ${
                        if (registeredFlavorNames.isEmpty()) {
                            "<none>"
                        } else {
                            registeredFlavorNames.sorted().joinToString { "'$it'" }
                        }
                    }.",
                fix = "Either register the flavor via `flavors { register(\"$flavor\") {} }` " +
                    "or `dimensions { dimension(\"...\") { flavor(\"$flavor\") } }`, " +
                    "or remove the orphan baseUrl key from the network block.",
            )
        }

        // KMPF-V30 — at least one resolved variant's active flavor has no matching baseUrl.
        variantActiveFlavors.forEach { (variantName, activeFlavor) ->
            if (buildKonfigBaseUrlFlavors.isNotEmpty() && activeFlavor !in buildKonfigBaseUrlFlavors) {
                findings += KmpFlavorValidationFinding(
                    code = CODE_BASE_URL_NOT_FOUND_FOR_VARIANT,
                    severity = KmpFlavorValidationSeverity.ERROR,
                    message = "Variant '$variantName' resolves to active flavor '$activeFlavor', " +
                        "but kmpFlavors.buildKonfig.network has no baseUrl mapped for it. " +
                        "baseUrl flavors: ${buildKonfigBaseUrlFlavors.sorted().joinToString { "'$it'" }}.",
                    fix = "Add `baseUrl(\"$activeFlavor\" to \"https://...\")` to the network {} " +
                        "block, or refine `variantFilter {}` to exclude variant '$variantName'.",
                )
            }
        }

        // KMPF-V28 — perTarget references a target name not present in kotlin.targets.
        val invalidPerTargets = perTargetNamesDeclared - kotlinTargetNames
        invalidPerTargets.forEach { targetName ->
            findings += KmpFlavorValidationFinding(
                code = CODE_PERTARGET_ON_NON_KMP,
                severity = KmpFlavorValidationSeverity.ERROR,
                message = "kmpFlavors.buildKonfig { perTarget(\"$targetName\") { } } " +
                    "references a target that isn't declared in this project's " +
                    "`kotlin { ... }` block. Available targets: " +
                    "${kotlinTargetNames.sorted().joinToString { "'$it'" }}.",
                fix = "Use a target name actually declared in `kotlin { ... }` " +
                    "(e.g. 'iosMain', 'androidMain', 'desktopMain', 'wasmJsMain'), " +
                    "OR add the missing target to the `kotlin { ... }` block.",
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
     * Suggest a non-colliding rename for KMPF-V23 collisions. Best-effort —
     * the result is informational and the consumer makes the final naming call.
     *
     * - `IS_<NAME>` → `TIER_<NAME>` (custom flags should sit outside the `IS_*`
     *   namespace the plugin reserves for auto-derived flavor/build-type flags).
     * - `VARIANT_NAME` → `APP_VARIANT_NAME`.
     * - `BUILD_TYPE` → `APP_BUILD_TYPE`.
     */
    internal fun suggestRename(reservedName: String): String = when {
        reservedName == "VARIANT_NAME" -> "APP_VARIANT_NAME"
        reservedName == "BUILD_TYPE" -> "APP_BUILD_TYPE"
        reservedName.startsWith("IS_") -> "TIER_" + reservedName.removePrefix("IS_")
        else -> "APP_$reservedName"
    }

    /**
     * v2.8 — Validate per-flavor versionCode propagation + signing config password coverage.
     *
     * Two sub-checks:
     *
     * - **KMPF-V50** (ERROR) — a flavor declares `versionCode.set(n)` with `n <= 0`. AGP rejects
     *   non-positive versionCodes at variant-materialization time; catch it earlier with a clear
     *   actionable message instead of letting the build fail deep in the AGP machinery.
     *
     * - **KMPF-V51** (ERROR) — a flavor's `signingConfig.set("name")` references a signing config
     *   whose `storePassword` OR `keyPassword` is unset AND no `*FromEnv()` resolution succeeded
     *   (env-var was unset at configuration time). The bridge still emits the signing config slot
     *   but AGP fails at signing time with a cryptic key-not-found error — better to surface the
     *   missing env-var at configuration time so the consumer can `export KEYSTORE_PASSWORD=...`
     *   and re-run.
     *
     * Decoupled from [validate] to keep the call surface stable for non-signing call-sites
     * (existing tests + the v2.4 call shape). [KmpFlavorPlugin] invokes this from the same
     * `afterEvaluate { }` block where [validate] runs, with the additional signing-config list
     * supplied from the extension.
     *
     * Returns an empty list when no version/signing issues are detected. Empty `flavors` or
     * empty `signingConfigs` is a valid no-op input.
     */
    fun validateVersionAndSigning(
        flavors: List<FlavorConfig>,
        signingConfigs: List<SigningConfig> = emptyList(),
    ): List<KmpFlavorValidationFinding> {
        val findings = mutableListOf<KmpFlavorValidationFinding>()
        val signingByName: Map<String, SigningConfig> = signingConfigs.associateBy { it.name }

        flavors.forEach { flavor ->
            // KMPF-V50: per-flavor versionCode must be positive when set.
            flavor.versionCode.orNull?.let { vc ->
                if (vc <= 0) {
                    findings += KmpFlavorValidationFinding(
                        code = FlavorValidationCodes.V50_VERSION_CODE_PROPAGATED,
                        severity = KmpFlavorValidationSeverity.ERROR,
                        message = "Flavor '${flavor.name}' declares versionCode=$vc which is non-positive. " +
                            "AGP rejects versionCodes <= 0 at variant materialization.",
                        fix = "Set a positive integer: `kmpFlavors { flavors { register(\"${flavor.name}\") " +
                            "{ versionCode.set(N) where N > 0 } } }`. Conventional values follow " +
                            "semver-like math (e.g. 280 for 2.8.0).",
                    )
                }
            }

            // KMPF-V51: per-flavor signingConfig reference must point at a config with passwords resolved.
            flavor.signingConfig.orNull?.let { ref ->
                val sc = signingByName[ref] ?: run {
                    findings += KmpFlavorValidationFinding(
                        code = FlavorValidationCodes.V51_SIGNING_ENV_VAR_SET,
                        severity = KmpFlavorValidationSeverity.ERROR,
                        message = "Flavor '${flavor.name}' references signingConfig '$ref' which is not " +
                            "declared in `kmpFlavors.signingConfigs {}`.",
                        fix = "Declare it: `kmpFlavors { signingConfigs { create(\"$ref\") { " +
                            "storeFile.set(...); storePasswordFromEnv(\"KEYSTORE_PASSWORD\"); " +
                            "keyAlias.set(...); keyPasswordFromEnv(\"KEY_PASSWORD\") } } }`.",
                    )
                    return@let
                }
                val storeMissing = sc.storePassword.orNull.isNullOrEmpty()
                val keyMissing = sc.keyPassword.orNull.isNullOrEmpty()
                if (storeMissing || keyMissing) {
                    val parts = mutableListOf<String>()
                    if (storeMissing) parts.add("storePassword")
                    if (keyMissing) parts.add("keyPassword")
                    findings += KmpFlavorValidationFinding(
                        code = FlavorValidationCodes.V51_SIGNING_ENV_VAR_SET,
                        severity = KmpFlavorValidationSeverity.ERROR,
                        message = "Flavor '${flavor.name}' uses signingConfig '$ref' which has missing " +
                            "${parts.joinToString(" + ")}. AGP would fail at signing time with a cryptic " +
                            "key-not-found error.",
                        fix = "Resolve the missing password(s) via env-var lookup: " +
                            "`signingConfigs { create(\"$ref\") { storePasswordFromEnv(\"KEYSTORE_PASSWORD\"); " +
                            "keyPasswordFromEnv(\"KEY_PASSWORD\") } }`. Set the env-vars before running Gradle.",
                    )
                }
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
