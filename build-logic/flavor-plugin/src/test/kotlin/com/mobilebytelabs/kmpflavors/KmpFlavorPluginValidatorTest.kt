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
 */

package com.mobilebytelabs.kmpflavors

import com.mobilebytelabs.kmpflavors.internal.KmpFlavorPluginValidator
import com.mobilebytelabs.kmpflavors.internal.KmpFlavorPluginValidator.CODE_BUILD_CONFIG_FIELD_AUTO_DERIVED_COLLISION
import com.mobilebytelabs.kmpflavors.internal.KmpFlavorPluginValidator.CODE_DIMENSION_HAS_NO_FLAVORS
import com.mobilebytelabs.kmpflavors.internal.KmpFlavorPluginValidator.CODE_FLAVOR_BUILD_TYPE_COLLISION
import com.mobilebytelabs.kmpflavors.internal.KmpFlavorPluginValidator.CODE_FLAVOR_MISSING_DIMENSION
import com.mobilebytelabs.kmpflavors.internal.KmpFlavorPluginValidator.CODE_INVALID_BUILD_CONFIG_FIELD_TYPE
import com.mobilebytelabs.kmpflavors.internal.KmpFlavorPluginValidator.CODE_MATRIX_MODE_WITHOUT_FLAVORS
import com.mobilebytelabs.kmpflavors.internal.KmpFlavorPluginValidator.CODE_UNKNOWN_ACTIVE_VARIANT
import com.mobilebytelabs.kmpflavors.internal.KmpFlavorPluginValidator.CODE_VARIANT_FILTER_EXCLUDED_ALL
import com.mobilebytelabs.kmpflavors.internal.KmpFlavorPluginValidator.CODE_ZERO_KMP_TARGETS
import com.mobilebytelabs.kmpflavors.internal.KmpFlavorValidationFinding
import com.mobilebytelabs.kmpflavors.internal.KmpFlavorValidationSeverity
import io.mockk.every
import io.mockk.mockk
import org.gradle.api.provider.Property
import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * W1.4 — fail-fast configuration validation per RFC §3 Q23.
 *
 * Each validation has a stable error code (`KMPF-Vxx`), human message,
 * and a concrete fix. The codes are part of the public API surface —
 * once shipped they don't change. v2.0's `docs/ERROR_CODES.md` (W5)
 * is the canonical catalog.
 */
class KmpFlavorPluginValidatorTest {

    private val project = ProjectBuilder.builder().build()

    private fun flavor(name: String): FlavorConfig = project.objects.newInstance(FlavorConfig::class.java, name)

    private fun buildType(name: String): BuildTypeConfig = project.objects.newInstance(BuildTypeConfig::class.java, name)

    private fun dim(name: String): FlavorDimension = project.objects.newInstance(FlavorDimension::class.java, name)

    private fun variant(name: String, flavorNames: List<String> = emptyList()): FlavorVariant = FlavorVariant(name = name, flavors = flavorNames.map { flavor(it) })

    private fun KmpFlavorValidationFinding.matches(code: String): Boolean = this.code == code

    @Test
    fun `KMPF-V01 fires when a flavor and build type share a name`() {
        val findings = KmpFlavorPluginValidator.validate(
            flavors = listOf(flavor("debug"), flavor("paid")),
            buildTypes = listOf(buildType("debug"), buildType("release")),
            resolvedVariants = listOf(variant("debug"), variant("paid")),
            matrixModeEnabled = false,
            detectedTargetCount = 1,
        )

        val v01 = findings.find { it.matches(CODE_FLAVOR_BUILD_TYPE_COLLISION) }
        assertNotNull(v01, "Expected KMPF-V01 finding; got $findings")
        assertEquals(KmpFlavorValidationSeverity.ERROR, v01!!.severity)
        assertTrue(v01.message.contains("debug"), "message must name the colliding identifier")
        assertTrue(v01.fix.isNotBlank(), "fix must be a non-empty actionable suggestion")
    }

