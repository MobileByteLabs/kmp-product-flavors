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

import com.mobilebytelabs.kmpflavors.KmpFlavorExtension
import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MatrixModeResolverTest {

    private fun newExtension(): KmpFlavorExtension = ProjectBuilder.builder().build().objects.newInstance(KmpFlavorExtension::class.java)

    @Test
    fun `explicit extension buildMatrix true wins over everything`() {
        val project = ProjectBuilder.builder().build()
        val ext = newExtension()
        ext.buildMatrix.set(true)
        assertTrue(MatrixModeResolver.isEnabled(project, ext))
    }

    @Test
    fun `explicit extension buildMatrix false wins over property and heuristic`() {
        val project = ProjectBuilder.builder().build()
        val ext = newExtension()
        ext.buildMatrix.set(false)
        assertFalse(MatrixModeResolver.isEnabled(project, ext, nonAndroidTargetCount = 10, flavorCount = 10))
    }

    @Test
    fun `Gradle property true enables when extension unset`() {
        val project = ProjectBuilder.builder().withName("p").build()
        project.extensions.extraProperties.set(MatrixModeResolver.GRADLE_PROPERTY, "true")
        val ext = newExtension()
        assertTrue(MatrixModeResolver.isEnabled(project, ext))
    }

    @Test
    fun `Gradle property anything-but-true returns false`() {
        val project = ProjectBuilder.builder().withName("p").build()
        project.extensions.extraProperties.set(MatrixModeResolver.GRADLE_PROPERTY, "yes")
        val ext = newExtension()
        // raw != null, so the property branch fires; "yes" is not literally "true".
        assertFalse(MatrixModeResolver.isEnabled(project, ext))
    }

    @Test
    fun `auto-heuristic enables when both thresholds met`() {
        val project = ProjectBuilder.builder().build()
        val ext = newExtension()
        // extension unset + no property set → falls through to auto-heuristic.
        assertTrue(MatrixModeResolver.isEnabled(project, ext, nonAndroidTargetCount = 2, flavorCount = 2))
    }

    @Test
    fun `auto-heuristic is false when targets below threshold`() {
        val project = ProjectBuilder.builder().build()
        val ext = newExtension()
        assertFalse(MatrixModeResolver.isEnabled(project, ext, nonAndroidTargetCount = 1, flavorCount = 5))
    }

    @Test
    fun `auto-heuristic is false when flavors below threshold`() {
        val project = ProjectBuilder.builder().build()
        val ext = newExtension()
        assertFalse(MatrixModeResolver.isEnabled(project, ext, nonAndroidTargetCount = 5, flavorCount = 1))
    }

    @Test
    fun `auto-heuristic disabled when autoEnable is false`() {
        val project = ProjectBuilder.builder().build()
        val ext = newExtension()
        ext.autoEnable.set(false)
        assertFalse(MatrixModeResolver.isEnabled(project, ext, nonAndroidTargetCount = 10, flavorCount = 10))
    }

    @Test
    fun `shouldAutoEnable pure function honors extension autoEnable`() {
        val ext = newExtension()
        assertTrue(MatrixModeResolver.shouldAutoEnable(ext, 2, 2))
        ext.autoEnable.set(false)
        assertFalse(MatrixModeResolver.shouldAutoEnable(ext, 5, 5))
    }

    @Test
    fun `threshold constants are stable`() {
        assertEquals(2, MatrixModeResolver.AUTO_TARGET_THRESHOLD)
        assertEquals(2, MatrixModeResolver.AUTO_FLAVOR_THRESHOLD)
        assertEquals("kmpFlavors.buildMatrix", MatrixModeResolver.GRADLE_PROPERTY)
    }
}
