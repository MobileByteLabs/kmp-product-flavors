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

import org.gradle.testkit.runner.GradleRunner
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

/**
 * v2.9.2 — a DEFAULTED SPM setup must stay silent on modules that do not distribute an
 * XCFramework.
 *
 * v2.9.0 flipped `spm.generateManifest` to default `true`, gated only on "module declares an
 * iOS target". That is true of every KMP LIBRARY module too — and those publish klibs, never
 * an XCFramework aggregate. Each such module therefore hit the no-producer path and emitted a
 * `logger.warn` PER VARIANT: a 6-variant matrix across a handful of library modules turns
 * into dozens of warnings for a feature the consumer never asked for. Real consumers apply
 * the flavor convention across their whole module graph, so this scaled badly.
 *
 * The contract now:
 *   * `generateManifest` UNSET  → AUTO. Generate when a producer exists; otherwise skip in
 *                                 SILENCE (info level). Nothing was asked for, so nothing is
 *                                 warned about.
 *   * `generateManifest = true` → EXPLICIT. The consumer asked for SPM, so a missing producer
 *                                 IS worth a warning — it means their manifest will not be
 *                                 generated and they need to know why.
 *   * `generateManifest = false`→ off entirely.
 */
class SpmAutoDefaultQuietTest {

    @TempDir
    lateinit var testProjectDir: File

    private fun writeBuild(spmBlock: String) {
        File(testProjectDir, "settings.gradle.kts").writeText(
            """
            pluginManagement { repositories { gradlePluginPortal(); mavenCentral(); google() } }
            dependencyResolutionManagement { repositories { mavenCentral(); google() } }
            rootProject.name = "spm-quiet"
            """.trimIndent(),
        )
        // A KMP LIBRARY module: iOS targets, but no XCFramework aggregate — it publishes
        // klibs. This is the shape that was being warned at.
        File(testProjectDir, "build.gradle.kts").writeText(
            """
            plugins {
                kotlin("multiplatform") version "${TestToolchainVersions.kgp}"
                id("io.github.mobilebytelabs.kmp-product-flavors")
            }
            kotlin {
                iosArm64()
                iosSimulatorArm64()
            }
            kmpFlavors {
                generateBuildConfig.set(false)
                flavors {
                    register("free") { isDefault.set(true) }
                    register("paid")
                }
                $spmBlock
            }
            """.trimIndent(),
        )
    }

    @BeforeEach
    fun setup() = writeBuild("")

    private fun configureOutput(): String = GradleRunner.create()
        .withProjectDir(testProjectDir)
        .withArguments("help", "--stacktrace")
        .withPluginClasspath()
        .build()
        .output

    @Test
    fun `a klib-publishing library module is NOT warned at when SPM was never requested`() {
        val output = configureOutput()
        assertFalse(
            output.contains("would reference an XCFramework"),
            "A module that never asked for SPM must not be warned about a missing " +
                "XCFramework producer:\n$output",
        )
    }

    @Test
    fun `explicitly enabling SPM without a producer DOES warn`() {
        writeBuild("spm { generateManifest.set(true) }")
        val output = configureOutput()
        assertTrue(
            output.contains("would reference an XCFramework"),
            "An explicit opt-in with no producer must still tell the consumer why no " +
                "manifest appeared:\n$output",
        )
    }

    @Test
    fun `explicitly disabling SPM is silent too`() {
        writeBuild("spm { generateManifest.set(false) }")
        val output = configureOutput()
        assertFalse(output.contains("would reference an XCFramework"), output)
    }
}
