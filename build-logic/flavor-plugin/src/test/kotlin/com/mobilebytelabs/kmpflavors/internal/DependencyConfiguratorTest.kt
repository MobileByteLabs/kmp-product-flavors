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
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DependencyConfiguratorTest {

    private fun project() = ProjectBuilder.builder().build()
    private fun newFlavor(name: String): FlavorConfig = project().objects.newInstance(FlavorConfig::class.java, name)

    @Test
    fun `no-op when activeVariant has no dependencies`() {
        val proj = project()
        val variant = FlavorVariant("free", listOf(newFlavor("free")))
        DependencyConfigurator(proj.logger).configure(proj, variant)
        assertTrue(true)
    }

    @Test
    fun `try-catch swallows invalid configuration name`() {
        val proj = project()
        val free = newFlavor("free").apply {
            dependency("nonExistentConfiguration", "com.example:lib:1.0")
        }
        val variant = FlavorVariant("free", listOf(free))
        // Should not throw — invalid configuration is logged warn but caught.
        DependencyConfigurator(proj.logger).configure(proj, variant)
        assertTrue(true)
    }
}
