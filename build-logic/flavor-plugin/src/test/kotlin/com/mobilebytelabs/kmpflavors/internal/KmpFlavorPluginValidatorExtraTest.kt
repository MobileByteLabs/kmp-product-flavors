/*
 * Copyright 2026 MobileByteLabs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package com.mobilebytelabs.kmpflavors.internal

import com.mobilebytelabs.kmpflavors.BuildTypeConfig
import com.mobilebytelabs.kmpflavors.FlavorConfig
import com.mobilebytelabs.kmpflavors.FlavorDimension
import com.mobilebytelabs.kmpflavors.FlavorVariant
import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Extra coverage for KmpFlavorPluginValidator branches not exercised by the
 * existing tests. Focus on KMPF-V24 / V25 / V26 / V27 / V28 / V29 / V30 + the
 * platform/version compatibility branches.
 */
class KmpFlavorPluginValidatorExtraTest {

    private fun project() = ProjectBuilder.builder().build()
    private fun newFlavor(name: String, dim: String? = null): FlavorConfig {
        val f = project().objects.newInstance(FlavorConfig::class.java, name)
        if (dim != null) f.dimension.set(dim)
        return f
    }
    private fun newDim(name: String): FlavorDimension =
        project().objects.newInstance(FlavorDimension::class.java, name)
    private fun newBuildType(name: String): BuildTypeConfig =
        project().objects.newInstance(BuildTypeConfig::class.java, name)
    private fun variant(name: String, flavors: List<FlavorConfig> = emptyList()): FlavorVariant =
        FlavorVariant(name = name, flavors = flavors)

    @Test
    fun `KMPF-V24 fires when both dimensions and legacy DSL used`() {
        val findings = KmpFlavorPluginValidator.validate(
            flavors = listOf(newFlavor("free", "tier")),
            buildTypes = emptyList(),
            resolvedVariants = listOf(variant("free")),
            matrixModeEnabled = false,
            detectedTargetCount = 1,
            dimensions = listOf(newDim("tier")),
            dimensionsDslUsed = true,
            legacyFlatDslUsed = true,
        )
        assertTrue(findings.any { it.code == KmpFlavorPluginValidator.CODE_DIMENSIONS_VS_FLAT_MUTEX })
    }

    @Test
    fun `KMPF-V25 fires for duplicate dimension names`() {
        val findings = KmpFlavorPluginValidator.validate(
            flavors = listOf(newFlavor("free", "tier")),
            buildTypes = emptyList(),
            resolvedVariants = listOf(variant("free")),
            matrixModeEnabled = false,
            detectedTargetCount = 1,
            dimensions = listOf(newDim("tier"), newDim("tier")),
        )
        assertTrue(findings.any { it.code == KmpFlavorPluginValidator.CODE_DIMENSION_NAME_CLASH })
    }

    @Test
    fun `KMPF-V08 fires when matrix mode but no flavors`() {
        val findings = KmpFlavorPluginValidator.validate(
            flavors = emptyList(),
            buildTypes = emptyList(),
            resolvedVariants = emptyList(),
            matrixModeEnabled = true,
            detectedTargetCount = 2,
        )
        assertTrue(findings.any { it.code == KmpFlavorPluginValidator.CODE_MATRIX_MODE_WITHOUT_FLAVORS })
    }

    @Test
    fun `KMPF-V05 fires when matrix on but zero targets`() {
        val findings = KmpFlavorPluginValidator.validate(
            flavors = listOf(newFlavor("free")),
            buildTypes = emptyList(),
            resolvedVariants = listOf(variant("free")),
            matrixModeEnabled = true,
            detectedTargetCount = 0,
        )
        assertTrue(findings.any { it.code == KmpFlavorPluginValidator.CODE_ZERO_KMP_TARGETS })
    }

    @Test
    fun `KMPF-V06 fires for unknown requested variant`() {
        val findings = KmpFlavorPluginValidator.validate(
            flavors = listOf(newFlavor("free")),
            buildTypes = emptyList(),
            resolvedVariants = listOf(variant("free")),
            matrixModeEnabled = false,
            detectedTargetCount = 1,
            requestedVariantName = "ghost",
        )
        assertTrue(findings.any { it.code == KmpFlavorPluginValidator.CODE_UNKNOWN_ACTIVE_VARIANT })
    }

    @Test
    fun `KMPF-V01 fires for flavor-buildType name collision`() {
        val findings = KmpFlavorPluginValidator.validate(
            flavors = listOf(newFlavor("debug")),
            buildTypes = listOf(newBuildType("debug")),
            resolvedVariants = listOf(variant("debug")),
            matrixModeEnabled = false,
            detectedTargetCount = 1,
        )
        assertTrue(findings.any { it.code == KmpFlavorPluginValidator.CODE_FLAVOR_BUILD_TYPE_COLLISION })
    }

