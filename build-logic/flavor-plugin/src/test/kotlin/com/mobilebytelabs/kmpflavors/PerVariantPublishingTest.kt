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
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

/**
 * W4.1 — RFC §3 Q21-D per-variant Maven publishing mechanism.
 *
 * When `kmpFlavors.publishMatrix.set(true)` AND the consumer applies
 * `maven-publish`, the plugin registers a `MavenPublication` named
 * `variant{Variant}` per inactive variant on each JVM target, with a
 * classifier-tagged Jar artifact. Gradle's maven-publish derives the
 * standard `publishVariant{Variant}PublicationToMavenLocal` tasks
 * from it.
 *
 * W4.1 scope: JVM target only — iOS / JS / WasmJs per-variant
 * publishing has KMP-specific complications deferred to W4.2.
 */
class PerVariantPublishingTest {

    @TempDir
    lateinit var testProjectDir: File

    @BeforeEach
    fun setup() {
        File(testProjectDir, "settings.gradle.kts").writeText(
            """
            pluginManagement { repositories { gradlePluginPortal(); mavenCentral(); google() } }
            dependencyResolutionManagement { repositories { mavenCentral(); google() } }
            rootProject.name = "publish-matrix-test"
            """.trimIndent(),
        )
    }

    @Test
    fun `publishMatrix=true with maven-publish applied registers per-variant publish tasks`() {
        File(testProjectDir, "build.gradle.kts").writeText(
            """
            plugins {
                kotlin("multiplatform") version "2.2.21"
                id("io.github.mobilebytelabs.kmp-product-flavors")
                `maven-publish`
            }
            group = "com.example"
            version = "0.1.0"

            kmpFlavors {
                buildMatrix.set(true)
                publishMatrix.set(true)
                generateBuildConfig.set(false)
                flavors {
                    register("free") { isDefault.set(true) }
                    register("paid")
                }
            }
            kotlin { jvm("desktop") }
            """.trimIndent(),
        )

        val result = GradleRunner.create()
            .withProjectDir(testProjectDir)
            .withArguments("tasks", "--all")
            .withPluginClasspath()
            .build()

        // Gradle's maven-publish derives `publish{PublicationName}PublicationTo{Repo}`.
        // Our publication for inactive variant `paid` is named `variantPaid`, so the
        // local-Maven publish task is `publishVariantPaidPublicationToMavenLocal`.
        assertTrue(
            result.output.contains("publishVariantPaidPublicationToMavenLocal"),
            "Expected per-variant publish task for paid variant:\n${result.output}",
        )
        assertTrue(
            result.output.contains("jarPaidKotlinDesktop"),
            "Expected per-variant Jar task:\n${result.output}",
        )
        // Lifecycle telemetry from PerVariantPublishConfigurator
        assertTrue(
            result.output.contains("publishMatrix: registered 1 per-variant publication"),
            "Expected publishMatrix lifecycle log:\n${result.output}",
        )
    }

    @Test
    fun `publishMatrix=false (default) registers NO per-variant publish tasks`() {
        File(testProjectDir, "build.gradle.kts").writeText(
            """
            plugins {
                kotlin("multiplatform") version "2.2.21"
                id("io.github.mobilebytelabs.kmp-product-flavors")
                `maven-publish`
            }
            group = "com.example"
            version = "0.1.0"

            kmpFlavors {
                buildMatrix.set(true)
                // publishMatrix not opted in
                generateBuildConfig.set(false)
                flavors {
                    register("free") { isDefault.set(true) }
                    register("paid")
                }
            }
            kotlin { jvm("desktop") }
            """.trimIndent(),
        )

        val result = GradleRunner.create()
            .withProjectDir(testProjectDir)
            .withArguments("tasks", "--all")
            .withPluginClasspath()
            .build()

        assertFalse(
            result.output.contains("publishVariantPaidPublicationToMavenLocal"),
            "publishMatrix opt-in absent — no per-variant publish task should exist:\n${result.output}",
        )
        assertFalse(
            result.output.contains("jarPaidKotlinDesktop"),
            "publishMatrix opt-in absent — no per-variant Jar task should exist:\n${result.output}",
        )
    }
}
