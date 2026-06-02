/*
 * Copyright 2026 MobileByteLabs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package com.mobilebytelabs.kmpflavors

import org.gradle.api.Action
import org.gradle.api.NamedDomainObjectContainer
import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class KmpFlavorExtensionTest {

    private fun newExtension(): KmpFlavorExtension =
        ProjectBuilder.builder().build().objects.newInstance(KmpFlavorExtension::class.java)

    @Test
    fun `generateBuildConfig convention is true`() {
        assertTrue(newExtension().generateBuildConfig.get())
    }

    @Test
    fun `buildConfigClassName convention is BuildKonfig`() {
        assertEquals("BuildKonfig", newExtension().buildConfigClassName.get())
    }

    @Test
    fun `createIntermediateSourceSets convention is true`() {
        assertTrue(newExtension().createIntermediateSourceSets.get())
    }

    @Test
    fun `createInactiveFlavorSourceSets convention is false`() {
        assertFalse(newExtension().createInactiveFlavorSourceSets.get())
    }

    @Test
    fun `enableBuildTypes convention is false`() {
        assertFalse(newExtension().enableBuildTypes.get())
    }

    @Test
    fun `bridgeAgpProductFlavors convention is true`() {
        assertTrue(newExtension().bridgeAgpProductFlavors.get())
    }

    @Test
    fun `bridgeAgpBuildTypes convention is true`() {
        assertTrue(newExtension().bridgeAgpBuildTypes.get())
    }

    @Test
    fun `autoEnable convention is true`() {
        assertTrue(newExtension().autoEnable.get())
    }

    @Test
    fun `Phase-4 helpers default to false`() {
        val e = newExtension()
        assertFalse(e.dependencyGuardPerVariant.get())
        assertFalse(e.excludeGeneratedFromFormatters.get())
        assertFalse(e.detektPerVariant.get())
        assertFalse(e.detektPerVariantPerTarget.get())
        assertFalse(e.variantCacheNamespacing.get())
        assertFalse(e.createIntermediateBuildTypeSourceSets.get())
        assertFalse(e.publishMatrixSbom.get())
        assertFalse(e.npmPublishMatrix.get())
    }

    @Test
    fun `publishMatrixLegacyIosClassifiers convention is true`() {
        assertTrue(newExtension().publishMatrixLegacyIosClassifiers.get())
    }

    @Test
    fun `flavorDimensions block marks legacyFlatDslUsed`() {
        val e = newExtension()
        assertFalse(e.legacyFlatDslUsed)
        e.flavorDimensions(Action<NamedDomainObjectContainer<FlavorDimension>> { register("tier") })
        assertTrue(e.legacyFlatDslUsed)
    }

    @Test
    fun `flavors block marks legacyFlatDslUsed`() {
        val e = newExtension()
        e.flavors(Action<NamedDomainObjectContainer<FlavorConfig>> { register("free") })
        assertTrue(e.legacyFlatDslUsed)
    }

    @Test
    fun `buildTypes block populates the container`() {
        val e = newExtension()
        e.buildTypes(
            Action<NamedDomainObjectContainer<BuildTypeConfig>> {
                register("debug")
                register("release")
            },
        )
        assertEquals(setOf("debug", "release"), e.buildTypes.names)
    }

    @Test
    fun `variantFilter appends to variantFilterActions`() {
        val e = newExtension()
        assertEquals(0, e.variantFilterActions.size)
        e.variantFilter(Action { exclude() })
        e.variantFilter(Action { setIgnore(true) })
        assertEquals(2, e.variantFilterActions.size)
    }

    @Test
    fun `promote appends to variantPromotions and returns the promotion`() {
        val e = newExtension()
        val promotion = e.promote("freeDev", "freeStaging")
        assertEquals("freeDev", promotion.from)
        assertEquals("freeStaging", promotion.to)
        assertEquals(1, e.variantPromotions.size)
        assertTrue(e.variantPromotions.contains(promotion))
    }

    @Test
    fun `promote with action populates transforms`() {
        val e = newExtension()
        val promotion = e.promote("a", "b") {
            applyTransform("renamePackage", "com.x" to "com.y")
            copyResources(false)
            copyTests(false)
        }
        assertEquals(1, promotion.transforms.size)
        assertEquals("renamePackage", promotion.transforms[0].kind)
        assertEquals(false, promotion.copyResources)
        assertEquals(false, promotion.copyTests)
    }

    @Test
    fun `spm block delegates to SpmConfig`() {
        val e = newExtension()
        e.spm(Action<SpmConfig> { generateManifest.set(true) })
        assertTrue(e.spm.generateManifest.get())
    }

    @Test
    fun `featureFlags block delegates to FeatureFlagsConfig`() {
        val e = newExtension()
        e.featureFlags(Action<FeatureFlagsConfig> { growthbook(Action<GrowthBookConfig> { enabled.set(true) }) })
        assertTrue(e.featureFlags.growthbook.enabled.get())
    }

    @Test
    fun `di block delegates to DiDsl`() {
        val e = newExtension()
        assertNotNull(e.di.get())
        e.di {
            koin(
                Action {
                    variantModule(
                        "network",
                        Action {
                            "free" { single("FreeFactory()") }
                        },
                    )
                },
            )
        }
        assertTrue(e.di.get().koin.variantModules.containsKey("network"))
    }

    @Test
    fun `analytics block delegates to AnalyticsTagsConfig`() {
        val e = newExtension()
        e.analytics(Action<AnalyticsTagsConfig> { enabled.set(true) })
        assertTrue(e.analytics.enabled.get())
    }

    @Test
    fun `dimensions block marks dimensionsDslUsed`() {
        val e = newExtension()
        assertFalse(e.dimensionsDslUsed)
        e.dimensions { dimension("tier") { flavor("free") } }
        assertTrue(e.dimensionsDslUsed)
    }

    @Test
    fun `buildKonfig block returns and accumulates`() {
        val e = newExtension()
        e.buildKonfig { secret("API_KEY") }
        e.buildKonfig { secret("AUTH_TOKEN") }
        assertEquals(2, e.buildKonfigDsl.secrets.size)
    }

    @Test
    fun `internal mutex setter functions are exercised`() {
        val e = newExtension()
        e.dimensionsDslUsed = true
        e.legacyFlatDslUsed = true
        assertTrue(e.dimensionsDslUsed)
        assertTrue(e.legacyFlatDslUsed)
    }
}
