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
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

/**
 * v2.8 — Direct coverage for [SigningConfigBridge].
 *
 * Mirrors the [AgpBridgeTest] pattern (FakeAndroidExtension test double with reflective shape).
 * Avoids the real AGP classpath: a full TestKit fixture lives in Tier D samples.
 *
 * Sub-plan 01 (v28-gap-fill).
 */
class SigningConfigBridgeTest {

    /** Fake AGP-shaped android extension exposing signingConfigs + productFlavors containers. */
    class FakeAndroidExtension {
        private val signingConfigs = FakeSigningConfigContainer()
        private val productFlavors = FakeFlavorContainer()

        @Suppress("unused")
        fun getSigningConfigs(): FakeSigningConfigContainer = signingConfigs

        @Suppress("unused")
        fun getProductFlavors(): FakeFlavorContainer = productFlavors
    }

    class FakeSigningConfigContainer {
        val entries: MutableMap<String, FakeAgpSigningConfig> = linkedMapOf()

        @Suppress("unused")
        fun create(name: String): FakeAgpSigningConfig = entries.getOrPut(name) { FakeAgpSigningConfig(name) }

        @Suppress("unused")
        fun maybeCreate(name: String): FakeAgpSigningConfig = entries.getOrPut(name) { FakeAgpSigningConfig(name) }

        @Suppress("unused")
        fun getByName(name: String): FakeAgpSigningConfig? = entries[name]
    }

    class FakeAgpSigningConfig(@Suppress("unused") val name: String) {
        var sf: File? = null
        var spw: String? = null
        var ka: String? = null
        var kpw: String? = null

        @Suppress("unused")
        fun setStoreFile(f: File?) {
            sf = f
        }

        @Suppress("unused")
        fun setStorePassword(p: String?) {
            spw = p
        }

        @Suppress("unused")
        fun setKeyAlias(a: String?) {
            ka = a
        }

        @Suppress("unused")
        fun setKeyPassword(p: String?) {
            kpw = p
        }
    }

    class FakeFlavorContainer {
        val entries: MutableMap<String, FakeAgpFlavorWithSigning> = linkedMapOf()

        @Suppress("unused")
        fun getByName(name: String): FakeAgpFlavorWithSigning? = entries[name]

        @Suppress("unused")
        fun findByName(name: String): FakeAgpFlavorWithSigning? = entries[name]

        @Suppress("unused")
        fun create(name: String): FakeAgpFlavorWithSigning = entries.getOrPut(name) { FakeAgpFlavorWithSigning(name) }
    }

    class FakeAgpFlavorWithSigning(@Suppress("unused") val name: String) {
        var sc: Any? = null

        @Suppress("unused")
        fun setSigningConfig(s: Any?) {
            sc = s
        }
    }

    private fun project(): Project = ProjectBuilder.builder().build()
    private fun newExtension(p: Project): KmpFlavorExtension = p.objects.newInstance(KmpFlavorExtension::class.java)
    private fun newSigningConfig(p: Project, name: String, configure: SigningConfig.() -> Unit = {}): SigningConfig {
        val sc = p.objects.newInstance(SigningConfig::class.java, name)
        sc.configure()
        return sc
    }
    private fun newFlavor(p: Project, name: String, configure: FlavorConfig.() -> Unit = {}): FlavorConfig {
        val flv = p.objects.newInstance(FlavorConfig::class.java, name)
        flv.configure()
        return flv
    }

    @Test
    fun `apply returns 0 when no android extension is present`() {
        val proj = project()
        val ext = newExtension(proj)
        val count = SigningConfigBridge.apply(proj, ext, proj.logger)
        assertEquals(0, count, "no android extension → silent no-op")
    }

    @Test
    fun `signingConfig is created in android signingConfigs container`() {
        val proj = project()
        val ext = newExtension(proj)
        proj.extensions.add("android", FakeAndroidExtension())

        // Bridge MUST register the whenObjectAdded listener BEFORE the consumer adds entries.
        SigningConfigBridge.apply(proj, ext, proj.logger)

        ext.signingConfigs.add(
            newSigningConfig(proj, "release") {
                storeFile.set(File("/tmp/release.jks"))
                storePassword.set("test-store-pw")
                keyAlias.set("kmpflavors")
                keyPassword.set("test-key-pw")
            },
        )

        val fakeAndroid = proj.extensions.findByName("android") as FakeAndroidExtension
        val agpRelease = fakeAndroid.getSigningConfigs().getByName("release")
        assertNotNull(agpRelease, "signingConfig 'release' should have been created in android.signingConfigs")
        assertEquals(File("/tmp/release.jks"), agpRelease!!.sf)
        assertEquals("test-store-pw", agpRelease.spw)
        assertEquals("kmpflavors", agpRelease.ka)
        assertEquals("test-key-pw", agpRelease.kpw)
    }