    @Test
    fun `KMPF-V01 does not fire when names are distinct`() {
        val findings = KmpFlavorPluginValidator.validate(
            flavors = listOf(flavor("free"), flavor("paid")),
            buildTypes = listOf(buildType("debug"), buildType("release")),
            resolvedVariants = listOf(variant("free"), variant("paid")),
            matrixModeEnabled = false,
            detectedTargetCount = 1,
        )

        assertTrue(findings.none { it.matches(CODE_FLAVOR_BUILD_TYPE_COLLISION) })
    }

    @Test
    fun `KMPF-V04 fires when variant filter excluded every variant`() {
        val findings = KmpFlavorPluginValidator.validate(
            flavors = listOf(flavor("free"), flavor("paid")),
            buildTypes = emptyList(),
            resolvedVariants = emptyList(), // filter excluded everything
            matrixModeEnabled = false,
            detectedTargetCount = 1,
        )

        val v04 = findings.find { it.matches(CODE_VARIANT_FILTER_EXCLUDED_ALL) }
        assertNotNull(v04)
        assertEquals(KmpFlavorValidationSeverity.ERROR, v04!!.severity)
    }

    @Test
    fun `KMPF-V05 fires (as WARNING) when matrix mode is on but zero KMP targets are detected`() {
        val findings = KmpFlavorPluginValidator.validate(
            flavors = listOf(flavor("free")),
            buildTypes = emptyList(),
            resolvedVariants = listOf(variant("free")),
            matrixModeEnabled = true,
            detectedTargetCount = 0,
        )

        val v05 = findings.find { it.matches(CODE_ZERO_KMP_TARGETS) }
        assertNotNull(v05)
        assertEquals(KmpFlavorValidationSeverity.WARNING, v05!!.severity)
    }

    @Test
    fun `KMPF-V05 does not fire when matrix mode is off — v1_x has no opinion on target count`() {
        val findings = KmpFlavorPluginValidator.validate(
            flavors = listOf(flavor("free")),
            buildTypes = emptyList(),
            resolvedVariants = listOf(variant("free")),
            matrixModeEnabled = false,
            detectedTargetCount = 0,
        )

        assertTrue(findings.none { it.matches(CODE_ZERO_KMP_TARGETS) })
    }

    @Test
    fun `KMPF-V08 fires when buildMatrix opt-in is set but no flavors are registered`() {
        val findings = KmpFlavorPluginValidator.validate(
            flavors = emptyList(),
            buildTypes = emptyList(),
            resolvedVariants = emptyList(),
            matrixModeEnabled = true,
            detectedTargetCount = 1,
        )

        val v08 = findings.find { it.matches(CODE_MATRIX_MODE_WITHOUT_FLAVORS) }
        assertNotNull(v08)
        assertEquals(KmpFlavorValidationSeverity.ERROR, v08!!.severity)
    }

    @Test
    fun `findings carry stable error codes shaped like KMPF-Vxx`() {
        val findings = KmpFlavorPluginValidator.validate(
            flavors = listOf(flavor("debug")),
            buildTypes = listOf(buildType("debug")),
            resolvedVariants = listOf(variant("debug")),
            matrixModeEnabled = false,
            detectedTargetCount = 1,
        )

        findings.forEach { f ->
            assertTrue(
                f.code.matches(Regex("""^KMPF-V\d{2}$""")),
                "Finding code '${f.code}' must match KMPF-Vxx",
            )
        }
    }

    @Test
    fun `KMPF-V02 fires when a flavor is missing dimension but dimensions are registered`() {
        val tier = dim("tier")
        val freeWithDim = flavor("free").also { it.dimension.set("tier") }
        val paidNoDim = flavor("paid") // missing dimension
        val findings = KmpFlavorPluginValidator.validate(
            flavors = listOf(freeWithDim, paidNoDim),
            buildTypes = emptyList(),
            resolvedVariants = listOf(variant("free")),
            matrixModeEnabled = false,
            detectedTargetCount = 1,
            dimensions = listOf(tier),
        )

        val v02 = findings.find { it.matches(CODE_FLAVOR_MISSING_DIMENSION) }
        assertNotNull(v02, "Expected KMPF-V02 finding; got $findings")
        assertEquals(KmpFlavorValidationSeverity.ERROR, v02!!.severity)
        assertTrue(v02.message.contains("paid"), "message must name the flavor missing the dimension")
        assertTrue(v02.fix.contains("dimension.set"), "fix must point at the dimension.set call")
    }

