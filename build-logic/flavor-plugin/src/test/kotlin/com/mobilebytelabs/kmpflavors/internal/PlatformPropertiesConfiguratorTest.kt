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

import com.mobilebytelabs.kmpflavors.FlavorConfig
import com.mobilebytelabs.kmpflavors.FlavorVariant
import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test

class PlatformPropertiesConfiguratorTest {

    private fun newFlavor(name: String): FlavorConfig =
        ProjectBuilder.builder().build().objects.newInstance(FlavorConfig::class.java, name)

    @Test
    fun `extras include variant name and flavor list`() {
        val project = ProjectBuilder.builder().build()
        val variant = FlavorVariant(
            name = "freeDev",
            flavors = listOf(newFlavor("free"), newFlavor("dev")),
        )
        PlatformPropertiesConfigurator(project.logger).configure(project, variant)
        val extras = project.extensions.extraProperties
        assertEquals("freeDev", extras.get("kmpFlavor.variantName"))
        assertEquals(listOf("free", "dev"), extras.get("kmpFlavor.flavorNames"))
    }

    @Test
    fun `applicationIdSuffix set when present`() {
        val project = ProjectBuilder.builder().build()
        val free = newFlavor("free").apply { applicationIdSuffix.set(".free") }
        val dev = newFlavor("dev").apply { applicationIdSuffix.set(".dev") }
        val variant = FlavorVariant("freeDev", listOf(free, dev))
        PlatformPropertiesConfigurator(project.logger).configure(project, variant)
        assertEquals(".free.dev", project.extensions.extraProperties.get("kmpFlavor.applicationIdSuffix"))
    }

    @Test
    fun `applicationIdSuffix unset when none of the flavors declare it`() {
        val project = ProjectBuilder.builder().build()
        val variant = FlavorVariant("free", listOf(newFlavor("free")))
        PlatformPropertiesConfigurator(project.logger).configure(project, variant)
        assertFalse(project.extensions.extraProperties.has("kmpFlavor.applicationIdSuffix"))
    }

    @Test
    fun `bundleIdSuffix set when present`() {
        val project = ProjectBuilder.builder().build()
        val free = newFlavor("free").apply { bundleIdSuffix.set(".free") }
        val variant = FlavorVariant("free", listOf(free))
        PlatformPropertiesConfigurator(project.logger).configure(project, variant)
        assertEquals(".free", project.extensions.extraProperties.get("kmpFlavor.bundleIdSuffix"))
    }

    @Test
    fun `versionNameSuffix set when present`() {
        val project = ProjectBuilder.builder().build()
        val free = newFlavor("free").apply { versionNameSuffix.set("-free") }
        val variant = FlavorVariant("free", listOf(free))
        PlatformPropertiesConfigurator(project.logger).configure(project, variant)
        assertEquals("-free", project.extensions.extraProperties.get("kmpFlavor.versionNameSuffix"))
    }

    @Test
    fun `desktopTitleSuffix set when present`() {
        val project = ProjectBuilder.builder().build()
        val free = newFlavor("free").apply { desktopWindowTitleSuffix.set("(Free)") }
        val variant = FlavorVariant("free", listOf(free))
        PlatformPropertiesConfigurator(project.logger).configure(project, variant)
        assertEquals("(Free)", project.extensions.extraProperties.get("kmpFlavor.desktopTitleSuffix"))
    }

    @Test
    fun `webTitleSuffix set when present`() {
        val project = ProjectBuilder.builder().build()
        val free = newFlavor("free").apply { webTitleSuffix.set("[Free]") }
        val variant = FlavorVariant("free", listOf(free))
        PlatformPropertiesConfigurator(project.logger).configure(project, variant)
        assertEquals("[Free]", project.extensions.extraProperties.get("kmpFlavor.webTitleSuffix"))
    }

    @Test
    fun `mergedExtras prefixed with kmpFlavor_extra`() {
        val project = ProjectBuilder.builder().build()
        val free = newFlavor("free").apply { extras.put("region", "us") }
        val dev = newFlavor("dev").apply { extras.put("env", "qa") }
        val variant = FlavorVariant("freeDev", listOf(free, dev))
        PlatformPropertiesConfigurator(project.logger).configure(project, variant)
        val extras = project.extensions.extraProperties
        assertEquals("us", extras.get("kmpFlavor.extra.region"))
        assertEquals("qa", extras.get("kmpFlavor.extra.env"))
    }

    @Test
    fun `android extension absent is silent no-op`() {
        val project = ProjectBuilder.builder().build()
        val variant = FlavorVariant("free", listOf(newFlavor("free")))
        // No android extension applied — should not throw.
        PlatformPropertiesConfigurator(project.logger).configure(project, variant)
    }
}