    @Test
    fun `KMPF-V02 fires for flavor without dimension when dimensions registered`() {
        val findings = KmpFlavorPluginValidator.validate(
            flavors = listOf(newFlavor("free"), newFlavor("paid", "tier")),
            buildTypes = emptyList(),
            resolvedVariants = listOf(variant("paid")),
            matrixModeEnabled = false,
            detectedTargetCount = 1,
            dimensions = listOf(newDim("tier")),
        )
        assertTrue(findings.any { it.code == KmpFlavorPluginValidator.CODE_FLAVOR_MISSING_DIMENSION })
    }

    @Test
    fun `KMPF-V03 fires for empty dimension`() {
        val findings = KmpFlavorPluginValidator.validate(
            flavors = listOf(newFlavor("free", "tier")),
            buildTypes = emptyList(),
            resolvedVariants = listOf(variant("free")),
            matrixModeEnabled = false,
            detectedTargetCount = 1,
            dimensions = listOf(newDim("tier"), newDim("env")),
        )
        assertTrue(findings.any {
            it.code == KmpFlavorPluginValidator.CODE_DIMENSION_HAS_NO_FLAVORS &&
                it.message.contains("'env'")
        })
    }

    @Test
    fun `KMPF-V04 fires when variantFilter excludes all variants`() {
        val findings = KmpFlavorPluginValidator.validate(
            flavors = listOf(newFlavor("free", "tier"), newFlavor("paid", "tier")),
            buildTypes = emptyList(),
            resolvedVariants = emptyList(),
            matrixModeEnabled = false,
            detectedTargetCount = 1,
            dimensions = listOf(newDim("tier")),
        )
        assertTrue(findings.any { it.code == KmpFlavorPluginValidator.CODE_VARIANT_FILTER_EXCLUDED_ALL })
    }

    @Test
    fun `KMPF-V23 fires on auto-derived BuildKonfig name collision`() {
        val flavor = newFlavor("free", "tier").apply {
            buildConfigField("Boolean", "IS_FREE", "true") // collides with auto-derived IS_FREE
        }
        val findings = KmpFlavorPluginValidator.validate(
            flavors = listOf(flavor),
            buildTypes = emptyList(),
            resolvedVariants = listOf(variant("free")),
            matrixModeEnabled = false,
            detectedTargetCount = 1,
            dimensions = listOf(newDim("tier")),
        )
        assertTrue(findings.any { it.code == KmpFlavorPluginValidator.CODE_BUILD_CONFIG_FIELD_AUTO_DERIVED_COLLISION })
    }

    @Test
    fun `KMPF-V23 fires on VARIANT_NAME collision`() {
        val flavor = newFlavor("free", "tier").apply {
            buildConfigField("String", "VARIANT_NAME", "\"x\"")
        }
        val findings = KmpFlavorPluginValidator.validate(
            flavors = listOf(flavor),
            buildTypes = emptyList(),
            resolvedVariants = listOf(variant("free")),
            matrixModeEnabled = false,
            detectedTargetCount = 1,
            dimensions = listOf(newDim("tier")),
        )
        assertTrue(findings.any {
            it.code == KmpFlavorPluginValidator.CODE_BUILD_CONFIG_FIELD_AUTO_DERIVED_COLLISION &&
                it.message.contains("VARIANT_NAME")
        })
    }

    @Test
    fun `KMPF-V23 fires on BUILD_TYPE collision via buildType`() {
        val bt = newBuildType("debug").apply { buildConfigField("String", "BUILD_TYPE", "\"x\"") }
        val findings = KmpFlavorPluginValidator.validate(
            flavors = listOf(newFlavor("free", "tier")),
            buildTypes = listOf(bt),
            resolvedVariants = listOf(variant("free")),
            matrixModeEnabled = false,
            detectedTargetCount = 1,
            dimensions = listOf(newDim("tier")),
        )
        assertTrue(findings.any {
            it.code == KmpFlavorPluginValidator.CODE_BUILD_CONFIG_FIELD_AUTO_DERIVED_COLLISION &&
                it.message.contains("BUILD_TYPE")
        })
    }

    @Test
    fun `KMPF-V07 fires for unsupported buildConfigField type`() {
        val flavor = newFlavor("free").apply {
            buildConfigField("CustomBigDecimal", "AMOUNT", "BigDecimal.ZERO")
        }
        val findings = KmpFlavorPluginValidator.validate(
            flavors = listOf(flavor),
            buildTypes = emptyList(),
            resolvedVariants = listOf(variant("free")),
            matrixModeEnabled = false,
            detectedTargetCount = 1,
        )
        assertTrue(findings.any { it.code == KmpFlavorPluginValidator.CODE_INVALID_BUILD_CONFIG_FIELD_TYPE })
    }