    @Test
    fun `KMPF-V02 does not fire when no dimensions are registered (single-dimension semantics)`() {
        val freeNoDim = flavor("free")
        val paidNoDim = flavor("paid")
        val findings = KmpFlavorPluginValidator.validate(
            flavors = listOf(freeNoDim, paidNoDim),
            buildTypes = emptyList(),
            resolvedVariants = listOf(variant("free"), variant("paid")),
            matrixModeEnabled = false,
            detectedTargetCount = 1,
            dimensions = emptyList(),
        )

        assertTrue(findings.none { it.matches(CODE_FLAVOR_MISSING_DIMENSION) })
    }

    @Test
    fun `KMPF-V03 fires when a dimension has no flavors assigned to it`() {
        val tier = dim("tier")
        val env = dim("environment")
        val free = flavor("free").also { it.dimension.set("tier") }
        // environment dimension has zero flavors → V03
        val findings = KmpFlavorPluginValidator.validate(
            flavors = listOf(free),
            buildTypes = emptyList(),
            resolvedVariants = emptyList(),
            matrixModeEnabled = false,
            detectedTargetCount = 1,
            dimensions = listOf(tier, env),
        )

        val v03 = findings.find { it.matches(CODE_DIMENSION_HAS_NO_FLAVORS) }
        assertNotNull(v03, "Expected KMPF-V03 finding; got $findings")
        assertEquals(KmpFlavorValidationSeverity.ERROR, v03!!.severity)
        assertTrue(v03.message.contains("environment"), "message must name the empty dimension")
    }

    @Test
    fun `KMPF-V03 suppresses V04 — they are not allowed to double-fire on the same empty matrix`() {
        val env = dim("environment")
        val free = flavor("free").also { it.dimension.set("tier") } // not in env dim
        val findings = KmpFlavorPluginValidator.validate(
            flavors = listOf(free),
            buildTypes = emptyList(),
            resolvedVariants = emptyList(),
            matrixModeEnabled = false,
            detectedTargetCount = 1,
            dimensions = listOf(env),
        )

        assertTrue(findings.any { it.matches(CODE_DIMENSION_HAS_NO_FLAVORS) }, "V03 must fire")
        assertTrue(findings.none { it.matches(CODE_VARIANT_FILTER_EXCLUDED_ALL) }, "V04 must NOT double-fire when V03 already explains the empty matrix")
    }

    @Test
    fun `KMPF-V06 fires (as WARNING) when -PkmpFlavor names a variant the resolver does not know`() {
        val findings = KmpFlavorPluginValidator.validate(
            flavors = listOf(flavor("free"), flavor("paid")),
            buildTypes = emptyList(),
            resolvedVariants = listOf(variant("free"), variant("paid")),
            matrixModeEnabled = false,
            detectedTargetCount = 1,
            requestedVariantName = "ghost",
        )

        val v06 = findings.find { it.matches(CODE_UNKNOWN_ACTIVE_VARIANT) }
        assertNotNull(v06, "Expected KMPF-V06 finding; got $findings")
        assertEquals(KmpFlavorValidationSeverity.WARNING, v06!!.severity, "V06 must be WARNING — -PkmpFlavor is project-wide in multi-project builds")
        assertTrue(v06.message.contains("ghost"), "message must echo the requested variant")
        assertTrue(v06.message.contains("free") && v06.message.contains("paid"), "message must list registered variants")
    }

    @Test
    fun `KMPF-V06 does not fire when the requested variant matches case-insensitively`() {
        val findings = KmpFlavorPluginValidator.validate(
            flavors = listOf(flavor("free")),
            buildTypes = emptyList(),
            resolvedVariants = listOf(variant("freeDev")),
            matrixModeEnabled = false,
            detectedTargetCount = 1,
            requestedVariantName = "freedev",
        )

        assertTrue(findings.none { it.matches(CODE_UNKNOWN_ACTIVE_VARIANT) }, "case-insensitive match must suppress V06")
    }

