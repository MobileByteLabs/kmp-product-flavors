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

import org.gradle.testkit.runner.GradleRunner
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

/**
 * v2.1 Phase 4 — RFC §3 G22 verification.
 *
 * `./gradlew generateVariantRunConfigurations` emits one `.run.xml` per
 * (variant × target) under `.run/`. Sibling to the existing
 * `generateRunConfigurations` which is one-config-per-variant scoped to
 * `assemble`.
 */
class GenerateVariantRunConfigurationsTaskTest {

    @TempDir
    lateinit var testProjectDir: File

    private lateinit var buildFile: File
    private lateinit var settingsFile: File

    @BeforeEach
    fun setup() {
        settingsFile = File(testProjectDir, "settings.gradle.kts")
        buildFile = File(testProjectDir, "build.gradle.kts")
        settingsFile.writeText(
            """
            pluginManagement {
                repositories {
                    gradlePluginPortal()
                    mavenCentral()
                    google()
                }
            }
            dependencyResolutionManagement {
                repositories {
                    mavenCentral()
                    google()
                }
            }
            rootProject.name = "variant-run-configs-test"
            """.trimIndent(),
        )
        // 2 flavors × 2 targets = 4 (variant × target) run configurations.
        buildFile.writeText(
            """
            plugins {
                kotlin("multiplatform") version "2.2.21"
                id("io.github.mobilebytelabs.kmp-product-flavors")
            }
            kotlin {
                jvm("desktop")
                jvm("server")
            }
            kmpFlavors {
                buildMatrix.set(true)
                generateBuildConfig.set(false)
                flavors {
                    register("free") { isDefault.set(true) }
                    register("paid")
                }
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `generateVariantRunConfigurations emits N variants × M targets run configs`() {
        val result = GradleRunner.create()
            .withProjectDir(testProjectDir)
            .withArguments("generateVariantRunConfigurations")
            .withPluginClasspath()
            .build()

        assertTrue(
            result.output.contains("BUILD SUCCESSFUL"),
            "generateVariantRunConfigurations must succeed:\n${result.output}",
        )
        // Each of the 4 (variant × target) pairs should produce its own .run.xml.
        val runDir = File(testProjectDir, ".run")
        assertTrue(runDir.isDirectory, "Expected .run directory to be created at ${runDir.absolutePath}")
        val xmlFiles = runDir.listFiles { _, name -> name.endsWith(".run.xml") }?.toList().orEmpty()
        val variantTargetPairs = listOf(
            "free_desktop",
            "free_server",
            "paid_desktop",
            "paid_server",
        )
        variantTargetPairs.forEach { pair ->
            assertTrue(
                xmlFiles.any { it.name.contains(pair) },
                "Expected a .run.xml file matching '$pair'; got: ${xmlFiles.map { it.name }}",
            )
        }
        assertEquals(
            4,
            xmlFiles.size,
            "Expected exactly 4 (variant × target) run configs; got: ${xmlFiles.map { it.name }}",
        )
    }

    @Test
    fun `active variant's run config uses compileKotlin{Target} (not compile{Variant}KotlinTarget)`() {
        GradleRunner.create()
            .withProjectDir(testProjectDir)
            .withArguments("generateVariantRunConfigurations")
            .withPluginClasspath()
            .build()

        val runDir = File(testProjectDir, ".run")
        val activeFreeDesktop = runDir.listFiles { _, name -> name.contains("free_desktop") }?.firstOrNull()
        requireNotNull(activeFreeDesktop) { "free_desktop run config must exist" }
        val xmlContent = activeFreeDesktop.readText()
        assertTrue(
            xmlContent.contains("compileKotlinDesktop"),
            "Active variant must invoke compileKotlinDesktop (KGP's standard task):\n$xmlContent",
        )
        // No per-variant task name for the active variant — would collide with v1.x wiring.
        assertFalse(
            xmlContent.contains("compileFreeKotlinDesktop"),
            "Active variant must NOT invoke compileFreeKotlinDesktop:\n$xmlContent",
        )
        // -PkmpFlavor still flows through so cross-tooling sees the variant identity.
        assertTrue(xmlContent.contains("-PkmpFlavor=free"))
    }

    @Test
    fun `inactive variant's run config uses compile{Variant}Kotlin{Target}`() {
        GradleRunner.create()
            .withProjectDir(testProjectDir)
            .withArguments("generateVariantRunConfigurations")
            .withPluginClasspath()
            .build()

        val runDir = File(testProjectDir, ".run")
        val paidDesktop = runDir.listFiles { _, name -> name.contains("paid_desktop") }?.firstOrNull()
        requireNotNull(paidDesktop) { "paid_desktop run config must exist" }
        val xmlContent = paidDesktop.readText()
        assertTrue(
            xmlContent.contains("compilePaidKotlinDesktop"),
            "Inactive variant must invoke compilePaidKotlinDesktop:\n$xmlContent",
        )
        assertTrue(xmlContent.contains("-PkmpFlavor=paid"))
    }
}
