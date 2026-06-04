/*
 * Copyright 2026 MobileByteLabs
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

package com.mobilebytelabs.kmpflavors.internal

import com.mobilebytelabs.kmpflavors.FlavorConfig
import com.mobilebytelabs.kmpflavors.KmpFlavorExtension
import com.mobilebytelabs.kmpflavors.SigningConfig
import org.gradle.api.Project
import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * v2.8 — Direct coverage for per-flavor `versionCode` + `versionName` reflective propagation
 * via [AgpProductFlavorRegistrar] and the [KmpFlavorPluginValidator.validateVersionAndSigning]
 * V50/V51 sub-checks.
 *
 * Mirrors the [AgpBridgeTest] / [SigningConfigBridgeTest] pattern with a fake AGP-shaped extension.
 * Sub-plan 01 (v28-gap-fill).
 */
class PerFlavorVersionPropagationTest {

    /** Fake AGP extension exposing productFlavors with versionCode/versionName setters. */
    class FakeAndroidExtension {
        val dimensions: MutableList<String> = mutableListOf()
        private val pf = FakeFlavorContainerWithVersions()

        @Suppress("unused")
        fun getFlavorDimensions(): MutableList<String> = dimensions

        @Suppress("unused")
        fun getProductFlavors(): FakeFlavorContainerWithVersions = pf
    }

    class FakeFlavorContainerWithVersions {
        val entries: MutableMap<String, FakeAgpFlavorWithVersions> = linkedMapOf()

        @Suppress("unused")
        fun findByName(name: String): FakeAgpFlavorWithVersions? = entries[name]

        @Suppress("unused")
        fun create(name: String): FakeAgpFlavorWithVersions = entries.getOrPut(name) { FakeAgpFlavorWithVersions(name) }
    }

    class FakeAgpFlavorWithVersions(@Suppress("unused") val name: String) {
        var dim: String? = null
        var vc: Int? = null
        var vn: String? = null

        @Suppress("unused")
        fun setDimension(d: String) { dim = d }

        @Suppress("unused")
        fun setVersionCode(value: Int) { vc = value }

        @Suppress("unused")
        fun setVersionName(value: String) { vn = value }
    }

    private fun project(): Project = ProjectBuilder.builder().build()
    private fun newExtension(p: Project): KmpFlavorExtension = p.objects.newInstance(KmpFlavorExtension::class.java)
    private fun newFlavor(p: Project, name: String, configure: FlavorConfig.() -> Unit = {}): FlavorConfig {
        val flv = p.objects.newInstance(FlavorConfig::class.java, name)
        flv.configure()
        return flv
    }
    private fun newSigningConfig(p: Project, name: String, configure: SigningConfig.() -> Unit = {}): SigningConfig {
        val sc = p.objects.newInstance(SigningConfig::class.java, name)
        sc.configure()
        return sc
    }

    @Test
    fun `versionCode and versionName propagate to AGP productFlavor when set`() {
        val proj = project()
        val ext = newExtension(proj)
        ext.flavorDimensions.add(proj.objects.newInstance(com.mobilebytelabs.kmpflavors.FlavorDimension::class.java, "environment"))
        val fakeAndroid = FakeAndroidExtension()
        proj.extensions.add("android", fakeAndroid)
        AgpProductFlavorRegistrar.apply(proj, ext, proj.logger)

        ext.flavors.add(
            newFlavor(proj, "demo") {
                dimension.set("environment")
                versionCode.set(100)
                versionName.set("0.1.0-demo")
            },
        )
        ext.flavors.add(
            newFlavor(proj, "prod") {
                dimension.set("environment")
                versionCode.set(280)
                versionName.set("2.8.0")
            },
        )

        val agpDemo = fakeAndroid.getProductFlavors().findByName("demo")
        val agpProd = fakeAndroid.getProductFlavors().findByName("prod")
        assertEquals(100, agpDemo?.vc, "demo flavor versionCode must reach AGP")
        assertEquals("0.1.0-demo", agpDemo?.vn, "demo flavor versionName must reach AGP")
        assertEquals(280, agpProd?.vc, "prod flavor versionCode must reach AGP")
        assertEquals("2.8.0", agpProd?.vn, "prod flavor versionName must reach AGP")
    }