    @Test
    fun `KMPF-V07 fires when a flavor declares buildConfigField with an unsupported type`() {
        val free = flavor("free").also {
            it.buildConfigField("MyClass", "FOO", "Foo()")
        }
        val findings = KmpFlavorPluginValidator.validate(
            flavors = listOf(free),
            buildTypes = emptyList(),
            resolvedVariants = listOf(variant("free", listOf("free"))),
            matrixModeEnabled = false,
            detectedTargetCount = 1,
        )

        val v07 = findings.find { it.matches(CODE_INVALID_BUILD_CONFIG_FIELD_TYPE) }
        assertNotNull(v07, "Expected KMPF-V07 finding; got $findings")
        assertEquals(KmpFlavorValidationSeverity.ERROR, v07!!.severity)
        assertTrue(v07.message.contains("MyClass"), "message must echo the unsupported type")
        assertTrue(v07.message.contains("FOO"), "message must echo the field name")
    }

    @Test
    fun `KMPF-V07 does not fire for any of the supported Kotlin literal types`() {
        val free = flavor("free").also {
            it.buildConfigField("Boolean", "A", "true")
            it.buildConfigField("Int", "B", "42")
            it.buildConfigField("Long", "C", "42L")
            it.buildConfigField("Float", "D", "1.0f")
            it.buildConfigField("Double", "E", "1.0")
            it.buildConfigField("String", "F", "\"x\"")
        }
        val findings = KmpFlavorPluginValidator.validate(
            flavors = listOf(free),
            buildTypes = emptyList(),
            resolvedVariants = listOf(variant("free", listOf("free"))),
            matrixModeEnabled = false,
            detectedTargetCount = 1,
        )

        assertTrue(findings.none { it.matches(CODE_INVALID_BUILD_CONFIG_FIELD_TYPE) })
    }

    @Test
    fun `KMPF-V23 fires when a custom field collides with an auto-derived flavor flag`() {
        // Real-world regression from samples/multi-target-multi-variant — flavor
        // 'enterprise' + custom buildConfigField "IS_ENTERPRISE" produces duplicate
        // const val. The validator surfaces this before codegen.
        val enterprise = flavor("enterprise").also {
            it.buildConfigField("Boolean", "IS_ENTERPRISE", "true")
        }
        val findings = KmpFlavorPluginValidator.validate(
            flavors = listOf(flavor("free"), flavor("paid"), enterprise),
            buildTypes = emptyList(),
            resolvedVariants = listOf(variant("free"), variant("paid"), variant("enterprise")),
            matrixModeEnabled = false,
            detectedTargetCount = 1,
        )

        val v23 = findings.find { it.matches(CODE_BUILD_CONFIG_FIELD_AUTO_DERIVED_COLLISION) }
        assertNotNull(v23, "Expected KMPF-V23 finding; got $findings")
        assertEquals(KmpFlavorValidationSeverity.ERROR, v23!!.severity)
        assertTrue(v23.message.contains("IS_ENTERPRISE"), "message must echo the colliding field name")
        assertTrue(v23.message.contains("enterprise"), "message must reference the source flavor")
        assertTrue(v23.fix.contains("TIER_ENTERPRISE"), "fix must suggest a concrete rename")
    }

    @Test
    fun `KMPF-V23 fires when a custom field collides with an auto-derived buildType flag`() {
        val paid = flavor("paid").also {
            // Flavor declares IS_RELEASE while a 'release' build type is also registered.
            it.buildConfigField("Boolean", "IS_RELEASE", "false")
        }
        val findings = KmpFlavorPluginValidator.validate(
            flavors = listOf(flavor("free"), paid),
            buildTypes = listOf(buildType("debug"), buildType("release")),
            resolvedVariants = listOf(variant("freeDebug"), variant("paidRelease")),
            matrixModeEnabled = false,
            detectedTargetCount = 1,
        )

        val v23 = findings.find { it.matches(CODE_BUILD_CONFIG_FIELD_AUTO_DERIVED_COLLISION) }
        assertNotNull(v23, "Expected KMPF-V23 finding; got $findings")
        assertEquals(KmpFlavorValidationSeverity.ERROR, v23!!.severity)
        assertTrue(v23.message.contains("IS_RELEASE"), "message must echo the colliding field name")
        assertTrue(v23.message.contains("release"), "message must reference the source build type")
    }