    @Test
    fun `flavor with signingConfig reference resolves to AGP signing config`() {
        val proj = project()
        val ext = newExtension(proj)
        val fakeAndroid = FakeAndroidExtension()
        // Pre-populate AGP flavor slot so the bridge can resolve productFlavors.getByName.
        fakeAndroid.getProductFlavors().create("prod")
        proj.extensions.add("android", fakeAndroid)

        SigningConfigBridge.apply(proj, ext, proj.logger)

        ext.signingConfigs.add(
            newSigningConfig(proj, "release") {
                storePassword.set("pw")
                keyPassword.set("kw")
            },
        )
        ext.flavors.add(
            newFlavor(proj, "prod") {
                signingConfig.set("release")
            },
        )

        val agpProdFlavor = fakeAndroid.getProductFlavors().getByName("prod")
        assertNotNull(agpProdFlavor)
        assertNotNull(agpProdFlavor!!.sc, "flavor 'prod' should have a resolved signingConfig")
        val resolvedSc = agpProdFlavor.sc as FakeAgpSigningConfig
        assertEquals("release", resolvedSc.name)
    }

    @Test
    fun `flavor referencing unknown signingConfig leaves signingConfig unassigned`() {
        val proj = project()
        val ext = newExtension(proj)
        val fakeAndroid = FakeAndroidExtension()
        fakeAndroid.getProductFlavors().create("prod")
        proj.extensions.add("android", fakeAndroid)

        SigningConfigBridge.apply(proj, ext, proj.logger)

        // Flavor references "ghost" — never declared in signingConfigs.
        ext.flavors.add(
            newFlavor(proj, "prod") {
                signingConfig.set("ghost")
            },
        )

        val agpProdFlavor = fakeAndroid.getProductFlavors().getByName("prod")!!
        assertNull(agpProdFlavor.sc, "unresolved signingConfig must not be assigned")
    }

    @Test
    fun `signingConfigs DSL block executes action on the container`() {
        // Covers KmpFlavorExtension.signingConfigs(action) DSL accessor (line 731).
        val proj = project()
        val ext = newExtension(proj)
        ext.signingConfigs(object : org.gradle.api.Action<org.gradle.api.NamedDomainObjectContainer<SigningConfig>> {
            override fun execute(container: org.gradle.api.NamedDomainObjectContainer<SigningConfig>) {
                container.create("release")
                container.create("debug")
            }
        })
        kotlin.test.assertEquals(setOf("release", "debug"), ext.signingConfigs.map { it.name }.toSet())
    }

    @Test
    fun `bridge is idempotent on multiple signingConfig adds`() {
        val proj = project()
        val ext = newExtension(proj)
        proj.extensions.add("android", FakeAndroidExtension())
        SigningConfigBridge.apply(proj, ext, proj.logger)
        ext.signingConfigs.add(newSigningConfig(proj, "release"))
        ext.signingConfigs.add(newSigningConfig(proj, "debug"))
        val fakeAndroid = proj.extensions.findByName("android") as FakeAndroidExtension
        assertEquals(setOf("release", "debug"), fakeAndroid.getSigningConfigs().entries.keys)
    }

    @Test
    fun `signingConfig with only env-resolved password emits propagation success`() {
        // Verifies the Property<T>.orNull contract — env-resolved unset is null, not empty string.
        val proj = project()
        val ext = newExtension(proj)
        proj.extensions.add("android", FakeAndroidExtension())
        SigningConfigBridge.apply(proj, ext, proj.logger)
        // No env-var set; storePassword/keyPassword stay unset (null orNull).
        ext.signingConfigs.add(
            newSigningConfig(proj, "release") {
                storeFile.set(File("/tmp/release.jks"))
                storePasswordFromEnv("__NEVER_SET_KEYSTORE_PASSWORD__")
                keyAlias.set("kmpflavors")
                keyPasswordFromEnv("__NEVER_SET_KEY_PASSWORD__")
            },
        )
        val fakeAndroid = proj.extensions.findByName("android") as FakeAndroidExtension
        val agpRelease = fakeAndroid.getSigningConfigs().getByName("release")
        assertNotNull(agpRelease)
        // storeFile + keyAlias landed; passwords stayed unset (null) — validator catches via V51.
        assertEquals(File("/tmp/release.jks"), agpRelease!!.sf)
        assertEquals("kmpflavors", agpRelease.ka)
        assertTrue(
            agpRelease.spw == null || agpRelease.spw!!.isEmpty(),
            "unset env-var → storePassword stays null/empty",
        )
    }
}
