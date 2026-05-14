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
import com.mobilebytelabs.kmpflavors.internal.KmpFlavorPluginValidator.CODE_FLAVOR_BUILD_TYPE_COLLISION
import com.mobilebytelabs.kmpflavors.internal.KmpFlavorPluginValidator.CODE_MATRIX_MODE_WITHOUT_FLAVORS
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
}