    @Test
    fun `unset versionCode and versionName leave AGP slots untouched`() {
        val proj = project()
        val ext = newExtension(proj)
        ext.flavorDimensions.add(proj.objects.newInstance(com.mobilebytelabs.kmpflavors.FlavorDimension::class.java, "environment"))
        val fakeAndroid = FakeAndroidExtension()
        proj.extensions.add("android", fakeAndroid)
        AgpProductFlavorRegistrar.apply(proj, ext, proj.logger)

        ext.flavors.add(
            newFlavor(proj, "demo") {
                dimension.set("environment")
                // versionCode + versionName intentionally unset
            },
        )

        val agpDemo = fakeAndroid.getProductFlavors().findByName("demo")
        assertNull(agpDemo?.vc, "unset versionCode → AGP slot stays null (defers to defaultConfig)")
        assertNull(agpDemo?.vn, "unset versionName → AGP slot stays null (defers to defaultConfig)")
    }

    @Test
    fun `V50 fires on non-positive versionCode`() {
        val proj = project()
        val flv = newFlavor(proj, "zero") { versionCode.set(0) }
        val flv2 = newFlavor(proj, "neg") { versionCode.set(-7) }
        val findings = KmpFlavorPluginValidator.validateVersionAndSigning(
            flavors = listOf(flv, flv2),
            signingConfigs = emptyList(),
        )
        val v50s = findings.filter { it.code == FlavorValidationCodes.V50_VERSION_CODE_PROPAGATED }
        assertEquals(2, v50s.size, "V50 should fire for both zero and negative versionCodes")
        assertTrue(v50s.all { it.severity == KmpFlavorValidationSeverity.ERROR })
    }

    @Test
    fun `V50 does not fire on positive versionCode`() {
        val proj = project()
        val flv = newFlavor(proj, "ok") { versionCode.set(280) }
        val findings = KmpFlavorPluginValidator.validateVersionAndSigning(
            flavors = listOf(flv),
            signingConfigs = emptyList(),
        )
        assertTrue(
            findings.none { it.code == FlavorValidationCodes.V50_VERSION_CODE_PROPAGATED },
            "V50 should NOT fire on a positive versionCode",
        )
    }

    @Test
    fun `V51 fires on flavor referencing unknown signingConfig`() {
        val proj = project()
        val flv = newFlavor(proj, "prod") { signingConfig.set("ghost") }
        val findings = KmpFlavorPluginValidator.validateVersionAndSigning(
            flavors = listOf(flv),
            signingConfigs = emptyList(),
        )
        val v51 = findings.firstOrNull { it.code == FlavorValidationCodes.V51_SIGNING_ENV_VAR_SET }
        assertTrue(v51 != null, "V51 should fire when flavor references unknown signingConfig")
        assertTrue(v51!!.message.contains("ghost"))
    }

    @Test
    fun `V51 fires on signingConfig with empty passwords`() {
        val proj = project()
        val sc = newSigningConfig(proj, "release") { /* no passwords set */ }
        val flv = newFlavor(proj, "prod") { signingConfig.set("release") }
        val findings = KmpFlavorPluginValidator.validateVersionAndSigning(
            flavors = listOf(flv),
            signingConfigs = listOf(sc),
        )
        val v51 = findings.firstOrNull { it.code == FlavorValidationCodes.V51_SIGNING_ENV_VAR_SET }
        assertTrue(v51 != null, "V51 should fire when signingConfig has empty store+key passwords")
        assertTrue(v51!!.message.contains("storePassword"))
        assertTrue(v51.message.contains("keyPassword"))
    }

    @Test
    fun `V51 does not fire when signingConfig has both passwords set`() {
        val proj = project()
        val sc = newSigningConfig(proj, "release") {
            storePassword.set("pw")
            keyPassword.set("kw")
        }
        val flv = newFlavor(proj, "prod") { signingConfig.set("release") }
        val findings = KmpFlavorPluginValidator.validateVersionAndSigning(
            flavors = listOf(flv),
            signingConfigs = listOf(sc),
        )
        assertTrue(
            findings.none { it.code == FlavorValidationCodes.V51_SIGNING_ENV_VAR_SET },
            "V51 should NOT fire when both passwords are set",
        )
    }