    @Test
    fun `KMPF-V23 fires when a custom field uses the always-reserved VARIANT_NAME constant`() {
        val free = flavor("free").also {
            it.buildConfigField("String", "VARIANT_NAME", "\"override\"")
        }
        val findings = KmpFlavorPluginValidator.validate(
            flavors = listOf(free, flavor("paid")),
            buildTypes = emptyList(),
            resolvedVariants = listOf(variant("free"), variant("paid")),
            matrixModeEnabled = false,
            detectedTargetCount = 1,
        )

        val v23 = findings.find { it.matches(CODE_BUILD_CONFIG_FIELD_AUTO_DERIVED_COLLISION) }
        assertNotNull(v23, "Expected KMPF-V23 finding; got $findings")
        assertTrue(v23!!.message.contains("VARIANT_NAME"))
        assertTrue(v23.fix.contains("APP_VARIANT_NAME"), "fix must suggest APP_VARIANT_NAME rename")
    }

    @Test
    fun `KMPF-V23 does not fire when a custom IS_ name does not match any registered flavor or buildType`() {
        // IS_DEBUG without a 'debug' build type is fine — no auto-derived IS_DEBUG
        // is generated, so no collision happens.
        val free = flavor("free").also {
            it.buildConfigField("Boolean", "IS_DEBUG", "true")
        }
        val findings = KmpFlavorPluginValidator.validate(
            flavors = listOf(free, flavor("paid")),
            buildTypes = emptyList(),
            resolvedVariants = listOf(variant("free"), variant("paid")),
            matrixModeEnabled = false,
            detectedTargetCount = 1,
        )

        assertTrue(findings.none { it.matches(CODE_BUILD_CONFIG_FIELD_AUTO_DERIVED_COLLISION) })
    }

    @Test
    fun `KMPF-V23 does not fire for well-prefixed custom fields like MAX_ITEMS TIER_NAME PREMIUM_TIER`() {
        val free = flavor("free").also {
            it.buildConfigField("Int", "MAX_ITEMS", "10")
            it.buildConfigField("String", "TIER_NAME", "\"free\"")
            it.buildConfigField("Boolean", "PREMIUM_TIER", "false")
        }
        val findings = KmpFlavorPluginValidator.validate(
            flavors = listOf(free, flavor("paid")),
            buildTypes = listOf(buildType("debug"), buildType("release")),
            resolvedVariants = listOf(variant("freeDebug"), variant("paidRelease")),
            matrixModeEnabled = false,
            detectedTargetCount = 1,
        )

        assertTrue(findings.none { it.matches(CODE_BUILD_CONFIG_FIELD_AUTO_DERIVED_COLLISION) })
    }

    @Test
    fun `KMPF-V23 fires on a buildType-scoped custom field that collides with a flavor flag`() {
        val debug = buildType("debug").also {
            // Build-type-scoped custom field that happens to match the auto-derived
            // IS_FREE flag from the registered 'free' flavor.
            it.buildConfigField("Boolean", "IS_FREE", "false")
        }
        val findings = KmpFlavorPluginValidator.validate(
            flavors = listOf(flavor("free"), flavor("paid")),
            buildTypes = listOf(debug),
            resolvedVariants = listOf(variant("freeDebug"), variant("paidDebug")),
            matrixModeEnabled = false,
            detectedTargetCount = 1,
        )

        val v23 = findings.find { it.matches(CODE_BUILD_CONFIG_FIELD_AUTO_DERIVED_COLLISION) }
        assertNotNull(v23, "Expected KMPF-V23 finding; got $findings")
        assertTrue(v23!!.message.contains("buildType 'debug'"), "message must identify the buildType source")
        assertTrue(v23.message.contains("IS_FREE"))
    }
}
