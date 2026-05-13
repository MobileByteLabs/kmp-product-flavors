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

import com.mobilebytelabs.kmpflavors.internal.MatrixModeResolver
import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Drives v2.0 RFC §3 Q16-C (hybrid opt-in): `gradle.properties` sets the
 * canonical default, the extension property overrides per-project. Both
 * sit at a SINGLE consumer touch-point — no per-module diff.
 */
class MatrixModeResolverTest {

    private fun newProject(properties: Map<String, String> = emptyMap()): org.gradle.api.Project {
        val project = ProjectBuilder.builder().build()
        properties.forEach { (k, v) -> project.extensions.extraProperties[k] = v }
        return project
    }

    private fun ext(project: org.gradle.api.Project): KmpFlavorExtension =
        project.extensions.create("kmpFlavors", KmpFlavorExtension::class.java)

    @Test
    fun `default off — neither extension nor property set, resolver returns false`() {
        val project = newProject()
        val extension = ext(project)

        assertFalse(MatrixModeResolver.isEnabled(project, extension))
    }

    @Test
    fun `extension buildMatrix set true enables matrix mode`() {
        val project = newProject()
        val extension = ext(project).apply { buildMatrix.set(true) }

        assertTrue(MatrixModeResolver.isEnabled(project, extension))
    }

    @Test
    fun `extension buildMatrix set false keeps matrix mode off`() {
        val project = newProject()
        val extension = ext(project).apply { buildMatrix.set(false) }

        assertFalse(MatrixModeResolver.isEnabled(project, extension))
    }

    @Test
    fun `gradle property kmpFlavors_buildMatrix true enables matrix mode`() {
        val project = newProject(mapOf("kmpFlavors.buildMatrix" to "true"))
        val extension = ext(project)

        assertTrue(MatrixModeResolver.isEnabled(project, extension))
    }

    @Test
    fun `gradle property false with extension true — extension wins (Q16-C extension overrides property)`() {
        val project = newProject(mapOf("kmpFlavors.buildMatrix" to "false"))
        val extension = ext(project).apply { buildMatrix.set(true) }

        // Per RFC §3 Q16-C: "convention-plugin extension can override per-project for special cases"
        assertTrue(MatrixModeResolver.isEnabled(project, extension))
    }

    @Test
    fun `gradle property garbage value treated as false`() {
        val project = newProject(mapOf("kmpFlavors.buildMatrix" to "yes-please"))
        val extension = ext(project)

        assertFalse(MatrixModeResolver.isEnabled(project, extension))
    }
}