    @Test
    fun `V51 fires when only storePassword is missing (key set)`() {
        val proj = project()
        val sc = newSigningConfig(proj, "release") {
            // storePassword unset (null) → storeMissing = true
            keyPassword.set("kw") // keyMissing = false
        }
        val flv = newFlavor(proj, "prod") { signingConfig.set("release") }
        val findings = KmpFlavorPluginValidator.validateVersionAndSigning(
            flavors = listOf(flv),
            signingConfigs = listOf(sc),
        )
        val v51 = findings.firstOrNull { it.code == FlavorValidationCodes.V51_SIGNING_ENV_VAR_SET }
        assertTrue(v51 != null, "V51 should fire when only storePassword is missing")
        assertTrue(v51!!.message.contains("storePassword"))
        // keyPassword should NOT appear in the missing parts list when it's set.
        assertTrue(!v51.message.contains("keyPassword + storePassword"))
    }

    @Test
    fun `V51 fires when only keyPassword is missing (store set)`() {
        val proj = project()
        val sc = newSigningConfig(proj, "release") {
            storePassword.set("pw") // storeMissing = false
            // keyPassword unset → keyMissing = true
        }
        val flv = newFlavor(proj, "prod") { signingConfig.set("release") }
        val findings = KmpFlavorPluginValidator.validateVersionAndSigning(
            flavors = listOf(flv),
            signingConfigs = listOf(sc),
        )
        val v51 = findings.firstOrNull { it.code == FlavorValidationCodes.V51_SIGNING_ENV_VAR_SET }
        assertTrue(v51 != null, "V51 should fire when only keyPassword is missing")
        assertTrue(v51!!.message.contains("keyPassword"))
    }

    @Test
    fun `flavor without versionCode or signingConfig produces no V50 or V51 findings`() {
        val proj = project()
        val flv = newFlavor(proj, "plain") { /* nothing set */ }
        val findings = KmpFlavorPluginValidator.validateVersionAndSigning(
            flavors = listOf(flv),
            signingConfigs = emptyList(),
        )
        assertTrue(findings.isEmpty(), "flavor with neither versionCode nor signingConfig → no findings")
    }

    @Test
    fun `SigningConfig storePasswordFromEnv with set env-var resolves password`() {
        // Verifies the env-var-driven password resolution helper path.
        // Use PATH which is essentially always set on POSIX systems.
        val proj = project()
        val sc = newSigningConfig(proj, "release") {
            storePasswordFromEnv("PATH")
            keyPasswordFromEnv("PATH")
        }
        assertTrue(
            sc.storePassword.orNull?.isNotEmpty() == true,
            "storePasswordFromEnv with set env-var must populate storePassword",
        )
        assertTrue(
            sc.keyPassword.orNull?.isNotEmpty() == true,
            "keyPasswordFromEnv with set env-var must populate keyPassword",
        )
    }

    @Test
    fun `SigningConfig storePasswordFromEnv with unset env-var leaves password unset`() {
        val proj = project()
        val sc = newSigningConfig(proj, "release") {
            storePasswordFromEnv("__DEFINITELY_NEVER_SET_KEYSTORE_PASSWORD_BBBB__")
            keyPasswordFromEnv("__DEFINITELY_NEVER_SET_KEY_PASSWORD_BBBB__")
        }
        assertNull(sc.storePassword.orNull, "unset env-var → storePassword stays null")
        assertNull(sc.keyPassword.orNull, "unset env-var → keyPassword stays null")
    }

    @Test
    fun `validateVersionAndSigning returns empty list when no flavors`() {
        val findings = KmpFlavorPluginValidator.validateVersionAndSigning(
            flavors = emptyList(),
            signingConfigs = emptyList(),
        )
        assertTrue(findings.isEmpty())
    }

    @Test
    fun `validateVersionAndSigning uses default empty signingConfigs when omitted`() {
        // Covers the default-value path on the signingConfigs parameter.
        val proj = project()
        val flv = newFlavor(proj, "ok") { versionCode.set(280) }
        val findings = KmpFlavorPluginValidator.validateVersionAndSigning(flavors = listOf(flv))
        assertTrue(findings.isEmpty(), "flavor with valid versionCode + default empty signingConfigs → no findings")
    }
}