    @Test
    fun `validateBuildKonfigDsl V26 WARN on schema 2_0`() {
        val findings = KmpFlavorPluginValidator.validateBuildKonfigDsl(
            buildKonfigSecretIds = listOf("API_KEY"),
            secretsManifestSchemaVersion = "2.0",
        )
        assertTrue(findings.any {
            it.code == KmpFlavorPluginValidator.CODE_SECRET_RESOLUTION_FAIL &&
                it.severity == KmpFlavorValidationSeverity.WARNING
        })
    }

    @Test
    fun `validateBuildKonfigDsl V27 fires for unsupported customField type`() {
        val findings = KmpFlavorPluginValidator.validateBuildKonfigDsl(
            customFieldUnsupportedTypes = listOf("badField" to "Map<String,Int>"),
        )
        assertTrue(findings.any { it.code == KmpFlavorPluginValidator.CODE_CUSTOM_TYPE_EMIT_FAIL })
    }

    @Test
    fun `validateBuildKonfigDsl V28 fires for unknown perTarget`() {
        val findings = KmpFlavorPluginValidator.validateBuildKonfigDsl(
            perTargetNamesDeclared = setOf("iosMain", "phantomMain"),
            kotlinTargetNames = setOf("iosMain", "jvmMain"),
        )
        assertTrue(findings.any {
            it.code == KmpFlavorPluginValidator.CODE_PERTARGET_ON_NON_KMP &&
                it.message.contains("phantomMain")
        })
    }

    @Test
    fun `validateBuildKonfigDsl V29 fires for unknown baseUrl flavor`() {
        val findings = KmpFlavorPluginValidator.validateBuildKonfigDsl(
            buildKonfigBaseUrlFlavors = setOf("free", "ghost"),
            registeredFlavorNames = setOf("free", "paid"),
        )
        assertTrue(findings.any {
            it.code == KmpFlavorPluginValidator.CODE_BASE_URL_FLAVOR_MISSING &&
                it.message.contains("ghost")
        })
    }

    @Test
    fun `validateBuildKonfigDsl V30 fires when variant active flavor has no baseUrl`() {
        val findings = KmpFlavorPluginValidator.validateBuildKonfigDsl(
            buildKonfigBaseUrlFlavors = setOf("free"),
            variantActiveFlavors = mapOf("paidProd" to "paid"),
        )
        assertTrue(findings.any { it.code == KmpFlavorPluginValidator.CODE_BASE_URL_NOT_FOUND_FOR_VARIANT })
    }

    @Test
    fun `validatePlatformAndVersionCompatibility V15 fires for Apple Silicon + iosX64`() {
        val findings = KmpFlavorPluginValidator.validatePlatformAndVersionCompatibility(
            hostOsArch = "aarch64",
            gradleVersion = "8.5",
            kgpVersion = "2.1.0",
            cmpVersion = "1.7.0",
            declaredIosTargetNames = setOf("iosX64", "iosArm64"),
        )
        assertTrue(findings.any { it.code == KmpFlavorPluginValidator.CODE_IOS_ROSETTA_REQUIRED })
    }

    @Test
    fun `validatePlatformAndVersionCompatibility V16 fires for CMP and KGP incompat`() {
        val findings = KmpFlavorPluginValidator.validatePlatformAndVersionCompatibility(
            hostOsArch = "x86_64",
            gradleVersion = "8.5",
            kgpVersion = "2.2.0",
            cmpVersion = "1.6.0",
            declaredIosTargetNames = emptySet(),
        )
        assertTrue(findings.any { it.code == KmpFlavorPluginValidator.CODE_CMP_KGP_VERSION_INCOMPATIBLE })
    }

    @Test
    fun `validatePlatformAndVersionCompatibility V17 fires for KGP 2_0 + Gradle 8_4`() {
        val findings = KmpFlavorPluginValidator.validatePlatformAndVersionCompatibility(
            hostOsArch = "x86_64",
            gradleVersion = "8.4",
            kgpVersion = "2.0.0",
            cmpVersion = null,
            declaredIosTargetNames = emptySet(),
        )
        assertTrue(findings.any { it.code == KmpFlavorPluginValidator.CODE_KGP_GRADLE_VERSION_INCOMPATIBLE })
    }

    @Test
    fun `clean configuration produces zero findings`() {
        val findings = KmpFlavorPluginValidator.validate(
            flavors = listOf(newFlavor("free", "tier"), newFlavor("paid", "tier")),
            buildTypes = listOf(newBuildType("debug"), newBuildType("release")),
            resolvedVariants = listOf(variant("freeDebug"), variant("paidRelease")),
            matrixModeEnabled = false,
            detectedTargetCount = 2,
            dimensions = listOf(newDim("tier")),
        )
        assertEquals(emptyList<KmpFlavorValidationFinding>(), findings)
    }

    @Test
    fun `SUPPORTED_BUILD_CONFIG_FIELD_TYPES is stable`() {
        assertTrue(KmpFlavorPluginValidator.SUPPORTED_BUILD_CONFIG_FIELD_TYPES.containsAll(
            setOf("Boolean", "Int", "Long", "Float", "Double", "String")
        ))
    }
}
